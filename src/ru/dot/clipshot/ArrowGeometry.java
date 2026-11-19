package ru.dot.clipshot;

final class ArrowGeometry {
    private ArrowGeometry() {}
    static float[] head(float x0, float y0, float x, float y, float stroke) {
        float dx = x - x0, dy = y - y0;
        double length = Math.hypot(dx, dy);
        if (length < stroke) return null;
        float ux = (float)(dx / length), uy = (float)(dy / length);
        float back = Math.min(stroke * 4.8f, (float) length * 0.46f);
        float side = back * 0.5f;
        float bx = x - ux * back, by = y - uy * back;
        return new float[]{bx - uy * side, by + ux * side, bx + uy * side, by - ux * side};
    }
}
