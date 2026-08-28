package com.lunamonitor.beta.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.lunamonitor.beta.lut.LutAtlasPacker
import com.lunamonitor.beta.model.ColorAdjustments
import com.lunamonitor.beta.model.ScaleMode
import com.lunamonitor.beta.model.CubeLut
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MonitorGlView(context: Context) : GLSurfaceView(context) {
    private val monitorRenderer = MonitorRenderer(::requestRender)
    @Volatile private var latestDecoderSurface: Surface? = null
    private var lastRecoveryLut: CubeLut? = null
    private var lastStyleLut: CubeLut? = null
    var onDecoderSurface: ((Surface) -> Unit)? = null
        set(value) {
            field = value
            latestDecoderSurface?.let { surface -> post { value?.invoke(surface) } }
        }

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(monitorRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        monitorRenderer.surfaceListener = { surface ->
            latestDecoderSurface = surface
            post { onDecoderSurface?.invoke(surface) }
        }
    }

    fun setAdjustments(value: ColorAdjustments) { monitorRenderer.adjustments.set(value); requestRender() }
    fun setScaleMode(value: ScaleMode) { monitorRenderer.scaleMode.set(value); requestRender() }
    fun setVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || monitorRenderer.videoSize.get() == VideoSize(width, height)) return
        monitorRenderer.videoSize.set(VideoSize(width, height))
        // Qualcomm decoders may allocate a coded surface taller than the visible crop.
        // Re-size the consumer buffer to the visible dimensions so padded bottom rows
        // are not scaled into a thin strip at the edge of the preview.
        queueEvent { monitorRenderer.updateBufferSize(width, height) }
        requestRender()
    }
    fun setMonitorTools(zebra: Boolean, falseColor: Boolean, peaking: Boolean) {
        monitorRenderer.tools.set(ToolState(zebra, falseColor, peaking)); requestRender()
    }
    fun setVideoVisible(visible: Boolean) {
        monitorRenderer.videoVisible.set(visible)
        requestRender()
    }
    fun setLuts(recovery: CubeLut?, style: CubeLut?) {
        if (lastRecoveryLut === recovery && lastStyleLut === style) return
        lastRecoveryLut = recovery
        lastStyleLut = style
        queueEvent { monitorRenderer.setLuts(recovery, style) }
        requestRender()
    }

    override fun onDetachedFromWindow() {
        queueEvent { monitorRenderer.release() }
        super.onDetachedFromWindow()
    }
}

