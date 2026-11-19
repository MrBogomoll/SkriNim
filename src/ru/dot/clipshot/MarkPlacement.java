package ru.dot.clipshot;

/** Start upright at the exact point in oriented image coordinates. */
final class MarkPlacement {
    static final int MAX_INITIAL_WIDTH_PX = 2048;

    static CaptionTransform place(float x,float y) { return new CaptionTransform(x,y); }
    static float radius(int width,int height) { return Math.max(13,Math.min(width,height)*.029f); }
    static int normalizeInitialWidth(int widthPx) { return Math.max(0, Math.min(MAX_INITIAL_WIDTH_PX,widthPx)); }

    static float radius(int width,int height,int initialWidthPx) {
        int explicit = normalizeInitialWidth(initialWidthPx);
        return explicit == 0 ? radius(width,height) : explicit / 2f;
    }

    /** Larger editor grips do not enlarge the actual mark. Both extents share units. */
    static float handleHalfExtent(float actualHalf,float minDisplayHalf) {
        return Math.max(actualHalf,minDisplayHalf);
    }

    static float cornerScale(float radialGestureScale,float actualHalfWidth,float displayHalfWidth) {
        if (!Float.isFinite(radialGestureScale) || radialGestureScale == 1f) return 1f;
        float ratio = actualHalfWidth > 0f ? Math.max(1f,displayHalfWidth/actualHalfWidth) : 1f;
        return Math.max(.05f, 1f + (radialGestureScale - 1f) * ratio);
    }

    private MarkPlacement() {}
}
