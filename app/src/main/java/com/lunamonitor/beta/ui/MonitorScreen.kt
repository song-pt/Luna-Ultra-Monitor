package com.lunamonitor.beta.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunamonitor.beta.MonitorViewModel
import com.lunamonitor.beta.model.ColorAdjustments
import com.lunamonitor.beta.model.ConnectionState
import com.lunamonitor.beta.model.CaptureMode
import com.lunamonitor.beta.model.CameraColorMode
import com.lunamonitor.beta.model.DrawerSide
import com.lunamonitor.beta.model.MonitorUiState
import com.lunamonitor.beta.model.ScaleMode
import com.lunamonitor.beta.model.ToolSection
import com.lunamonitor.beta.model.AppLanguage
import com.lunamonitor.beta.model.VideoFormatPolicy
import com.lunamonitor.beta.render.MonitorGlView
import com.lunamonitor.beta.lut.CubeLutParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LunaMonitorApp(viewModel: MonitorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LunaMonitorTheme {
        MonitorScreen(state, viewModel)
    }
}

@Composable
private fun MonitorScreen(state: MonitorUiState, actions: MonitorViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) actions.stopGimbal()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); actions.stopGimbal() }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(
            factory = { context -> MonitorGlView(context).also { it.onDecoderSurface = actions::attachDecoderSurface } },
            update = { view ->
                view.setAdjustments(state.adjustments)
                view.setScaleMode(state.scaleMode)
                view.setVideoSize(state.streamStats.width, state.streamStats.height)
                view.setMonitorTools(state.zebra, state.falseColor, state.peaking)
                view.setLuts(state.recoveryLut, state.styleLut)
                view.setVideoVisible(state.connection == ConnectionState.STREAMING)
            },
            modifier = Modifier.fillMaxSize(),
        )

        GuideOverlay(state)

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (state.connection != ConnectionState.STREAMING) EmptyMonitorState(state, actions)

            if (!state.cleanView) {
                TopStatusBar(state)
                BottomToolBar(state, actions)
                state.selectedSection?.let { ToolPanel(it, state, actions) }
                if (state.selectedSection == null) {
                    CameraControlDrawer(state, actions)
                    GimbalControl(state, actions)
                }
            } else {
                CleanViewExit(actions)
            }

            state.thermalSuggestion?.let { ThermalSuggestion(it, state.language, actions) }
            state.error?.let { ErrorBubble(it, state.language, actions) }
        }
    }
}

