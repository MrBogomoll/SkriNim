package ru.dot.clipshot;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/** View-space-only overlay. Pixel colors are supplied in row-major top-to-bottom order. */
final class EyedropperLoupe {
    static final int GRID = 9;
    private final Look look;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pixelPaint = new Paint();
    private final Path disk = new Path();

    EyedropperLoupe(Look look, float density) {
        this.look = look;
        this.density = density;
    }

    void draw(Canvas canvas, float x, float y, int viewWidth, int viewHeight, int[] pixels) {
        if (pixels == null || pixels.length < GRID * GRID || viewWidth <= 0 || viewHeight <= 0) return;
        float margin = 8f * density;
        float radius = Math.min(46f * density,
                (Math.min(viewWidth, viewHeight) - 2f * margin) / 2f);
        if (radius < 4f * density || radius <= 0) return;
        float minX = radius + margin, maxX = viewWidth - radius - margin;
        float minY = radius + margin, maxY = viewHeight - radius - margin;
        float cx = clamp(x, minX, maxX);
        float rise = radius + 30f * density;
        float above = y - rise, below = y + rise;
        // Use the unobstructed side. If neither fits completely, use the roomier side.
        boolean useAbove = above >= minY || (below > maxY && y - minY >= maxY - y);
        float cy = clamp(useAbove ? above : below, minY, maxY);
        int selected = pixels[(GRID * GRID) / 2];

        drawConnector(canvas, x, y, cx, cy, radius);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x44000000);
        canvas.drawCircle(cx, cy + 2f * density, radius + 2f * density, paint);

        int save = canvas.save();
        disk.rewind();
        disk.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.clipPath(disk);
        float cell = radius * 2f / GRID;
        // Snap cell edges to device pixels so adjacent texels remain crisp.
        for (int row = 0; row < GRID; ++row) {
            float top = edge(cy - radius + row * cell);
            float bottom = edge(cy - radius + (row + 1) * cell);
            if (row == 0) top -= 1;
            if (row == GRID - 1) bottom += 1;
            for (int column = 0; column < GRID; ++column) {
                float left = edge(cx - radius + column * cell);
                float right = edge(cx - radius + (column + 1) * cell);
                if (column == 0) left -= 1;
                if (column == GRID - 1) right += 1;
                pixelPaint.setColor(0xff000000 | pixels[row * GRID + column]);
                canvas.drawRect(left, top, right, bottom, pixelPaint);
            }
        }
        paint.setStyle(Paint.Style.STROKE);
        float left = edge(cx - cell / 2f), top = edge(cy - cell / 2f);
        float right = edge(cx + cell / 2f), bottom = edge(cy + cell / 2f);
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(3f * density);
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(1.25f * density);
        canvas.drawRect(left, top, right, bottom, paint);
        canvas.restoreToCount(save);
        paint.setColor(look.frame);
        paint.setStrokeWidth(2f * density);
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff000000 | selected);
        canvas.drawCircle(x, y, 5f * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(look.frame);
        paint.setStrokeWidth(2f * density);
        canvas.drawCircle(x, y, 5f * density, paint);
    }

    private void drawConnector(Canvas canvas, float x, float y, float cx, float cy, float r) {
        float vx = x - cx, vy = y - cy;
        float length2 = vx * vx + vy * vy;
        if (length2 <= r * r) return; // No circle tangents from a point inside it.
        float along = r * r / length2;
        float side = (float) (r * Math.sqrt(length2 - r * r) / length2);
        float ax = cx + along * vx - side * vy, ay = cy + along * vy + side * vx;
        float bx = cx + along * vx + side * vy, by = cy + along * vy - side * vx;
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor((look.frame & 0xffffff) | 0xa8000000);
        paint.setStrokeWidth(1.25f * density);
        canvas.drawLine(ax, ay, x, y, paint);
        canvas.drawLine(bx, by, x, y, paint);
    }

    private static float edge(float value) { return Math.round(value); }
    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(value, high));
    }
}
