package ru.dot.clipshot;

/** Position and rotation of a draft caption in image pixels. */
final class CaptionTransform {
    static final float MIN_SIZE=.005f;
    static final int NONE=0, INSIDE=1, CORNER=2, X_EDGE=3, Y_EDGE=4;
    float cx,cy,angle;

    CaptionTransform(float x,float y) { cx=x; cy=y; }

    float x(float u,float v) {
        double r=Math.toRadians(angle);
        return cx+(float)(Math.cos(r)*u-Math.sin(r)*v);
    }
    float y(float u,float v) {
        double r=Math.toRadians(angle);
        return cy+(float)(Math.sin(r)*u+Math.cos(r)*v);
    }
    float localX(float dx,float dy) {
        double r=Math.toRadians(angle);
        return (float)(Math.cos(r)*dx+Math.sin(r)*dy);
    }
    float localY(float dx,float dy) {
        double r=Math.toRadians(angle);
        return (float)(-Math.sin(r)*dx+Math.cos(r)*dy);
    }
    /** Converts a fixed oriented-image point to the box's local top-left axes. */
    float boxLocalX(float x,float y,float halfW) { return localX(x-cx,y-cy)+halfW; }
    float boxLocalY(float x,float y,float halfH) { return localY(x-cx,y-cy)+halfH; }
    int hit(float x,float y,float w,float h,float slop) {
        float sx=Math.abs(localX(x-cx,y-cy)),sy=Math.abs(localY(x-cx,y-cy));
        float W=w,H=h;
        if (sx>W+slop||sy>H+slop) return NONE;
        float dx=Math.abs(sx-W),dy=Math.abs(sy-H);
        if (dx<=slop && dy<=slop) return CORNER;
        if (dx<=slop) return X_EDGE;
        if (dy<=slop) return Y_EDGE;
        return INSIDE;
    }
    void move(float x,float y,float left,float top,float right,float bottom) {
        cx=clamp(x,left,right);
        cy=clamp(y,top,bottom);
    }
    static float symbolBoxScale(float proposed,float width,float height,float minWidth,float minHeight,float maxSide) {
        float hi=Math.max(1,Math.min(maxSide/width,maxSide/height));
        float lo=Math.min(hi,Math.max(minWidth/width,minHeight/height));
        return clamp(proposed,lo,hi);
    }
    static float fontFromGrip(float original,float x,float y,float dx,float dy,float low,float high) {
        float ratio=(float)(Math.hypot(x+dx,y+dy)/Math.max(.01,Math.hypot(x,y)));
        return clamp(original*ratio,low,high);
    }
    static float fit(float width,float height,float maxW,float maxH) {
        return clamp(Math.min(maxW/width,maxH/height),MIN_SIZE,1);
    }
    static String textForLayout(String text,boolean wrap) {
        return wrap?text:text.replaceAll("\\s*[\\r\\n\\u0085\\u2028\\u2029]+\\s*"," ");
    }
    static float rotated(float initial,float x,float y,float dx,float dy) {
        float destX=x+dx,destY=y+dy;
        if (Math.hypot(destX,destY)<1f) return initial;
        double delta=Math.toDegrees(Math.atan2(destY,destX)-Math.atan2(y,x));
        return (float)Math.IEEEremainder(initial+delta,360);
    }
    void axes(float a,float b,float c,float d,float[] out) {
        double r=Math.toRadians(angle);
        float u=(float)Math.cos(r), v=(float)Math.sin(r);
        out[0]=(a*u+b*v); out[1]=(-a*v+b*u);
        out[2]=(c*u+d*v); out[3]=(-c*v+d*u);
    }
    private static float clamp(float v,float min,float max) { return Math.max(min,Math.min(max,v)); }
}