@Composable
private fun TopStatusBar(state: MonitorUiState) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value
        Row(
            Modifier.fillMaxWidth().background(Color(0xB8080909)).padding(horizontal = if (MonitorLayoutPolicy.compactToolbar(widthDp)) 8.dp else 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!MonitorLayoutPolicy.hideBrand(widthDp)) {
                Text("LUNA MONITOR", fontSize = 12.sp, letterSpacing = 1.4.sp, maxLines = 1)
                Spacer(Modifier.width(14.dp))
            }
            StatusDot(state.connection == ConnectionState.STREAMING)
            Spacer(Modifier.width(7.dp))
            Text(UiText.runtime(state.language, state.connectionMessage), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (state.connection == ConnectionState.STREAMING) {
                if (MonitorLayoutPolicy.showFullStats(widthDp)) {
                    Text("${state.streamStats.width}×${state.streamStats.height}", fontSize = 11.sp, maxLines = 1)
                    Spacer(Modifier.width(12.dp))
                    Text(UiText.runtime(state.language, state.streamStats.codec), fontSize = 11.sp, maxLines = 1)
                    Spacer(Modifier.width(12.dp))
                }
                Text("%.1f fps".format(state.streamStats.fps), fontSize = 11.sp, maxLines = 1)
                if (MonitorLayoutPolicy.showBitrate(widthDp)) {
                    Spacer(Modifier.width(12.dp))
                    Text("${state.streamStats.bitrateKbps} kb/s", fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    Box(Modifier.size(7.dp).background(if (active) Color(0xFFAFB7AC) else Color(0xFF5E615F), RoundedCornerShape(50)))
}

@Composable
private fun LanguageButton(label: String, selected: Boolean, action: () -> Unit) {
    Text(
        label,
        Modifier.clickable(onClick = action)
            .background(if (selected) Color(0xFF3A3E3C) else Color(0xFF202322), RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        color = Color.White, fontSize = 11.sp,
    )
}

@Composable
private fun EmptyMonitorState(state: MonitorUiState, actions: MonitorViewModel) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val busy = state.connection in setOf(ConnectionState.CONNECTING, ConnectionState.HANDSHAKING, ConnectionState.WAITING_VIDEO)
        Text(if (busy) UiText.runtime(state.language, state.connectionMessage) else UiText.get(state.language, "connectTitle"), fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(UiText.get(state.language, "wifiHint"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        NeutralButton(if (busy) UiText.get(state.language, "connecting") else UiText.get(state.language, "connect"), !busy, actions::connect)
        Spacer(Modifier.height(14.dp))
        Text(UiText.get(state.language, "language"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LanguageButton("中文", state.language == AppLanguage.ZH) { actions.setLanguage(AppLanguage.ZH) }
            LanguageButton("English", state.language == AppLanguage.EN) { actions.setLanguage(AppLanguage.EN) }
            LanguageButton("日本語", state.language == AppLanguage.JA) { actions.setLanguage(AppLanguage.JA) }
        }
    }
}

@Composable
private fun BoxScope.BottomToolBar(state: MonitorUiState, actions: MonitorViewModel) {
    BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        val compact = MonitorLayoutPolicy.compactToolbar(maxWidth.value)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (compact) 4.dp else 8.dp, vertical = if (compact) 6.dp else 12.dp)
                .background(Color(0xE6111313), RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF2E3130), RoundedCornerShape(14.dp))
                .padding(if (compact) 3.dp else 5.dp),
        ) {
            listOf(
                Triple(ToolSection.COLOR, "color", MonitorGlyph.PALETTE), Triple(ToolSection.LUT, "lut", MonitorGlyph.LUT),
                Triple(ToolSection.EXPOSURE, "exposure", MonitorGlyph.SUN), Triple(ToolSection.FOCUS, "focus", MonitorGlyph.MOUNTAIN),
                Triple(ToolSection.COMPOSITION, "composition", MonitorGlyph.CROP), Triple(ToolSection.DISPLAY, "display", MonitorGlyph.DISPLAY),
                Triple(ToolSection.CAMERA_SETTINGS, "parameters", MonitorGlyph.SLIDERS),
            ).forEach { (section, label, glyph) ->
                IconLabelButton(UiText.get(state.language, label), glyph, state.selectedSection == section, { actions.selectSection(section) }, Modifier.weight(1f), compact)
            }
        }
    }
}

private enum class MonitorGlyph { CAMERA, PALETTE, LUT, SUN, MOUNTAIN, CROP, DISPLAY, SLIDERS }

@Composable
private fun IconLabelButton(label: String, glyph: MonitorGlyph, selected: Boolean, action: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.clickable(onClick = action)
            .background(if (selected) Color(0xFF2B2E2D) else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(horizontal = 1.dp, vertical = if (compact) 3.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineGlyph(glyph, tint, Modifier.size(if (compact) 19.dp else 23.dp))
        Spacer(Modifier.height(if (compact) 1.dp else 2.dp))
        Text(label, color = tint, fontSize = if (compact) 8.sp else 9.sp, lineHeight = 10.sp, maxLines = 1)
    }
}

@Composable
private fun LineGlyph(glyph: MonitorGlyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val w = (s * .075f).coerceAtLeast(1.5f)
        val stroke = Stroke(width = w, cap = StrokeCap.Round)
        when (glyph) {
            MonitorGlyph.CAMERA -> {
                drawRoundRect(tint, Offset(s*.10f,s*.27f), Size(s*.80f,s*.58f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s*.10f), style = stroke)
                drawCircle(tint, s*.18f, Offset(s*.52f,s*.56f), style = stroke)
                drawLine(tint, Offset(s*.25f,s*.27f), Offset(s*.34f,s*.15f), w, StrokeCap.Round)
                drawLine(tint, Offset(s*.34f,s*.15f), Offset(s*.56f,s*.15f), w, StrokeCap.Round)
                drawLine(tint, Offset(s*.56f,s*.15f), Offset(s*.64f,s*.27f), w, StrokeCap.Round)
            }
            MonitorGlyph.PALETTE -> {
                val p = Path().apply { moveTo(s*.82f,s*.62f); cubicTo(s*.74f,s*.91f,s*.18f,s*.91f,s*.12f,s*.51f); cubicTo(s*.06f,s*.16f,s*.47f,s*.04f,s*.73f,s*.18f); cubicTo(s*.96f,s*.31f,s*.96f,s*.54f,s*.82f,s*.62f); close() }
                drawPath(p, tint, style = stroke)
                listOf(.30f to .38f, .46f to .25f, .65f to .31f).forEach { drawCircle(tint, s*.035f, Offset(s*it.first,s*it.second)) }
                drawCircle(tint, s*.08f, Offset(s*.68f,s*.66f), style = stroke)
            }
            MonitorGlyph.LUT -> {
                drawRect(tint, Offset(s*.16f,s*.16f), Size(s*.48f,s*.48f), style=stroke)
                drawRect(tint, Offset(s*.36f,s*.36f), Size(s*.48f,s*.48f), style=stroke)
                drawLine(tint,Offset(s*.16f,s*.16f),Offset(s*.36f,s*.36f),w,StrokeCap.Round)
                drawLine(tint,Offset(s*.64f,s*.16f),Offset(s*.84f,s*.36f),w,StrokeCap.Round)
                drawLine(tint,Offset(s*.16f,s*.64f),Offset(s*.36f,s*.84f),w,StrokeCap.Round)
            }
            MonitorGlyph.SUN -> {
                drawCircle(tint, s*.20f, Offset(s*.5f,s*.5f), style = stroke)
                repeat(8) { i -> val a=i*Math.PI/4; val dx=kotlin.math.cos(a).toFloat(); val dy=kotlin.math.sin(a).toFloat(); drawLine(tint,Offset(s*(.5f+dx*.31f),s*(.5f+dy*.31f)),Offset(s*(.5f+dx*.43f),s*(.5f+dy*.43f)),w,StrokeCap.Round) }
            }
            MonitorGlyph.MOUNTAIN -> {
                val p=Path().apply { moveTo(s*.08f,s*.82f); lineTo(s*.38f,s*.30f); lineTo(s*.55f,s*.55f); lineTo(s*.68f,s*.38f); lineTo(s*.92f,s*.82f) }
                drawPath(p,tint,style=stroke); drawLine(tint,Offset(s*.08f,s*.82f),Offset(s*.92f,s*.82f),w,StrokeCap.Round)
            }
            MonitorGlyph.CROP -> {
                drawLine(tint,Offset(s*.22f,s*.08f),Offset(s*.22f,s*.70f),w,StrokeCap.Round); drawLine(tint,Offset(s*.22f,s*.70f),Offset(s*.84f,s*.70f),w,StrokeCap.Round)
                drawLine(tint,Offset(s*.08f,s*.28f),Offset(s*.70f,s*.28f),w,StrokeCap.Round); drawLine(tint,Offset(s*.70f,s*.28f),Offset(s*.70f,s*.90f),w,StrokeCap.Round)
            }
            MonitorGlyph.DISPLAY -> {
                drawRoundRect(tint,Offset(s*.08f,s*.16f),Size(s*.84f,s*.58f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(s*.07f),style=stroke)
                drawLine(tint,Offset(s*.38f,s*.88f),Offset(s*.62f,s*.88f),w,StrokeCap.Round); drawLine(tint,Offset(s*.5f,s*.74f),Offset(s*.5f,s*.88f),w,StrokeCap.Round)
            }
            MonitorGlyph.SLIDERS -> {
                listOf(.28f to .35f,.50f to .68f,.72f to .44f).forEach { (y,x) -> drawLine(tint,Offset(s*.14f,s*y),Offset(s*.86f,s*y),w,StrokeCap.Round); drawCircle(tint,s*.07f,Offset(s*x,s*y),style=stroke) }
            }
        }
    }
}

@Composable
private fun BoxScope.ToolPanel(section: ToolSection, state: MonitorUiState, actions: MonitorViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 48.dp, bottom = 68.dp)) {
        val compactSettings = section == ToolSection.CAMERA_SETTINGS && MonitorLayoutPolicy.compactHeight(maxHeight.value)
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).width(MonitorLayoutPolicy.panelWidth(maxWidth.value).dp).fillMaxHeight(),
            color = Color(0xF2111313), contentColor = Color(0xFFE9EAE6), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF303332)),
        ) {
          Column(Modifier.padding(if (MonitorLayoutPolicy.compactHeight(maxHeight.value)) 10.dp else 16.dp)) {
            Text(sectionTitle(section, state.language), fontSize = if (compactSettings) 13.sp else 15.sp, color = Color(0xFFE9EAE6), maxLines = 1)
            if (!compactSettings) Text(sectionSubtitle(section, state.language), color = Color(0xFFB8BCB8), fontSize = 11.sp)
            Spacer(Modifier.height(if (compactSettings) 4.dp else 8.dp)); HorizontalDivider(color = Color(0xFF2B2D2D)); Spacer(Modifier.height(if (compactSettings) 4.dp else 8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { when (section) {
                ToolSection.COLOR -> ColorPanel(state.adjustments, state.language, actions::setAdjustments)
                ToolSection.LUT -> LutPanel(state, actions)
                ToolSection.EXPOSURE -> ToggleList(listOf(UiText.get(state.language,"zebra") to state.zebra, UiText.get(state.language,"falseColor") to state.falseColor), listOf(actions::toggleZebra, actions::toggleFalseColor))
                ToolSection.FOCUS -> ToggleList(listOf(UiText.get(state.language,"peaking") to state.peaking), listOf(actions::togglePeaking))
                ToolSection.COMPOSITION -> Column(Modifier.monitorScroll()) {
                    ToggleList(
                        listOf(UiText.get(state.language,"thirds") to state.showThirds, UiText.get(state.language,"centerMark") to state.showCenter, UiText.get(state.language,"safeArea") to state.showSafeArea),
                        listOf(actions::toggleThirds, actions::toggleCenter, actions::toggleSafeArea),
                    )
                    Spacer(Modifier.height(12.dp)); HorizontalDivider(color = Color(0xFF2B2D2D)); Spacer(Modifier.height(8.dp))
                    GuideStyleControls(state, actions)
                }
                ToolSection.DISPLAY -> DisplayPanel(state, actions)
                ToolSection.CAMERA_SETTINGS -> CameraSettingsPanel(state, actions, compactSettings)
            } }
          }
        }
    }
}

