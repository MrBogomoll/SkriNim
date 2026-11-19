package ru.dot.clipshot;

final class StrokeSize {
    private static final float[] LEGACY = {0.45f, 0.7f, 1f, 1.55f, 2.3f};
    private static final float ERASER_BASE = 6f;
    final int min, max;
    private final float base;

    StrokeSize(int shortSide) { this(shortSide, 1f); }

    StrokeSize(int shortSide, float multiplier) { this(shortSide, multiplier, 1); }

    private StrokeSize(int shortSide, float multiplier, int maxMultiplier) {
        base = Math.max(2f, shortSide * 0.0048f) * multiplier;
        min = Math.max(1, Math.round(base * LEGACY[0]));
        max = Math.round(base * 3.5f) * maxMultiplier;
    }

    static StrokeSize caption(int shortSide) { return new StrokeSize(shortSide,8f,5); }

    static int captionWidth(int shortSide,int savedPixels,int oldProgress) {
        return savedPixels>0?caption(shortSide).clamp(savedPixels):
                new StrokeSize(shortSide,8f).fromProgress(oldProgress);
    }

    // Keep most of the travel for the familiar range; the top adds large display text.
    private static final float ORIGINAL_CAPTION_TRAVEL=.6f;
    static float sliderFraction(int min,int max,int pivot,int value) {
        if (pivot<=min||pivot>=max) return (value-min)/(float)(max-min);
        if (value<=pivot) return ORIGINAL_CAPTION_TRAVEL*(value-min)/(pivot-min);
        return ORIGINAL_CAPTION_TRAVEL+(1-ORIGINAL_CAPTION_TRAVEL)*(value-pivot)/(max-pivot);
    }
    static int sliderValue(int min,int max,int pivot,float fraction) {
        fraction=Math.max(0,Math.min(1,fraction));
        if (pivot<=min||pivot>=max) return Math.round(min+(max-min)*fraction);
        if (fraction<=ORIGINAL_CAPTION_TRAVEL)
            return Math.round(min+(pivot-min)*fraction/ORIGINAL_CAPTION_TRAVEL);
        return Math.round(pivot+(max-pivot)*(fraction-ORIGINAL_CAPTION_TRAVEL)/(1-ORIGINAL_CAPTION_TRAVEL));
    }

    static StrokeSize eraser(int shortSide) {
        return new StrokeSize(shortSide, ERASER_BASE, 3);
    }

    static int eraserWidth(int shortSide, int progress, int oldProgress) {
        if (progress < 0) return new StrokeSize(shortSide, ERASER_BASE).fromProgress(oldProgress);
        return eraser(shortSide).fromProgress(progress);
    }

    int clamp(int value) { return Math.max(min, Math.min(max, value)); }
    float fraction(int value) { return (clamp(value) - min) / (float) (max - min); }
    int fromProgress(int progress) { return clamp(Math.round(min + (max - min) * (progress / 1000f))); }
    int progress(int value) { return Math.round(fraction(value) * 1000f); }
    int fromLegacy(int index) {
        if (index < 0 || index >= LEGACY.length) index = 2;
        return clamp(Math.round(base * LEGACY[index]));
    }
    static int atY(int min, int max, float y, float top, float bottom) {
        int value = Math.round(min + (max - min) * (bottom - y) / Math.max(1f, bottom - top));
        return Math.max(min, Math.min(max, value));
    }
}
