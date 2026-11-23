package ru.dot.clipshot;

final class CropAspect {
    static final int LEFT=1, RIGHT=2, TOP=4, BOTTOM=8;
    private CropAspect() {}

    static void fit(float w,float h,float cx,float cy,float boxW,float boxH,float ratio,float[] out) {
        float cw=Math.min(Math.min(w,boxW),Math.min(h,boxH)*ratio);
        float ch=cw/ratio;
        float l=clamp(cx-cw/2,0,w-cw), t=clamp(cy-ch/2,0,h-ch);
        bounds(l,t,l+cw,t+ch,out);
    }

    static void resize(float w,float h,float l,float t,float r,float b,int drag,
            float dx,float dy,float minSide,float ratio,float[] out) {
        int sx=(drag&LEFT)!=0?-1:(drag&RIGHT)!=0?1:0;
        int sy=(drag&TOP)!=0?-1:(drag&BOTTOM)!=0?1:0;
        float x=sx<0?r:sx>0?l:(l+r)/2;
        float y=sy<0?b:sy>0?t:(t+b)/2;
        float roomW=sx<0?x:sx>0?w-x:2*Math.min(x,w-x);
        float roomH=sy<0?y:sy>0?h-y:2*Math.min(y,h-y);
        float limit=Math.max(0,Math.min(roomW,roomH*ratio));
        float want=(r-l)+(sx==0?sy*dy*ratio:sy==0?sx*dx:(sx*dx+sy*dy/ratio)/(1+1/(ratio*ratio)));
        float cw=clamp(want,Math.min(limit,Math.max(minSide,minSide*ratio)),limit), ch=cw/ratio;
        float nl=sx<0?x-cw:sx>0?x:x-cw/2;
        float nt=sy<0?y-ch:sy>0?y:y-ch/2;
        bounds(nl,nt,nl+cw,nt+ch,out);
    }

    /** Largest centered integer crop of the requested aspect inside all four selected edges. */
    static void pixels(int w, int h, float l, float t, float r, float b, int numerator, int denominator, int[] out) {
        int left = clampInt((int) Math.ceil(l), 0, w);
        int top = clampInt((int) Math.ceil(t), 0, h);
        int right = clampInt((int) Math.floor(r), left, w);
        int bottom = clampInt((int) Math.floor(b), top, h);
        if (numerator > 0 && denominator > 0) {
            int scale = Math.min((right - left) / numerator, (bottom - top) / denominator);
            int width = scale * numerator;
            int height = scale * denominator;
            left += (right - left - width) / 2;
            top += (bottom - top - height) / 2;
            right = left + width;
            bottom = top + height;
        }
        out[0] = left;
        out[1] = top;
        out[2] = right;
        out[3] = bottom;
    }

    private static void bounds(float l,float t,float r,float b,float[] out) {
        out[0]=l;out[1]=t;out[2]=r;out[3]=b;
    }
    private static float clamp(float n,float min,float max) { return Math.max(min,Math.min(n,max)); }
    private static int clampInt(int n,int min,int max) { return Math.max(min,Math.min(n,max)); }
}
