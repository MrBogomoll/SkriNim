package ru.dot.clipshot;

final class InkOpacity {
    private InkOpacity() {}
    static int clamp(int percent) { return Math.max(0, Math.min(100, percent)); }
    static int apply(int color, int percent) {
        int alpha = Math.round((color >>> 24) * (clamp(percent) / 100f));
        return (color & 0x00ffffff) | (alpha << 24);
    }
}
