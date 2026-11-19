package ru.dot.clipshot;

/** View-only zoom. The same scale and offset must be applied to image and crop. */
final class EditorZoom {
    float scale=1, left, top;

    void reset() { scale=1; }

    void update(float fitW,float fitH,float oldLeft,float oldTop,int viewW,int viewH,
                float oldX,float oldY,float x,float y,float spanRatio) {
        float next=Math.max(1f,Math.min(8f,scale*spanRatio));
        float gain=next/scale;
        float pad=Math.max(0,Math.min(viewW-fitW,viewH-fitH)/2);
        left=bound(x-(oldX-oldLeft)*gain,fitW*next,viewW,pad);
        top=bound(y-(oldY-oldTop)*gain,fitH*next,viewH,pad);
        scale=next;
    }

    private static float bound(float start,float size,float space,float pad) {
        if (size<=space-2*pad) return (space-size)/2;
        return Math.max(space-pad-size,Math.min(pad,start));
    }

    /** Once two fingers touch, consume the full stream, including the last finger's up. */
    static final class Gesture {
        private boolean captured;
        boolean capture(boolean down,boolean end,int count) {
            if (down) captured=false;
            boolean consume=captured || count>=2;
            captured=!end && consume;
            return consume;
        }
        boolean isCaptured() { return captured; }
    }
}
