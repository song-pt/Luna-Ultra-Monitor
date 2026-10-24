#include <metal_stdlib>
using namespace metal;
struct Vertex { float4 position [[position]]; float2 uv; };
struct Grade { float a[20]; float flags[4]; float geometry[4]; };
vertex Vertex monitorVertex(uint id [[vertex_id]]) {
    const float2 p[4]={float2(-1,-1),float2(1,-1),float2(-1,1),float2(1,1)};
    const float2 uv[4]={float2(0,1),float2(1,1),float2(0,0),float2(1,0)};
    return {float4(p[id],0,1),uv[id]};
}
constexpr sampler videoSampler(coord::normalized, address::clamp_to_edge, filter::linear);
float4 sampleVideo(texture2d<float> ytex,texture2d<float> uvtex,float2 uv,float fullRange) {
    float y=ytex.sample(videoSampler,uv).r;
    float2 cbcr=uvtex.sample(videoSampler,uv).rg-0.5;
    if(fullRange<0.5) {y=(y-16.0/255.0)*(255.0/219.0);cbcr*=255.0/224.0;}
    return float4(y+1.5748*cbcr.y,y-0.187324*cbcr.x-0.468124*cbcr.y,y+1.8556*cbcr.x,1);
}
float2 boundedUv(float2 p,float2 texel){return clamp(p,0.5*texel,1.0-0.5*texel);}
float3 applyLut(texture3d<float> lut,float3 c) {
    float n=lut.get_width();float3 uv=(clamp(c,0.0,1.0)*(n-1.0)+0.5)/n;
    return lut.sample(videoSampler,uv).rgb;
}
float3 falseMap(float x){
              if(x<0.05)return float3(0.28,0.08,0.42); if(x<0.18)return float3(0.08,0.22,0.55);
              if(x<0.42)return float3(0.10,0.48,0.48); if(x<0.62)return float3(0.42);
              if(x<0.78)return float3(0.62,0.52,0.18); if(x<0.93)return float3(0.62,0.25,0.16); return float3(0.78,0.72,0.70);
            }
            float3 hueRotate(float3 c,float a){
              float co=cos(a),si=sin(a); float3x3 m=float3x3(
                0.213+co*0.787-si*0.213,0.213-co*0.213+si*0.143,0.213-co*0.213-si*0.787,
                0.715-co*0.715-si*0.715,0.715+co*0.285+si*0.140,0.715-co*0.715+si*0.715,
                0.072-co*0.072+si*0.928,0.072-co*0.072-si*0.283,0.072+co*0.928+si*0.072);
              return clamp(m*c,0.0,1.0);
            }
            #define uExposure (u.a[0]/1.0)