private class MonitorRenderer(private val invalidate: () -> Unit) : GLSurfaceView.Renderer,
    SurfaceTexture.OnFrameAvailableListener {
    val adjustments = AtomicReference(ColorAdjustments())
    val scaleMode = AtomicReference(ScaleMode.FIT)
    val tools = AtomicReference(ToolState())
    val videoSize = AtomicReference(VideoSize(1440, 720))
    val videoVisible = AtomicBoolean(false)
    var surfaceListener: ((Surface) -> Unit)? = null
    private var program = 0
    private var texture = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var decoderSurface: Surface? = null
    private var recoveryTexture = 0
    private var styleTexture = 0
    private var fallbackLutTexture = 0
    private var recoverySize = 0
    private var styleSize = 0
    private var recoveryColumns = 1
    private var styleColumns = 1
    private var pendingRecovery: CubeLut? = null
    private var pendingStyle: CubeLut? = null
    private var width = 1
    private var height = 1
    private val frameAvailable = AtomicBoolean(false)
    private val textureMatrix = FloatArray(16)
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)); position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        decoderSurface?.release()
        surfaceTexture?.release()
        program = link(VERTEX, FRAGMENT)
        check(RendererSamplerLayout.isValid())
        fallbackLutTexture = createFallbackLutTexture()
        texture = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        Matrix.setIdentityM(textureMatrix, 0)
        surfaceTexture = SurfaceTexture(texture).apply {
            setDefaultBufferSize(1440, 720)
            // A concrete Looper avoids vendor-dependent callback delivery from
            // the GLSurfaceView render thread, which can leave decoded frames black.
            setOnFrameAvailableListener(this@MonitorRenderer, Handler(Looper.getMainLooper()))
        }
        decoderSurface = Surface(surfaceTexture).also { surfaceListener?.invoke(it) }
        setLuts(pendingRecovery, pendingStyle)
        GLES30.glClearColor(0.015f, 0.017f, 0.017f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { this.width = width; this.height = height }

    override fun onDrawFrame(gl: GL10?) {
        if (frameAvailable.getAndSet(false)) {
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(textureMatrix)
        }
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (!videoVisible.get()) return
        val crop = viewportAndCrop(scaleMode.get())
        GLES30.glViewport(crop[0].toInt(), crop[1].toInt(), crop[2].toInt(), crop[3].toInt())
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uTexture"), RendererSamplerLayout.VIDEO)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uTexMatrix"), 1, false, textureMatrix, 0)
        bindLut("uRecoveryLut", "uHasRecoveryLut", "uRecoverySize", "uRecoveryColumns", recoveryTexture, recoverySize, recoveryColumns, RendererSamplerLayout.RECOVERY_LUT)
        bindLut("uStyleLut", "uHasStyleLut", "uStyleSize", "uStyleColumns", styleTexture, styleSize, styleColumns, RendererSamplerLayout.STYLE_LUT)
        val stride = 4 * 4
        vertices.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, stride, vertices)
        GLES30.glEnableVertexAttribArray(0)
        vertices.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, vertices)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uCrop"), crop[4], crop[5])
        val a = adjustments.get()
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uExposure"), a.exposure)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uBrightness"), a.brightness / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uContrast"), a.contrast / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uHighlights"), a.highlights / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uShadows"), a.shadows / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uTemperature"), a.temperature / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uTint"), a.tint / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSaturation"), a.saturation / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uVibrance"), a.vibrance / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uWhites"), a.whites / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uBlacks"), a.blacks / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSharpness"), a.sharpness / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uMidtones"), a.midtones / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uGamma"), a.gamma / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uHue"), a.hue * 0.0174532925f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uFade"), a.fade / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uClarity"), a.clarity / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uDehaze"), a.dehaze / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uHighlightSoftness"), a.highlightSoftness / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uVignette"), a.vignette / 100f)
        val t = tools.get()
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uZebra"), if (t.zebra) 1 else 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uFalseColor"), if (t.falseColor) 1 else 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uPeaking"), if (t.peaking) 1 else 0)
        val size = videoSize.get()
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uTexel"), 1f / size.width, 1f / size.height)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) { frameAvailable.set(true); invalidate() }

    fun updateBufferSize(width: Int, height: Int) {
        surfaceTexture?.setDefaultBufferSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    fun setLuts(recovery: CubeLut?, style: CubeLut?) {
        pendingRecovery = recovery
        pendingStyle = style
        val first = uploadLut(recoveryTexture, recovery)
        recoveryTexture = first.first; recoverySize = first.second; recoveryColumns = first.third
        val second = uploadLut(styleTexture, style)
        styleTexture = second.first; styleSize = second.second; styleColumns = second.third
    }

    private fun uploadLut(oldTexture: Int, lut: CubeLut?): Triple<Int, Int, Int> {
        if (oldTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(oldTexture), 0)
        if (lut == null || program == 0) return Triple(0, 0, 1)
        val atlas = LutAtlasPacker.pack(lut)
        val id = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val data = ByteBuffer.allocateDirect(atlas.rgba.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(atlas.rgba); position(0)
        }
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, atlas.width, atlas.height, 0, GLES30.GL_RGBA, GLES30.GL_FLOAT, data)
        return Triple(id, lut.size, atlas.columns)
    }

    private fun bindLut(sampler: String, enabled: String, sizeUniform: String, columnsUniform: String, id: Int, size: Int, columns: Int, unit: Int) {
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, enabled), if (id != 0) 1 else 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, sizeUniform), size.toFloat().coerceAtLeast(2f))
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, columnsUniform), columns.toFloat().coerceAtLeast(1f))
        // Samplers of different types may not alias one texture unit, even when
        // the LUT branch is disabled. The old default (all samplers == unit 0)
        // made glDrawArrays invalid while FPS/bitrate continued to update.
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (id != 0) id else fallbackLutTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, sampler), unit)
    }

    private fun createFallbackLutTexture(): Int {
        val id = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val pixel = ByteBuffer.allocateDirect(4).put(byteArrayOf(0, 0, 0, 0)).apply { position(0) }
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixel)
        return id
    }

    private fun viewportAndCrop(mode: ScaleMode): FloatArray {
        val size = videoSize.get()
        val videoAspect = size.width.toFloat() / size.height.coerceAtLeast(1)
        val viewAspect = width.toFloat() / height.coerceAtLeast(1)
        return when (mode) {
            ScaleMode.FIT -> if (viewAspect > videoAspect) {
                val w = height * videoAspect; floatArrayOf((width - w) / 2f, 0f, w, height.toFloat(), 1f, 1f)
            } else {
                val h = width / videoAspect; floatArrayOf(0f, (height - h) / 2f, width.toFloat(), h, 1f, 1f)
            }
            ScaleMode.FILL_CROP -> if (viewAspect > videoAspect) {
                floatArrayOf(0f, 0f, width.toFloat(), height.toFloat(), 1f, videoAspect / viewAspect)
            } else {
                floatArrayOf(0f, 0f, width.toFloat(), height.toFloat(), viewAspect / videoAspect, 1f)
            }
            ScaleMode.STRETCH -> floatArrayOf(0f, 0f, width.toFloat(), height.toFloat(), 1f, 1f)
            ScaleMode.PIXEL_TO_PIXEL -> floatArrayOf(
                (width - size.width) / 2f, (height - size.height) / 2f,
                size.width.toFloat(), size.height.toFloat(), 1f, 1f,
            )
        }
    }

    fun release() {
        surfaceTexture?.setOnFrameAvailableListener(null)
        decoderSurface?.release(); decoderSurface = null
        surfaceTexture?.release(); surfaceTexture = null
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        if (recoveryTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(recoveryTexture), 0)
        if (styleTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(styleTexture), 0)
        if (fallbackLutTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(fallbackLutTexture), 0)
        recoveryTexture = 0; styleTexture = 0; fallbackLutTexture = 0
        if (program != 0) GLES30.glDeleteProgram(program)
    }

    private fun link(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int = GLES30.glCreateShader(type).also {
            GLES30.glShaderSource(it, source); GLES30.glCompileShader(it)
            val ok = IntArray(1); GLES30.glGetShaderiv(it, GLES30.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { GLES30.glGetShaderInfoLog(it) }
        }
        val v = compile(GLES30.GL_VERTEX_SHADER, vertex)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fragment)
        return GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, v); GLES30.glAttachShader(it, f); GLES30.glLinkProgram(it)
            val ok = IntArray(1); GLES30.glGetProgramiv(it, GLES30.GL_LINK_STATUS, ok, 0)
            check(ok[0] != 0) { GLES30.glGetProgramInfoLog(it) }
            GLES30.glDeleteShader(v); GLES30.glDeleteShader(f)
        }
    }

    companion object {
        private const val VERTEX = """#version 300 es
            layout(location=0) in vec2 aPosition; layout(location=1) in vec2 aUv; uniform mat4 uTexMatrix;
            out vec2 vUv; void main(){
              gl_Position=vec4(aPosition,0.0,1.0);
              vec2 cameraUv=(uTexMatrix*vec4(aUv,0.0,1.0)).xy;
              // SurfaceTexture already supplies the correct horizontal mapping.
              // Luna needs only the remaining vertical correction on Android.
              vUv=vec2(cameraUv.x,1.0-cameraUv.y);
            }
        """
        private const val FRAGMENT = """#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision highp float; uniform samplerExternalOES uTexture; uniform vec2 uCrop;
            uniform highp sampler2D uRecoveryLut,uStyleLut; uniform bool uHasRecoveryLut,uHasStyleLut;
            uniform float uRecoverySize,uStyleSize,uRecoveryColumns,uStyleColumns;
            uniform float uExposure,uBrightness,uContrast,uHighlights,uShadows,uTemperature,uTint,uSaturation,uVibrance;
            uniform float uWhites,uBlacks,uSharpness,uMidtones,uGamma,uHue,uFade,uClarity,uDehaze,uHighlightSoftness,uVignette;
            uniform bool uZebra,uFalseColor,uPeaking; uniform vec2 uTexel;
            in vec2 vUv; out vec4 outColor;
            vec3 falseMap(float x){
              if(x<0.05)return vec3(0.28,0.08,0.42); if(x<0.18)return vec3(0.08,0.22,0.55);
              if(x<0.42)return vec3(0.10,0.48,0.48); if(x<0.62)return vec3(0.42);
              if(x<0.78)return vec3(0.62,0.52,0.18); if(x<0.93)return vec3(0.62,0.25,0.16); return vec3(0.78,0.72,0.70);
            }
            vec3 hueRotate(vec3 c,float a){
              float co=cos(a),si=sin(a); mat3 m=mat3(
                0.213+co*0.787-si*0.213,0.213-co*0.213+si*0.143,0.213-co*0.213-si*0.787,
                0.715-co*0.715-si*0.715,0.715+co*0.285+si*0.140,0.715-co*0.715+si*0.715,
                0.072-co*0.072+si*0.928,0.072-co*0.072-si*0.283,0.072+co*0.928+si*0.072);
              return clamp(m*c,0.0,1.0);
            }
            vec3 applyLut(sampler2D lut,vec3 c,float size,float columns){
              vec3 p=clamp(c,0.0,1.0)*(size-1.0);
              float b0=floor(p.b),b1=min(b0+1.0,size-1.0),blend=fract(p.b),rows=ceil(size/columns);
              vec2 atlasSize=vec2(size*columns,size*rows);
              vec2 uv0=(vec2(mod(b0,columns)*size+p.r,floor(b0/columns)*size+p.g)+0.5)/atlasSize;
              vec2 uv1=(vec2(mod(b1,columns)*size+p.r,floor(b1/columns)*size+p.g)+0.5)/atlasSize;
              return mix(texture(lut,uv0).rgb,texture(lut,uv1).rgb,blend);
            }
            void main(){
              vec2 uv=(vUv-0.5)*uCrop+0.5;
              // Never sample the padded/undefined outer half texel. Some Qualcomm
              // Surface decoders expose that row as a stretched line at the bottom.
              vec2 edge=0.5*uTexel; uv=clamp(uv,edge,vec2(1.0)-edge);
              vec3 c=texture(uTexture,uv).rgb;
              if(uHasRecoveryLut)c=applyLut(uRecoveryLut,c,uRecoverySize,uRecoveryColumns);
              if(uHasStyleLut)c=applyLut(uStyleLut,c,uStyleSize,uStyleColumns);
              c*=exp2(uExposure); float l=dot(c,vec3(0.2126,0.7152,0.0722));
              float sh=1.0-smoothstep(0.0,0.55,l); float hi=smoothstep(0.45,1.0,l);
              c+=uShadows*sh*0.35; c+=uHighlights*hi*0.28;
              c+=uBlacks*(1.0-smoothstep(0.0,0.34,l))*0.22; c+=uWhites*smoothstep(0.66,1.0,l)*0.22;
              c+=uMidtones*(4.0*l*(1.0-l))*0.18; c+=uBrightness;
              c=(c-0.5)*(1.0+uContrast)+0.5; c.r+=uTemperature*0.08; c.b-=uTemperature*0.08; c.g+=uTint*0.05;
              c=pow(max(c,vec3(0.0001)),vec3(exp2(-uGamma*0.8))); c=hueRotate(c,uHue);
              l=dot(c,vec3(0.2126,0.7152,0.0722)); c=mix(vec3(l),c,1.0+uSaturation);
              float chroma=max(c.r,max(c.g,c.b))-min(c.r,min(c.g,c.b)); c=mix(vec3(l),c,1.0+uVibrance*(1.0-chroma));
              vec3 n=texture(uTexture,uv+vec2(0.0,uTexel.y)).rgb, s=texture(uTexture,uv-vec2(0.0,uTexel.y)).rgb;
              vec3 e=texture(uTexture,uv+vec2(uTexel.x,0.0)).rgb, w=texture(uTexture,uv-vec2(uTexel.x,0.0)).rgb;
              vec3 detail=c-(n+s+e+w)*0.25; c+=detail*(uSharpness*0.48+uClarity*0.30);
              c=(c-0.5)*(1.0+uDehaze*0.28)+0.5; l=dot(c,vec3(0.2126,0.7152,0.0722));
              float compressed=mix(l,l/(1.0+max(l-0.62,0.0)*1.6),uHighlightSoftness); c*=compressed/max(l,0.0001);
              c=mix(c,vec3(0.12)+c*0.80,uFade); float vig=smoothstep(0.78,0.28,length(vUv-0.5));
              c*=mix(1.0,clamp(vig,0.25,1.0),max(uVignette,0.0)); c=mix(c,c+(1.0-vig)*0.18,max(-uVignette,0.0));
              c=clamp(c,0.0,1.0); l=dot(c,vec3(0.2126,0.7152,0.0722));
              if(uFalseColor)c=falseMap(l);
              if(uZebra && l>0.92 && mod(gl_FragCoord.x+gl_FragCoord.y,12.0)<5.0)c=mix(c,vec3(0.85),0.78);
              if(uPeaking){
                float lx=dot(texture(uTexture,uv+vec2(uTexel.x,0.0)).rgb-texture(uTexture,uv-vec2(uTexel.x,0.0)).rgb,vec3(0.333));
                float ly=dot(texture(uTexture,uv+vec2(0.0,uTexel.y)).rgb-texture(uTexture,uv-vec2(0.0,uTexel.y)).rgb,vec3(0.333));
                if(length(vec2(lx,ly))>0.10)c=mix(c,vec3(0.78,0.82,0.76),0.88);
              }
              outColor=vec4(c,1.0);
            }
        """
    }
}

private data class ToolState(val zebra: Boolean = false, val falseColor: Boolean = false, val peaking: Boolean = false)
private data class VideoSize(val width: Int, val height: Int)