@Composable
private fun BoxScope.CleanViewExit(actions: MonitorViewModel) {
    Surface(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).clickable(onClick = actions::toggleCleanView),
        color = Color(0xD91A1C1B), contentColor = Color(0xFFF0F1ED), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF646864)),
    ) {
        Text(UiText.get(actions.state.value.language, "cleanExit"), Modifier.padding(horizontal = 16.dp, vertical = 9.dp), fontSize = 12.sp)
    }
}

private fun sectionTitle(section: ToolSection, language: AppLanguage) = UiText.get(language, when (section) {
    ToolSection.COLOR -> "colorTitle"; ToolSection.LUT -> "lutTitle"; ToolSection.EXPOSURE -> "exposureTitle"; ToolSection.FOCUS -> "focusTitle"
    ToolSection.COMPOSITION -> "compositionTitle"; ToolSection.DISPLAY -> "displayTitle"; ToolSection.CAMERA_SETTINGS -> "parametersTitle"
})
private fun sectionSubtitle(section: ToolSection, language: AppLanguage) = UiText.get(language, when (section) {
    ToolSection.COLOR -> "colorSubtitle"; ToolSection.LUT -> "lutSubtitle"; ToolSection.EXPOSURE -> "exposureSubtitle"
    ToolSection.FOCUS -> "focusSubtitle"; ToolSection.COMPOSITION -> "compositionSubtitle"; ToolSection.DISPLAY -> "displaySubtitle"
    ToolSection.CAMERA_SETTINGS -> "parametersSubtitle"
})

