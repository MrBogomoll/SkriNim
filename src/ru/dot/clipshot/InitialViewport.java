package ru.dot.clipshot;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Detect a common vertical displacement before opening the editor during an existing fling. */
final class InitialViewport {
    private static final int COLUMNS = 12;
    private final int height;
    private final int[] rows;

    InitialViewport(ByteBuffer rgba, int w, int h, int stride) {
        // Omit the app toolbar, system bars and the scrollbar.
        height = h - 2 * (h / 7);
        rows = new int[height];
        ByteBuffer bytes = rgba.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < COLUMNS; i++) {
            int x = Math.min(w - 1, (int)(w * (.08 + .84 * (i + .5) / COLUMNS)));
            int pos = bytes.position() + (h / 7) * stride + x * 4;
            for (int y = 0; y < height; y++, pos += stride)
                rows[y] = 31 * rows[y] + (bytes.getInt(pos) & 0x00f8f8f8);
        }
    }

    boolean scrolledFrom(InitialViewport previous) {
        if (height != previous.height) return false;
        // Compare distinct horizontal edges. Plain space and static toolbars
        // cannot establish that the source page moved.
        int[] edges = new int[96];
        int count = 0, bandSize = Math.max(1, height / edges.length);
        for (int start = 1; start < height && count < edges.length; start += bandSize) {
            int end = Math.min(height, start + bandSize);
            for (int y = start; y < end; y++) {
                if (rows[y] != rows[y - 1] && rows[y] != previous.rows[y]) {
                    edges[count++] = y;
                    break;
                }
            }
        }
        if (count < 5) return false;
        for (int distance = 1; distance <= height / 2; distance++) {
            if (follows(previous, edges, count, distance)
                    || follows(previous, edges, count, -distance)) return true;
        }
        return false;
    }

    private boolean follows(InitialViewport before, int[] edges, int count, int shift) {
        int support = 0, compared = 0, first = height, last = 0;
        for (int i = 0; i < count; i++) {
            int y = edges[i], old = y + shift;
            if (old < 1 || old >= height) continue;
            compared++;
            if (rows[y] == before.rows[old] && rows[y - 1] == before.rows[old - 1]) {
                support++;
                first = Math.min(first, y);
                last = y;
            }
        }
        return support >= 5 && support * 4 >= compared * 3 && last - first >= height / 12;
    }
}
