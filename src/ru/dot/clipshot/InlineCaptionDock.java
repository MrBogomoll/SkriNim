package ru.dot.clipshot;

/** View-only offset for a caption field, never an image or draft coordinate change. */
final class InlineCaptionDock {
    private InlineCaptionDock() {}

    static int bottom(int rootHeight,int baselineHeight,int imeInset,int safeBottom,int rootTop,int visibleBottom,
                      int panelHeight,int gap) {
        int obscured=visibleBottom>rootTop?Math.max(0,rootTop+rootHeight-visibleBottom):0;
        int consumed=Math.max(0,baselineHeight-rootHeight);
        int used=Math.max(Math.max(safeBottom,Math.max(0,imeInset-consumed)),obscured);
        return Math.max(0,Math.min(rootHeight-panelHeight,used+gap));
    }

    static float shift(float centerY,float width,float height,float angle,float safeTop,float safeBottom) {
        if (safeBottom<=safeTop) return 0;
        double radians=Math.toRadians(angle);
        float projectedHalf=(float)(Math.abs(Math.sin(radians))*width
                +Math.abs(Math.cos(radians))*height)*.5f;
        float lo=safeTop+projectedHalf,hi=safeBottom-projectedHalf;
        // If the field cannot fit entirely, keep its center in the available
        // area. EditText still scrolls its contents and the canvas stays fixed.
        float target=lo>hi?(safeTop+safeBottom)*.5f:Math.max(lo,Math.min(hi,centerY));
        return target-centerY;
    }
}
