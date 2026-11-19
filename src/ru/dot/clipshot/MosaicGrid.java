package ru.dot.clipshot;

import java.util.BitSet;

/** Source-pixel tiles touched by one opaque stroke. */
final class MosaicGrid {
    final int cell, columns;
    private final int width, height, rows;
    private final BitSet covered = new BitSet();

    MosaicGrid(int width, int height, int cell) {
        this.width = width;
        this.height = height;
        this.cell = cell;
        columns = (width + cell - 1) / cell;
        rows = (height + cell - 1) / cell;
    }

    boolean isEmpty() { return covered.isEmpty(); }
    int next(int start) { return covered.nextSetBit(start); }
    boolean covers(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && covered.get((y / cell) * columns + x / cell);
    }
    void cover(float x0, float y0, float x1, float y1, float radius) {
        cover(x0, y0, x1, y1, radius, null);
    }
    void cover(float x0, float y0, float x1, float y1, float radius, java.util.function.IntConsumer added) {
        int left = Math.max(0, (int)Math.floor((Math.min(x0, x1) - radius) / cell));
        int top = Math.max(0, (int)Math.floor((Math.min(y0, y1) - radius) / cell));
        int right = Math.min(columns - 1, (int)Math.floor((Math.max(x0, x1) + radius) / cell));
        int bottom = Math.min(rows - 1, (int)Math.floor((Math.max(y0, y1) + radius) / cell));
        float vx = x1 - x0, vy = y1 - y0, length2 = vx * vx + vy * vy;
        // If the brush touches a tile, its center lies at most half a tile diagonal farther away.
        float reach = radius + 0.708f * cell, reach2 = reach * reach;
        for (int y = top; y <= bottom; ++y) for (int x = left; x <= right; ++x) {
            int index = y * columns + x;
            if (covered.get(index)) continue;
            float dx = (x + .5f) * cell - x0, dy = (y + .5f) * cell - y0;
            float along = length2 == 0 ? 0 : Math.max(0, Math.min(1, (dx * vx + dy * vy) / length2));
            dx -= along * vx;
            dy -= along * vy;
            if (dx * dx + dy * dy <= reach2) {
                covered.set(index);
                if (added != null) added.accept(index);
            }
        }
    }

    static int minCell(int shortSide) { return Math.max(4, Math.round(shortSide * .004f)); }
    static int maxCell(int shortSide) {
        return Math.max(minCell(shortSide) + 1, Math.min(144, Math.round(shortSide * .09f)));
    }
    static int average(int[] pixels, int count) {
        return averageComposite(pixels, null, count);
    }

    static int averageComposite(int[] pixels, int[] overlay, int count) {
        int red = 0, green = 0, blue = 0;
        for (int i = 0; i < count; i++) {
            int pixel = pixels[i];
            if (overlay != null) pixel = over(pixel, overlay[i]);
            red += (pixel >>> 16) & 255;
            green += (pixel >>> 8) & 255;
            blue += pixel & 255;
        }
        int half = count / 2;
        return 0xff000000 | (((red + half) / count) << 16)
                | (((green + half) / count) << 8) | ((blue + half) / count);
    }
    static int over(int base, int top) {
        int alpha = top >>> 24;
        if (alpha == 0) return base;
        if (alpha == 255) return top;
        int inv = 255 - alpha;
        int red = (((top >>> 16) & 255) * alpha + ((base >>> 16) & 255) * inv + 127) / 255;
        int green = (((top >>> 8) & 255) * alpha + ((base >>> 8) & 255) * inv + 127) / 255;
        int blue = ((top & 255) * alpha + (base & 255) * inv + 127) / 255;
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }
}