#define uBrightness (u.a[1]/100.0)
#define uContrast (u.a[2]/100.0)
#define uHighlights (u.a[3]/100.0)
#define uShadows (u.a[4]/100.0)
#define uTemperature (u.a[5]/100.0)
#define uTint (u.a[6]/100.0)
#define uSaturation (u.a[7]/100.0)
#define uVibrance (u.a[8]/100.0)
#define uWhites (u.a[9]/100.0)
#define uBlacks (u.a[10]/100.0)
#define uSharpness (u.a[11]/100.0)
#define uMidtones (u.a[12]/100.0)
#define uGamma (u.a[13]/100.0)
#define uHue (u.a[14]/57.2957795)
#define uFade (u.a[15]/100.0)
#define uClarity (u.a[16]/100.0)
#define uDehaze (u.a[17]/100.0)
#define uHighlightSoftness (u.a[18]/100.0)
#define uVignette (u.a[19]/100.0)
#define uZebra (u.flags[0]>0.5)
#define uFalseColor (u.flags[1]>0.5)
#define uPeaking (u.flags[2]>0.5)
fragment float4 monitorFragment(Vertex in [[stage_in]],texture2d<float> ytex [[texture(0)]],texture2d<float> uvtex [[texture(1)]],texture3d<float> recovery [[texture(2)]],texture3d<float> style [[texture(3)]],constant Grade &u [[buffer(0)]]) {
    float2 texel=float2(1.0/ytex.get_width(),1.0/ytex.get_height());
    float2 uv=boundedUv((in.uv-0.5)*float2(u.geometry[0],u.geometry[1])+0.5,texel);
    float3 c=sampleVideo(ytex,uvtex,uv,u.geometry[2]).rgb;
    if(u.flags[3]>0.5) c=applyLut(recovery,c);
    if(u.geometry[3]>0.5) c=applyLut(style,c);
c*=exp2(uExposure); float l=dot(c,float3(0.2126,0.7152,0.0722));
              float sh=1.0-smoothstep(0.0,0.55,l); float hi=smoothstep(0.45,1.0,l);
              c+=uShadows*sh*0.35; c+=uHighlights*hi*0.28;
              c+=uBlacks*(1.0-smoothstep(0.0,0.34,l))*0.22; c+=uWhites*smoothstep(0.66,1.0,l)*0.22;
              c+=uMidtones*(4.0*l*(1.0-l))*0.18; c+=uBrightness;
              c=(c-0.5)*(1.0+uContrast)+0.5; c.r+=uTemperature*0.08; c.b-=uTemperature*0.08; c.g+=uTint*0.05;
              c=pow(max(c,float3(0.0001)),float3(exp2(-uGamma*0.8))); c=hueRotate(c,uHue);
              l=dot(c,float3(0.2126,0.7152,0.0722)); c=mix(float3(l),c,1.0+uSaturation);
              float chroma=max(c.r,max(c.g,c.b))-min(c.r,min(c.g,c.b)); c=mix(float3(l),c,1.0+uVibrance*(1.0-chroma));
              float3 n=sampleVideo(ytex,uvtex,boundedUv(uv+float2(0.0,texel.y),texel),u.geometry[2]).rgb, s=sampleVideo(ytex,uvtex,boundedUv(uv-float2(0.0,texel.y),texel),u.geometry[2]).rgb;
              float3 e=sampleVideo(ytex,uvtex,boundedUv(uv+float2(texel.x,0.0),texel),u.geometry[2]).rgb, w=sampleVideo(ytex,uvtex,boundedUv(uv-float2(texel.x,0.0),texel),u.geometry[2]).rgb;
              float3 detail=c-(n+s+e+w)*0.25; c+=detail*(uSharpness*0.48+uClarity*0.30);
              c=(c-0.5)*(1.0+uDehaze*0.28)+0.5; l=dot(c,float3(0.2126,0.7152,0.0722));
              float compressed=mix(l,l/(1.0+max(l-0.62,0.0)*1.6),uHighlightSoftness); c*=compressed/max(l,0.0001);
              c=mix(c,float3(0.12)+c*0.80,uFade); float vig=(1.0-smoothstep(0.28,0.78,length(in.uv-0.5)));
              c*=mix(1.0,clamp(vig,0.25,1.0),max(uVignette,0.0)); c=mix(c,c+(1.0-vig)*0.18,max(-uVignette,0.0));
              c=clamp(c,0.0,1.0); l=dot(c,float3(0.2126,0.7152,0.0722));
              if(uFalseColor)c=falseMap(l);
              if(uZebra && l>0.92 && fmod(in.position.x+in.position.y,12.0)<5.0)c=mix(c,float3(0.85),0.78);
              if(uPeaking){
                float lx=dot(sampleVideo(ytex,uvtex,boundedUv(uv+float2(texel.x,0.0),texel),u.geometry[2]).rgb-sampleVideo(ytex,uvtex,boundedUv(uv-float2(texel.x,0.0),texel),u.geometry[2]).rgb,float3(0.333));
                float ly=dot(sampleVideo(ytex,uvtex,boundedUv(uv+float2(0.0,texel.y),texel),u.geometry[2]).rgb-sampleVideo(ytex,uvtex,boundedUv(uv-float2(0.0,texel.y),texel),u.geometry[2]).rgb,float3(0.333));
                if(length(float2(lx,ly))>0.10)c=mix(c,float3(0.78,0.82,0.76),0.88);
              }
              return float4(c,1);
}
