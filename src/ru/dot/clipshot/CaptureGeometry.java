package ru.dot.clipshot;

/** Position of a full or maximized captured window on the default display. */
final class CaptureGeometry {
    final int left, top, width, height;

    private CaptureGeometry(int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    static CaptureGeometry resolve(int screenWidth, int screenHeight, int width, int height,
                                   int leftInset, int topInset, int rightInset, int bottomInset,
                                   boolean fullscreenSource) {
        if (width <= 0 || height <= 0 || width > screenWidth || height > screenHeight) return null;
        if (width == screenWidth && height == screenHeight) return new CaptureGeometry(0, 0, width, height);
        // Projection reports the selected window's size without its position.
        // Recognize only a nearly full display cropped exactly by known system
        // bars/cutouts, with a foreground activity that announced full screen.
        if (!fullscreenSource || (long)width * 100 < (long)screenWidth * 82
                || (long)height * 100 < (long)screenHeight * 82) return null;
        int x = insetOrigin(screenWidth-width, leftInset, rightInset);
        int y = insetOrigin(screenHeight-height, topInset, bottomInset);
        return x >= 0 && y >= 0 ? new CaptureGeometry(x, y, width, height) : null;
    }

    private static int insetOrigin(int removed, int leading, int trailing) {
        if (removed == 0) return 0;
        if (leading < 0 || trailing < 0) return -1;
        if (removed == leading + trailing) return leading;
        if (removed == leading && removed == trailing) return -1;
        if (removed == leading) return leading;
        return removed == trailing ? 0 : -1;
    }

    float displayX(float fraction) { return left + width * fraction; }
    float displayY(float fraction) { return top + height * fraction; }
}
