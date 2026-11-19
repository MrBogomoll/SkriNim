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

    static void pixels(int w,int h,float l,float t,float r,float b,int numerator,int denominator,int[] out) {
        if(numerator>0 && denominator>0) {
            int max=Math.min(w/numerator,h/denominator);
            if(max>0) {
                int k=Math.max(1,Math.min(max,Math.round(((r-l)/numerator+(b-t)/denominator)/2)));
                int cw=k*numerator,ch=k*denominator;
                out[0]=clampInt(Math.round((l+r-cw)/2),0,w-cw);
                out[1]=clampInt(Math.round((t+b-ch)/2),0,h-ch);
                out[2]=out[0]+cw;out[3]=out[1]+ch;
                return;
            }
        }
        out[0]=clampInt(Math.round(l),0,w-1);
        out[1]=clampInt(Math.round(t),0,h-1);
        out[2]=clampInt(Math.round(r),out[0]+1,w);
        out[3]=clampInt(Math.round(b),out[1]+1,h);
    }

    private static void bounds(float l,float t,float r,float b,float[] out) {
        out[0]=l;out[1]=t;out[2]=r;out[3]=b;
    }
    private static float clamp(float n,float min,float max) { return Math.max(min,Math.min(n,max)); }
    private static int clampInt(int n,int min,int max) { return Math.max(min,Math.min(n,max)); }
}