@Composable
private fun CameraSettingsPanel(state: MonitorUiState, actions: MonitorViewModel, compact: Boolean) {
    val sizes = listOf("1080P", "2.7K", "4K", "8K")
    val formats = MonitorViewModel.VIDEO_FORMATS.filter { it.label.substringBefore(' ') == state.selectedVideoSize }
    Column(Modifier.fillMaxSize()) {
        if (!compact) Text(UiText.get(state.language, "applyReadback"), fontSize = 10.sp, color = Color(0xFFB8BCB8))
        state.cameraControlMessage?.let { message ->
            if (!compact) Spacer(Modifier.height(5.dp))
            Text(UiText.runtime(state.language, message), fontSize = if (compact) 9.sp else 10.sp, color = Color.White, maxLines = 1)
        }
        Spacer(Modifier.height(if (compact) 3.dp else 10.dp))
        if (!compact) {
            Text(UiText.get(state.language, "cameraColorMode"), fontSize = 11.sp, color = Color.White)
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                CameraColorMode.STANDARD to "cameraColorStandard",
                CameraColorMode.DOLBY_VISION to "cameraColorDolby",
                CameraColorMode.I_LOG to "cameraColorILog",
            ).forEach { (mode, labelKey) ->
                ParameterChoice(
                    UiText.get(state.language, labelKey),
                    state.cameraColorMode == mode,
                    VideoFormatPolicy.canSetColorMode(state.connection, state.captureMode, state.cameraControlBusy, state.recording),
                    modifier = Modifier.weight(1f),
                    compact = compact,
                ) { actions.setVideoColorMode(mode) }
            }
        }
        if (!compact) {
            Text(
                UiText.get(state.language, "cameraColorVideoOnly"),
                fontSize = 9.sp,
                color = Color(0xFFB8BCB8),
                modifier = Modifier.padding(top = 5.dp, bottom = 9.dp),
            )
        } else Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(.42f).monitorScroll(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(UiText.get(state.language, "resolution"), fontSize = 11.sp, color = Color.White)
                sizes.forEach { size ->
                    ParameterChoice(size, state.selectedVideoSize == size, true, compact = compact) { actions.selectVideoSize(size) }
                }
            }
            Column(Modifier.weight(.58f).monitorScroll(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(UiText.get(state.language, "frameRate"), fontSize = 11.sp, color = Color.White)
                formats.forEach { option ->
                    ParameterChoice(
                        option.label.substringAfter(' ') + " fps",
                        state.videoFormat?.resolutionCode == option.resolutionCode,
                        VideoFormatPolicy.canApply(state.connection, state.cameraControlBusy, state.recording),
                        compact = compact,
                    ) { actions.setVideoFormat(option) }
                }
            }
        }
    }
}

@Composable
private fun ParameterChoice(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, compact: Boolean = false, action: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(enabled = enabled, onClick = action)
            .background(if (selected) Color(0xFF414644) else Color(0xFF202322), RoundedCornerShape(9.dp))
            .border(1.dp, if (selected) Color(0xFF747B77) else Color(0xFF303432), RoundedCornerShape(9.dp))
            .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = if (compact) 10.sp else 11.sp, color = if (enabled) Color.White else Color(0xFF858A87), maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (selected) drawSelectionDot()
    }
}

@Composable private fun drawSelectionDot() = Box(Modifier.size(7.dp).background(Color.White, RoundedCornerShape(50)))

@Composable
private fun LutPanel(state: MonitorUiState, actions: MonitorViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun load(uri: android.net.Uri, recovery: Boolean) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "preview.cube"
                    context.contentResolver.openInputStream(uri)?.use { CubeLutParser.parse(name, it) }
                        ?: error("Unable to open .cube file")
                }
            }.onSuccess { if (recovery) actions.setRecoveryLut(it) else actions.setStyleLut(it) }
                .onFailure { actions.setLutError(it.message ?: "Invalid .cube file") }
        }
    }
    val recoveryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) load(uri, true) }
    val stylePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) load(uri, false) }
    Column(Modifier.fillMaxSize().monitorScroll(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LutSlot(UiText.get(state.language,"recoveryLut"), state.recoveryLut?.name ?: UiText.get(state.language,"none"),
            { recoveryPicker.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) }, { actions.setRecoveryLut(null) }, state.language)
        LutSlot(UiText.get(state.language,"styleLut"), state.styleLut?.name ?: UiText.get(state.language,"none"),
            { stylePicker.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) }, { actions.setStyleLut(null) }, state.language)
        HorizontalDivider(color = Color(0xFF303332))
        Text(UiText.get(state.language,"lutOrder"), fontSize = 10.sp, lineHeight = 14.sp, color = Color(0xFFB8BCB8))
        state.lutMessage?.let { Text(UiText.runtime(state.language, it), fontSize = 10.sp, color = Color(0xFFFFB4AB)) }
    }
}

