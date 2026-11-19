package ru.dot.clipshot;

/** Keeps the style panel within the editor's safe area, ideally above the anchor. */
final class ThicknessPlacement {
    final int x, y, width, height;

    private ThicknessPlacement(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    static ThicknessPlacement fit(int left, int top, int right, int bottom, int anchorRight,
            int anchorTop, int preferredWidth, int preferredHeight, int minHeight, int gap) {
        int width = Math.min(preferredWidth, Math.max(0, right - left));
        int above = anchorTop - gap - top;
        int height = Math.min(preferredHeight, Math.min(Math.max(0, bottom - top),
                Math.max(minHeight, above)));
        int x = Math.max(left, Math.min(anchorRight - width, right - width));
        int y = Math.max(top, Math.min(anchorTop - gap - height, bottom - height));
        return new ThicknessPlacement(x, y, width, height);
    }
}
