// 固定控制点布局的 G2 距离场，供全部材质通道共享。
package top.yukonga.miuix.kmp.material.agsl

internal val G2SilhouetteAgsl: String = """
// 同一 G2 三次曲线的距离与法线，控制点直接来自实际 Compose 裁剪路径。
uniform float2 in_size;
uniform float4 in_radii;
uniform float in_smoothing;
uniform float in_g2Scale;
uniform float4 in_g2Curves[24];

float roundedBoxSdf(float2 p, float2 b, float r) {
    float2 d = p - b + r;
    return min(max(d.x, d.y), 0.0) + length(max(d, float2(0.0))) - r;
}
float pickRadius(float2 p, float2 b) {
    float top = p.x < b.x ? in_radii.x : in_radii.y;
    float bottom = p.x < b.x ? in_radii.w : in_radii.z;
    return min(p.y < b.y ? top : bottom, min(b.x, b.y));
}
float2 g2Line(float2 p, float2 a, float2 b) {
    float2 edge = b-a;
    float den = dot(edge,edge);
    float t = den > 0.0000001 ? clamp(dot(p-a,edge)/den,0.0,1.0) : 0.0;
    float2 delta = p-mix(a,b,t);
    return float2(dot(delta,delta), edge.x*delta.y-edge.y*delta.x);
}
float2 g2Bezier(float2 a, float2 b, float2 c, float2 d, float t) {
    float u=1.0-t;
    return u*u*u*a+3.0*u*u*t*b+3.0*u*t*t*c+t*t*t*d;
}
float2 g2Curve(float2 p, float2 a, float2 b, float2 c, float2 d) {
    float2 chord=d-a;
    float t=clamp(dot(p-a,chord)/max(dot(chord,chord),0.0000001),0.0,1.0);
    float lo=0.0;
    float hi=1.0;
    // 有界牛顿迭代避免超出曲线段；端点参与最后的最近距离比较。
    for (int step=0;step<10;step++) {
        float u=1.0-t;
        float2 q=g2Bezier(a,b,c,d,t);
        float2 v=3.0*(u*u*(b-a)+2.0*u*t*(c-b)+t*t*(d-c));
        float2 acc=6.0*(u*(c-2.0*b+a)+t*(d-2.0*c+b));
        float f=dot(q-p,v);
        if(f>0.0) hi=t; else lo=t;
        float den=dot(v,v)+dot(q-p,acc);
        float candidate=den>0.0000001 ? t-f/den : (lo+hi)*0.5;
        if(abs(f)<0.000001) break;
        t=(candidate>lo && candidate<hi) ? candidate : (lo+hi)*0.5;
    }
    float2 q=g2Bezier(a,b,c,d,t);
    float2 tangent=3.0*((1.0-t)*(1.0-t)*(b-a)+2.0*(1.0-t)*t*(c-b)+t*t*(d-c));
    float2 delta=p-q;
    float dist=dot(delta,delta);
    if(dot(p-a,p-a)<dist) {delta=p-a;tangent=b-a;dist=dot(delta,delta);}
    if(dot(p-d,p-d)<dist) {delta=p-d;tangent=d-c;dist=dot(delta,delta);}
    return float2(dist,tangent.x*delta.y-tangent.y*delta.x);
}
float2 g2Nearest(float2 a,float2 b) { return a.x<=b.x ? a : b; }
// 贝塞尔曲线位于控制点凸包内，远离转角时直接使用直线距离。
float2 g2BoundedCurve(float2 p,float2 a,float2 b,float2 c,float2 d,float2 best) {
    float2 low=min(min(a,b),min(c,d));
    float2 high=max(max(a,b),max(c,d));
    float2 delta=max(max(low-p,p-high),float2(0.0));
    if(dot(delta,delta)>=best.x) return best;
    return g2Nearest(best,g2Curve(p,a,b,c,d));
}

float g2Corner0(float2 p, float2 b) {
    float4 c0=in_g2Curves[0]/in_g2Scale;
    float4 c1=in_g2Curves[1]/in_g2Scale;
    float4 c2=in_g2Curves[2]/in_g2Scale;
    float4 c3=in_g2Curves[3]/in_g2Scale;
    float4 c4=in_g2Curves[4]/in_g2Scale;
    float4 c5=in_g2Curves[5]/in_g2Scale;
    float2 near=g2Line(p,float2(b.x,0.0),c0.xy);
    near=g2Nearest(near,g2Line(p,c5.zw,float2(in_size.x,b.y)));
    near=g2BoundedCurve(p,c0.xy,c0.zw,c1.xy,c1.zw,near);
    near=g2BoundedCurve(p,c2.xy,c2.zw,c3.xy,c3.zw,near);
    near=g2BoundedCurve(p,c4.xy,c4.zw,c5.xy,c5.zw,near);
    return sqrt(max(near.x,0.0))*(near.y>=0.0 ? -1.0 : 1.0);
}

float g2Corner1(float2 p, float2 b) {
    float4 c0=in_g2Curves[6]/in_g2Scale;
    float4 c1=in_g2Curves[7]/in_g2Scale;
    float4 c2=in_g2Curves[8]/in_g2Scale;
    float4 c3=in_g2Curves[9]/in_g2Scale;
    float4 c4=in_g2Curves[10]/in_g2Scale;
    float4 c5=in_g2Curves[11]/in_g2Scale;
    float2 near=g2Line(p,float2(in_size.x,b.y),c0.xy);
    near=g2Nearest(near,g2Line(p,c5.zw,float2(b.x,in_size.y)));
    near=g2BoundedCurve(p,c0.xy,c0.zw,c1.xy,c1.zw,near);
    near=g2BoundedCurve(p,c2.xy,c2.zw,c3.xy,c3.zw,near);
    near=g2BoundedCurve(p,c4.xy,c4.zw,c5.xy,c5.zw,near);
    return sqrt(max(near.x,0.0))*(near.y>=0.0 ? -1.0 : 1.0);
}

float g2Corner2(float2 p, float2 b) {
    float4 c0=in_g2Curves[12]/in_g2Scale;
    float4 c1=in_g2Curves[13]/in_g2Scale;
    float4 c2=in_g2Curves[14]/in_g2Scale;
    float4 c3=in_g2Curves[15]/in_g2Scale;
    float4 c4=in_g2Curves[16]/in_g2Scale;
    float4 c5=in_g2Curves[17]/in_g2Scale;
    float2 near=g2Line(p,float2(b.x,in_size.y),c0.xy);
    near=g2Nearest(near,g2Line(p,c5.zw,float2(0.0,b.y)));
    near=g2BoundedCurve(p,c0.xy,c0.zw,c1.xy,c1.zw,near);
    near=g2BoundedCurve(p,c2.xy,c2.zw,c3.xy,c3.zw,near);
    near=g2BoundedCurve(p,c4.xy,c4.zw,c5.xy,c5.zw,near);
    return sqrt(max(near.x,0.0))*(near.y>=0.0 ? -1.0 : 1.0);
}

float g2Corner3(float2 p, float2 b) {
    float4 c0=in_g2Curves[18]/in_g2Scale;
    float4 c1=in_g2Curves[19]/in_g2Scale;
    float4 c2=in_g2Curves[20]/in_g2Scale;
    float4 c3=in_g2Curves[21]/in_g2Scale;
    float4 c4=in_g2Curves[22]/in_g2Scale;
    float4 c5=in_g2Curves[23]/in_g2Scale;
    float2 near=g2Line(p,float2(0.0,b.y),c0.xy);
    near=g2Nearest(near,g2Line(p,c5.zw,float2(b.x,0.0)));
    near=g2BoundedCurve(p,c0.xy,c0.zw,c1.xy,c1.zw,near);
    near=g2BoundedCurve(p,c2.xy,c2.zw,c3.xy,c3.zw,near);
    near=g2BoundedCurve(p,c4.xy,c4.zw,c5.xy,c5.zw,near);
    return sqrt(max(near.x,0.0))*(near.y>=0.0 ? -1.0 : 1.0);
}

float silhouetteSdf(float2 p) {
    float2 b=in_size*0.5;
    float r=pickRadius(p,b);
    if(in_smoothing<=0.0 || r<=0.0) return roundedBoxSdf(abs(p-b),b,r);
    if(p.y<b.y) return p.x<b.x ? g2Corner3(p,b) : g2Corner0(p,b);
    return p.x<b.x ? g2Corner2(p,b) : g2Corner1(p,b);
}
"""