@Composable
private fun LutSlot(title: String, fileName: String, select: () -> Unit, clear: () -> Unit, language: AppLanguage) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF1B1E1D), RoundedCornerShape(10.dp)).padding(11.dp)) {
        Text(title, fontSize = 12.sp, color = Color.White)
        Text(fileName, fontSize = 10.sp, maxLines = 1, color = Color(0xFFB8BCB8))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NeutralButton(UiText.get(language,"selectCube"), true, select, Modifier.weight(1f))
            NeutralButton(UiText.get(language,"clear"), fileName != UiText.get(language,"none"), clear, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColorPanel(value: ColorAdjustments, language: AppLanguage, update: (ColorAdjustments) -> Unit) {
    val rows = listOf(
        Triple("adjustExposure", value.exposure, -5f..5f), Triple("brightness", value.brightness, -100f..100f),
        Triple("contrast", value.contrast, -100f..100f), Triple("highlights", value.highlights, -100f..100f),
        Triple("shadows", value.shadows, -100f..100f), Triple("whites", value.whites, -100f..100f),
        Triple("blacks", value.blacks, -100f..100f), Triple("temperature", value.temperature, -100f..100f),
        Triple("tint", value.tint, -100f..100f), Triple("saturation", value.saturation, -100f..100f),
        Triple("vibrance", value.vibrance, -100f..100f), Triple("sharpness", value.sharpness, 0f..100f),
        Triple("midtones", value.midtones, -100f..100f), Triple("gamma", value.gamma, -100f..100f),
        Triple("hue", value.hue, -180f..180f), Triple("fade", value.fade, 0f..100f),
        Triple("clarity", value.clarity, -100f..100f), Triple("dehaze", value.dehaze, -100f..100f),
        Triple("softHighlights", value.highlightSoftness, 0f..100f), Triple("vignette", value.vignette, -100f..100f),
    )
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.scrollEdges({ listState.canScrollBackward }, { listState.canScrollForward })) {
        items(rows) { (name, current, range) ->
            AdjustmentRow(UiText.get(language,name), current, range) { next ->
                update(when (name) {
                    "adjustExposure" -> value.copy(exposure = next); "brightness" -> value.copy(brightness = next); "contrast" -> value.copy(contrast = next)
                    "highlights" -> value.copy(highlights = next); "shadows" -> value.copy(shadows = next); "whites" -> value.copy(whites = next)
                    "blacks" -> value.copy(blacks = next); "temperature" -> value.copy(temperature = next); "tint" -> value.copy(tint = next)
                    "saturation" -> value.copy(saturation = next); "vibrance" -> value.copy(vibrance = next); "sharpness" -> value.copy(sharpness = next)
                    "midtones" -> value.copy(midtones = next); "gamma" -> value.copy(gamma = next); "hue" -> value.copy(hue = next)
                    "fade" -> value.copy(fade = next); "clarity" -> value.copy(clarity = next); "dehaze" -> value.copy(dehaze = next)
                    "softHighlights" -> value.copy(highlightSoftness = next); else -> value.copy(vignette = next)
                })
            }
        }
    }
}

@Composable
private fun AdjustmentRow(name: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.width(78.dp), fontSize = 12.sp)
            SmallStep("−") { onChange((value - 1f).coerceIn(range)) }
            Slider(value, onChange, valueRange = range, modifier = Modifier.weight(1f).height(28.dp))
            SmallStep("＋") { onChange((value + 1f).coerceIn(range)) }
            Text(if (value == 0f) "0" else "%.1f".format(value), Modifier.width(38.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SmallStep(label: String, action: () -> Unit) {
    Text(label, Modifier.size(28.dp).clickable(onClick = action).padding(top = 5.dp), fontSize = 14.sp)
}

@Composable
private fun ToggleList(items: List<Pair<String, Boolean>>, actions: List<() -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item -> NeutralToggle(item.first, item.second, actions[index]) }
    }
}

@Composable
private fun GuideStyleControls(state: MonitorUiState, actions: MonitorViewModel) {
    GuideStyleRow(UiText.get(state.language,"lineWidth"), state.guideWidth, 1f..6f, 0.5f, actions::setGuideWidth)
    GuideStyleRow(UiText.get(state.language,"opacity"), state.guideOpacity, 0.2f..1f, 0.05f, actions::setGuideOpacity, percent = true)
    GuideStyleRow(UiText.get(state.language,"centerSize"), state.centerMarkSize, 12f..96f, 4f, actions::setCenterMarkSize)
}

@Composable
private fun GuideStyleRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    update: (Float) -> Unit,
    percent: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(62.dp), fontSize = 11.sp)
            SmallStep("−") { update((value - step).coerceIn(range)) }
            Slider(value, update, valueRange = range, modifier = Modifier.weight(1f).height(30.dp))
            SmallStep("＋") { update((value + step).coerceIn(range)) }
            Text(
                if (percent) "${(value * 100).toInt()}%" else "%.1f".format(value),
                Modifier.width(38.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DisplayPanel(state: MonitorUiState, actions: MonitorViewModel) {
    Column(Modifier.monitorScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ScaleMode.FIT to UiText.get(state.language,"fit"), ScaleMode.FILL_CROP to UiText.get(state.language,"cropFill"),
            ScaleMode.STRETCH to UiText.get(state.language,"stretch"), ScaleMode.PIXEL_TO_PIXEL to UiText.get(state.language,"pixel"),
        ).forEach { (mode, title) -> NeutralToggle(title, state.scaleMode == mode) { actions.setScaleMode(mode) } }
        Spacer(Modifier.height(6.dp)); NeutralToggle(UiText.get(state.language,"clean"), state.cleanView, actions::toggleCleanView)
    }
}

@Composable
private fun NeutralToggle(label: String, selected: Boolean, action: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = action)
            .background(if (selected) Color(0xFF2A2D2C) else Color(0xFF181A1A), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp); Spacer(Modifier.weight(1f))
        Box(Modifier.size(8.dp).background(if (selected) Color(0xFFD0D2CD) else Color(0xFF4B4E4C), RoundedCornerShape(50)))
    }
}

@Composable
private fun BoxScope.CameraControlDrawer(state: MonitorUiState, actions: MonitorViewModel) {
    val right = state.controlsSide == DrawerSide.RIGHT
    var dragDistance by remember(state.controlsSide) { mutableFloatStateOf(0f) }
    Column(
        Modifier.align(if (right) Alignment.CenterEnd else Alignment.CenterStart)
            .fillMaxHeight().padding(top = 48.dp, bottom = 68.dp, start = 10.dp, end = 10.dp),
        horizontalAlignment = if (right) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.pointerInput(state.controlsExpanded) {
            if (!state.controlsExpanded) detectDragGestures(
                onDragStart = { dragDistance = 0f },
                onDragEnd = {
                    if (dragDistance < -36f) actions.setControlsSide(DrawerSide.LEFT)
                    if (dragDistance > 36f) actions.setControlsSide(DrawerSide.RIGHT)
                },
            ) { change, amount -> change.consume(); dragDistance += amount.x }
        }) { CameraDrawerHandle(state.controlsExpanded, state.language, actions::toggleControls) }
        if (state.controlsExpanded) {
            Spacer(Modifier.height(5.dp))
            Row(Modifier.weight(1f).width(154.dp), verticalAlignment = Alignment.CenterVertically) {
                if (right) VerticalZoomRail(state, actions, Modifier.weight(1f))
                Column(Modifier.width(76.dp).monitorScroll(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    val connected = state.connection in setOf(ConnectionState.STREAMING, ConnectionState.WAITING_VIDEO)
                    NeutralButton(UiText.get(state.language, if (state.captureMode == CaptureMode.PHOTO) "switchVideo" else "switchPhoto"),
                        connected && !state.cameraControlBusy && !state.recording && state.captureMode != CaptureMode.UNKNOWN, actions::switchCaptureMode)
                    val label = UiText.get(state.language, when(state.captureMode) {
                        CaptureMode.PHOTO -> "takePhoto"
                        CaptureMode.VIDEO -> if(state.recording) "stopRecord" else "startRecord"
                        else -> "readMode"
                    })
                    Canvas(Modifier.size(68.dp).semantics { contentDescription = label }
                        .clickable(enabled = connected && !state.cameraControlBusy, onClick = actions::triggerCapture)) {
                        val center = Offset(size.width/2, size.height/2)
                        drawCircle(Color.White, size.minDimension*.47f, center, style=Stroke(2.dp.toPx()))
                        val c = if(state.captureMode == CaptureMode.VIDEO) Color(0xFFE85A55) else Color.White
                        if(state.recording) drawRoundRect(c, Offset(size.width*.32f,size.height*.32f), Size(size.width*.36f,size.height*.36f), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                        else drawCircle(c, size.minDimension*.37f, center)
                    }
                    Text(label, fontSize=10.sp, color=Color.White, maxLines=2)
                    if (state.cameraControlBusy) Text("…", color=Color.White)
                    state.cameraControlMessage?.let { Text(UiText.runtime(state.language,it), fontSize=9.sp, color=Color.LightGray, maxLines=3) }
                    NeutralButton(UiText.get(state.language, if(connected) "disconnect" else "connect"), true,
                        if(connected) actions::disconnect else actions::connect)
                }
                if (!right) VerticalZoomRail(state, actions, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VerticalZoomRail(state: MonitorUiState, actions: MonitorViewModel, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val railHeight = maxHeight.coerceAtMost(240.dp)
        Row(Modifier.height(railHeight), verticalAlignment=Alignment.CenterVertically) {
            Canvas(Modifier.width(30.dp).fillMaxHeight()
                .semantics { contentDescription = "Zoom ${state.zoom}" }
                .pointerInput(actions, railHeight) {
                    fun preview(y: Float) { actions.previewZoom(1f + (1f-y/size.height).coerceIn(0f,1f)*11f) }
                    detectDragGestures(onDragStart={preview(it.y)}, onDragEnd=actions::commitZoom,
                        onDragCancel={}, onDrag={change, _ -> change.consume();preview(change.position.y)})
                }) {
                val x=size.width/2; val pad=8.dp.toPx()
                drawLine(Color(0xFF757D78),Offset(x,pad),Offset(x,size.height-pad),3.dp.toPx(),StrokeCap.Round)
                val y=pad+(size.height-2*pad)*(1f-(state.zoom-1f)/11f)
                drawCircle(Color.White,7.dp.toPx(),Offset(x,y))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement=Arrangement.SpaceEvenly,
                horizontalAlignment=Alignment.CenterHorizontally) {
                listOf(12f,6f,3f,2f,1f).forEach { zoom ->
                    Text("${zoom.toInt()}×", Modifier.background(if(kotlin.math.abs(state.zoom-zoom)<.1f) Color(0xFF4C5550) else Color(0xB0202522),RoundedCornerShape(20.dp))
                        .clickable(enabled=!state.cameraControlBusy) {actions.setZoomPreset(zoom)}.padding(6.dp),color=Color.White,fontSize=10.sp)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.GimbalControl(state: MonitorUiState, actions: MonitorViewModel) {
    val right = state.controlsSide != DrawerSide.RIGHT
    Column(Modifier.align(if(right) Alignment.CenterEnd else Alignment.CenterStart)
        .fillMaxHeight().padding(top=48.dp,bottom=68.dp,start=10.dp,end=10.dp),
        verticalArrangement=Arrangement.Center, horizontalAlignment=if(right) Alignment.End else Alignment.Start) {
        NeutralButton(UiText.get(state.language, if(state.gimbalExpanded) "collapse" else "gimbal"), true,actions::toggleGimbal)
        if(state.gimbalExpanded) {
            Column(Modifier.weight(1f).width(156.dp).background(Color(0xD51A1F1C),RoundedCornerShape(20.dp)).padding(8.dp).monitorScroll(),
                horizontalAlignment=Alignment.CenterHorizontally) {
                var stick by remember { mutableStateOf(Offset.Zero) }
                DisposableEffect(Unit) { onDispose { actions.stopGimbal() } }
                val enabled=state.connection in setOf(ConnectionState.STREAMING,ConnectionState.WAITING_VIDEO) && com.lunamonitor.beta.protocol.GimbalProtocol.supportsFirmware(state.cameraFirmware)
                val description=UiText.get(state.language,"gimbalDirections")
                Canvas(Modifier.size(132.dp).semantics {contentDescription=description}.pointerInput(enabled) {
                    if(enabled) detectDragGestures(
                        onDragStart={point -> stick=point-Offset(size.width/2f,size.height/2f);actions.moveGimbal(stick.x/(size.width*.38f),stick.y/(size.height*.38f))},
                        onDragEnd={stick=Offset.Zero;actions.stopGimbal()},
                        onDragCancel={stick=Offset.Zero;actions.stopGimbal()},
                        onDrag={change,_->change.consume();stick=change.position-Offset(size.width/2f,size.height/2f);actions.moveGimbal(stick.x/(size.width*.38f),stick.y/(size.height*.38f))})
                }) {
                    val c=Offset(size.width/2,size.height/2);val radius=size.minDimension*.38f
                    drawCircle(Color(0xFF363F39),radius,c)
                    drawCircle(Color(0xFF8F9C93),radius,c,style=Stroke(1.dp.toPx()))
                    drawLine(Color(0xFF707B73),Offset(c.x-radius,c.y),Offset(c.x+radius,c.y),1.dp.toPx())
                    drawLine(Color(0xFF707B73),Offset(c.x,c.y-radius),Offset(c.x,c.y+radius),1.dp.toPx())
                    val length=stick.getDistance();val offset=if(length>radius) stick*(radius/length) else stick
                    drawCircle(if(enabled) Color(0xFFD6DED8) else Color.Gray,16.dp.toPx(),c+offset)
                }
                Text("↑ ↓ ← →",color=Color.LightGray,fontSize=12.sp)
                if (!enabled) Text(UiText.get(state.language,"gimbalFirmware"),fontSize=9.sp,color=Color.LightGray)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    NeutralButton(UiText.get(state.language,"recenter"),enabled && !state.cameraControlBusy,{actions.gimbalAction(2)},Modifier.weight(1f))
                    NeutralButton(UiText.get(state.language,"flip"),enabled && !state.cameraControlBusy,{actions.gimbalAction(3)},Modifier.weight(1f))
                }
                Text(UiText.get(state.language,"gimbalRelease"),fontSize=9.sp,color=Color.LightGray)
            }
        }
    }
}

@Composable
private fun CameraDrawerHandle(expanded: Boolean, language: AppLanguage, action: () -> Unit) {
    Column(
        Modifier.clickable(onClick = action).background(Color(0xFF252827), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineGlyph(MonitorGlyph.CAMERA, Color(0xFFE7E8E4), Modifier.size(23.dp))
        Text(if (expanded) UiText.get(language,"collapse") else UiText.get(language,"camera"), fontSize = 9.sp, color = Color(0xFFE7E8E4))
    }
}

@Composable
private fun QuickZoomButton(value: Float, selected: Float, height: androidx.compose.ui.unit.Dp, action: (Float) -> Unit) {
    val active = kotlin.math.abs(selected - value) < 0.05f
    Button(
        onClick = { action(value) },
        modifier = Modifier.width(42.dp).height(height),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF3A3E3C) else Color(0xFF222524),
            contentColor = Color(0xFFE7E8E4),
        ),
    ) { Text("${value.toInt()}X", fontSize = 10.sp) }
}

@Composable
private fun BoxScope.ThermalSuggestion(message: String, language: AppLanguage, actions: MonitorViewModel) {
    Row(
        Modifier.align(Alignment.TopCenter).padding(top = 52.dp).background(Color(0xF21D1F1E), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF4C4F4C), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(UiText.runtime(language,message), fontSize = 11.sp); Spacer(Modifier.width(12.dp))
        Text(UiText.get(language,"agree"), Modifier.clickable(onClick = actions::acceptThermalReduction).padding(6.dp), fontSize = 11.sp)
        Text(UiText.get(language,"keep"), Modifier.clickable(onClick = actions::dismissThermalSuggestion).padding(6.dp), fontSize = 11.sp, color = Color(0xFFB8BCB8))
    }
}

@Composable
private fun BoxScope.ErrorBubble(message: String, language: AppLanguage, actions: MonitorViewModel) {
    Row(
        Modifier.align(Alignment.TopCenter).padding(top = 52.dp).background(Color(0xF2282222), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(UiText.runtime(language,message), fontSize = 11.sp); Spacer(Modifier.width(14.dp))
        Text(UiText.get(language,"close"), Modifier.clickable(onClick = actions::clearError).padding(5.dp), fontSize = 11.sp)
    }
}

@Composable
private fun NeutralButton(label: String, enabled: Boolean, action: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = action, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252827), contentColor = Color(0xFFE7E8E4), disabledContainerColor = Color(0xFF171919), disabledContentColor = Color(0xFF8E918D)),
    ) { Text(label, fontSize = 12.sp) }
}

@Composable
private fun GuideOverlay(state: MonitorUiState) {
    if (!state.showThirds && !state.showCenter && !state.showSafeArea) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat(); val viewAspect = w / h
        val videoAspect = state.streamStats.width.toFloat() / state.streamStats.height.coerceAtLeast(1)
        val rect = when (state.scaleMode) {
            ScaleMode.FIT -> if (viewAspect > videoAspect) { val vw = h * videoAspect; Rect((w - vw) / 2, 0f, (w + vw) / 2, h) }
                else { val vh = w / videoAspect; Rect(0f, (h - vh) / 2, w, (h + vh) / 2) }
            else -> Rect(0f, 0f, w, h)
        }
        Canvas(Modifier.fillMaxSize()) {
            val line = Color.White.copy(alpha = state.guideOpacity)
            val strokeWidth = state.guideWidth.dp.toPx()
            if (state.showThirds) {
                drawLine(line, Offset(rect.left + rect.width / 3, rect.top), Offset(rect.left + rect.width / 3, rect.bottom), strokeWidth)
                drawLine(line, Offset(rect.left + rect.width * 2 / 3, rect.top), Offset(rect.left + rect.width * 2 / 3, rect.bottom), strokeWidth)
                drawLine(line, Offset(rect.left, rect.top + rect.height / 3), Offset(rect.right, rect.top + rect.height / 3), strokeWidth)
                drawLine(line, Offset(rect.left, rect.top + rect.height * 2 / 3), Offset(rect.right, rect.top + rect.height * 2 / 3), strokeWidth)
            }
            if (state.showCenter) {
                val halfCenter = state.centerMarkSize.dp.toPx() / 2f
                drawLine(line, Offset(rect.center.x - halfCenter, rect.center.y), Offset(rect.center.x + halfCenter, rect.center.y), strokeWidth)
                drawLine(line, Offset(rect.center.x, rect.center.y - halfCenter), Offset(rect.center.x, rect.center.y + halfCenter), strokeWidth)
            }
            if (state.showSafeArea) {
                val insetX = rect.width * .05f; val insetY = rect.height * .05f
                drawRect(line, Offset(rect.left + insetX, rect.top + insetY), Size(rect.width - insetX * 2, rect.height - insetY * 2), style = Stroke(strokeWidth))
            }
        }
    }
}

/** The indicators only exist while more content is actually reachable. */
private fun Modifier.scrollEdges(backward: () -> Boolean, forward: () -> Boolean): Modifier = drawWithContent {
    drawContent()
    val h = 20.dp.toPx().coerceAtMost(size.height/4)
    if (backward()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xE0111512),Color.Transparent),0f,h),size=Size(size.width,h))
        val x=size.width/2
        drawLine(Color.LightGray,Offset(x-4.dp.toPx(),8.dp.toPx()),Offset(x,4.dp.toPx()),1.5.dp.toPx())
        drawLine(Color.LightGray,Offset(x,4.dp.toPx()),Offset(x+4.dp.toPx(),8.dp.toPx()),1.5.dp.toPx())
    }
    if (forward()) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color(0xE0111512)),size.height-h,size.height),topLeft=Offset(0f,size.height-h),size=Size(size.width,h))
        val x=size.width/2; val y=size.height-5.dp.toPx()
        drawLine(Color.White,Offset(x-4.dp.toPx(),y-4.dp.toPx()),Offset(x,y),1.5.dp.toPx())
        drawLine(Color.White,Offset(x,y),Offset(x+4.dp.toPx(),y-4.dp.toPx()),1.5.dp.toPx())
    }
}

@Composable
private fun Modifier.monitorScroll(): Modifier {
    val scroll=rememberScrollState()
    return this.scrollEdges({scroll.canScrollBackward},{scroll.canScrollForward}).verticalScroll(scroll)
}
