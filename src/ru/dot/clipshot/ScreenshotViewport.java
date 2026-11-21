package ru.dot.clipshot;

import android.graphics.Bitmap;

/** Checks that returning from the editor revealed the original page before scrolling it. */
final class ScreenshotViewport {
    private static final int ACROSS = 64, DOWN = 96;
    private static final int TILE_COLS = 4, TILE_ROWS = 8;

    /** Also accepts an unchanged blank page, only after the source is independently known. */
    static boolean settled(Bitmap earlier, Bitmap later) {
        if (matches(earlier, later)) return true;
        if (earlier == null || later == null || earlier.isRecycled() || later.isRecycled()) return false;
        int w = earlier.getWidth(), h = earlier.getHeight();
        if (w != later.getWidth() || h != later.getHeight()) return false;
        int[] a = new int[w], b = new int[w];
        for (int row = 0; row < DOWN; row++) {
            int y = h/10 + (int)((row+.5) * (h*.80/DOWN));
            earlier.getPixels(a, 0, w, 0, y, w, 1);
            later.getPixels(b, 0, w, 0, y, w, 1);
            for (int col = 0; col < ACROSS; col++) {
                int x = w/20 + (int)((col+.5) * (w*.90/ACROSS));
                if (distance(a[x], b[x]) > 8) return false;
            }
        }
        return true;
    }

    static boolean matches(Bitmap original, Bitmap live) {
        if (original == null || live == null || original.isRecycled() || live.isRecycled()) return false;
        int w = original.getWidth(), h = original.getHeight();
        if (w != live.getWidth() || h != live.getHeight() || w < ACROSS || h < DOWN) return false;

        int[] oldRow = new int[w], liveRow = new int[w];
        Tile[] tiles = new Tile[TILE_ROWS * TILE_COLS];
        for (int i = 0; i < tiles.length; i++) tiles[i] = new Tile();
        int radius = Math.max(1, w / 250);
        int matchingPixels = 0;
        for (int row = 0; row < DOWN; row++) {
            // Omit the system bars and most app toolbars: unrelated pages often share them.
            int y = h / 8 + (int) ((row + .5) * (h * .76 / DOWN));
            original.getPixels(oldRow, 0, w, 0, y, w, 1);
            live.getPixels(liveRow, 0, w, 0, y, w, 1);
            double phase = ((row * 13) % 17 + .5) / 17;
            for (int col = 0; col < ACROSS; col++) {
                int x = w / 20 + (int) ((col + phase) * (w * .90 / ACROSS));
                boolean center = closeColor(oldRow[x], liveRow[x]);
                if (center) matchingPixels++;
                if (!hasDetail(oldRow, x, radius) && !hasDetail(liveRow, x, radius)) continue;
                Tile tile = tiles[(row * TILE_ROWS / DOWN) * TILE_COLS + col * TILE_COLS / ACROSS];
                tile.detail++;
                if (center && closeColor(oldRow[x-radius], liveRow[x-radius])
                        && closeColor(oldRow[x+radius], liveRow[x+radius])) {
                    tile.matched++;
                    tile.columns |= 1 << (col % (ACROSS / TILE_COLS));
                    tile.rows |= 1 << (row % (DOWN / TILE_ROWS));
                }
            }
        }

        int detailed = 0, stable = 0, bands = 0, columns = 0;
        for (int i = 0; i < tiles.length; i++) {
            Tile tile = tiles[i];
            if (tile.detail < 5) continue;
            detailed++;
            // A point, vertical divider, or flat shared background cannot establish page identity.
            if (tile.matched * 100 < tile.detail * 76 || Integer.bitCount(tile.columns) < 3
                    || Integer.bitCount(tile.rows) < 2) continue;
            stable++;
            bands |= 1 << (i / TILE_COLS);
            columns |= 1 << (i % TILE_COLS);
        }
        if (matchingPixels * 100 >= DOWN * ACROSS * 86 && stable >= 5 && stable * 4 >= detailed * 3
                && Integer.bitCount(bands) >= 3 && Integer.bitCount(columns) >= 2) return true;
        if (matchingPixels * 100 >= DOWN * ACROSS * 70 && pooledMatch(original, live, oldRow, liveRow))
            return true;
        return matchesPageDetails(original, live, oldRow, liveRow);
    }

    private static final int DETAIL_ROWS = 144, DETAIL_COLS = 192;

    // Short text can occupy just one tile. A video or ad can change a whole
    // tile row. Look for the same glyph edges at the same coordinates, and
    // require agreement outside any single band of changing content.
    private static boolean matchesPageDetails(Bitmap original, Bitmap live, int[] a, int[] b) {
        int w = original.getWidth(), h = original.getHeight(), radius = Math.max(1, w/350);
        int[] previousA = new int[w], previousB = new int[w];
        DetailRow[] rows = new DetailRow[DETAIL_ROWS];
        int changedTop = DETAIL_ROWS, changedBottom = -1;
        GradientMatch horizontal = new GradientMatch(), vertical = new GradientMatch();
        int different = 0, edges = 0, landmarks = 0, lines = 0, left = DETAIL_COLS, right = -1;
        for (int row = 0; row < DETAIL_ROWS; row++) {
            int y = h/8 + (int)((row + .5) * (h*.76/DETAIL_ROWS));
            original.getPixels(a, 0, w, 0, y, w, 1);
            live.getPixels(b, 0, w, 0, y, w, 1);
            DetailRow r = rows[row] = new DetailRow();
            int rowLandmarks = 0;
            for (int col = 0; col < DETAIL_COLS; col++) {
                int x = w/20 + (int)((col + .5) * (w*.90/DETAIL_COLS));
                boolean same = closeColor(a[x], b[x]);
                if (!same) r.different++;
                if (row > 0) {
                    int dxA = luma(a[x+radius]) - luma(a[x-radius]);
                    int dxB = luma(b[x+radius]) - luma(b[x-radius]);
                    int dyA = luma(a[x]) - luma(previousA[x]);
                    int dyB = luma(b[x]) - luma(previousB[x]);
                    horizontal.add(dxA, dxB);
                    vertical.add(dyA, dyB);
                    if (sameEdge(dxA, dxB) && sameEdge(dyA, dyB)) {
                        rowLandmarks++;
                        left = Math.min(left, col);
                        right = Math.max(right, col);
                    }
                }
                if (!hasDetail(a,x,radius) && !hasDetail(b,x,radius)) continue;
                r.edges++;
                if (!same || !closeColor(a[x-radius], b[x-radius]) || !closeColor(a[x+radius], b[x+radius]))
                    continue;
                r.matched++;
                // A vertical bar can contribute many identical horizontal edges.
                // Require contrast in both dimensions to count a landmark.
                if (row > 0 && distance(a[x], previousA[x]) > 35 && distance(b[x], previousB[x]) > 35
                        && closeColor(previousA[x], previousB[x])) {
                    r.corners++;
                    r.left = Math.min(r.left, col);
                    r.right = Math.max(r.right, col);
                }
            }
            if (r.different > DETAIL_COLS/16) {
                changedTop = Math.min(changedTop, row);
                changedBottom = row;
            }
            different += r.different;
            edges += r.edges;
            landmarks += rowLandmarks;
            if (rowLandmarks >= 2) lines++;
            int[] swap = previousA; previousA = a; a = swap;
            swap = previousB; previousB = b; b = swap;
        }
        // A fading Quick Settings shade reduces the contrast of antialiased
        // glyphs. A dot product of signed gradients tests their shape and exact
        // position independently of contrast. Use it only on mostly flat pages
        // with two-dimensional landmarks in both captures.
        int area = DETAIL_ROWS * DETAIL_COLS;
        if (different < area/50 && edges < area/16 && landmarks >= 18 && lines >= 3
                && right-left >= DETAIL_COLS/12 && horizontal.matches() && vertical.matches()) return true;
        if (supportedRows(rows, DETAIL_ROWS, -1)) return true;
        return changedBottom >= changedTop && changedBottom - changedTop + 1 <= DETAIL_ROWS/2
                && supportedRows(rows, changedTop, changedBottom);
    }

    private static final class DetailRow {
        int different, edges, matched, corners, left = DETAIL_COLS, right = -1;
    }

    private static final class GradientMatch {
        long dot, energyA, energyB;
        void add(int a, int b) {
            dot += a*b;
            energyA += a*a;
            energyB += b*b;
        }
        boolean matches() {
            return dot > 0 && (double)dot * dot >= .95 * .95 * energyA * energyB;
        }
    }

    private static int luma(int color) {
        return (77*(color >> 16 & 255) + 150*(color >> 8 & 255) + 29*(color & 255)) >> 8;
    }

    private static boolean sameEdge(int a, int b) {
        return (a > 25 && b > 25) || (a < -25 && b < -25);
    }

    private static boolean supportedRows(DetailRow[] rows, int excludeFrom, int excludeTo) {
        int different = 0, edges = 0, matched = 0, corners = 0, left = DETAIL_COLS, right = -1;
        int lines = 0, bands = 0;
        for (int y=0; y<rows.length; y++) {
            if (y >= excludeFrom && y <= excludeTo) continue;
            DetailRow r = rows[y];
            different += r.different;
            edges += r.edges;
            matched += r.matched;
            corners += r.corners;
            if (r.corners < 2) continue;
            lines++;
            left = Math.min(left, r.left);
            right = Math.max(right, r.right);
            bands |= 1 << (y * 8 / DETAIL_ROWS);
        }
        boolean partial = excludeTo >= excludeFrom;
        int area = DETAIL_COLS * (partial ? DETAIL_ROWS - (excludeTo-excludeFrom+1) : DETAIL_ROWS);
        if (different * 100 > area * 2 || matched * 100 < edges * 97) return false;
        if (partial) {
            // Matching chrome at just the top and bottom of a changing page
            // is insufficient. Require detailed content across the retained body.
            return Integer.bitCount(bands & 0x7e) >= 3 && corners >= 64
                    && lines >= 10 && right-left >= DETAIL_COLS/5;
        }
        return corners >= 16 && lines >= 3 && right-left >= DETAIL_COLS/12;
    }

    // The tail of the Quick Settings transition can soften text in the original.
    // Compare local color averages while requiring distinctive, two-dimensional
    // structure at several heights. A white splash or a shared divider has none.
    private static boolean pooledMatch(Bitmap original, Bitmap live, int[] oldRow, int[] liveRow) {
        int cols = 40, rows = 64;
        int[] a = pooledColors(original, oldRow, cols, rows);
        int[] b = pooledColors(live, liveRow, cols, rows);
        int evidence = 0, supported = 0, bands = 0, distinct = 0;
        for (int y = 1; y < rows-1; y++) for (int x = 1; x < cols-1; x++) {
            int i = y*cols+x;
            int detail = Math.max(corner(a, i, cols), corner(b, i, cols));
            if (detail < 28) continue;
            evidence++;
            int error = distance(a[i], b[i]);
            if (error > Math.max(10, detail / 5)) continue;
            supported++;
            bands |= 1 << (y * 8 / rows);
            if (detail >= 55) distinct++;
        }
        return supported >= 10 && distinct >= 5 && supported * 100 >= evidence * 87
                && Integer.bitCount(bands) >= 3;
    }

    private static int[] pooledColors(Bitmap bitmap, int[] row, int cols, int rows) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] result = new int[cols*rows];
        // Four subsamples on each axis inside a cell. Summing separate channels
        // avoids overflow and gives the blur comparison a fixed cost on large displays.
        int[] red = new int[cols], green = new int[cols], blue = new int[cols];
        for (int y=0; y<rows; y++) {
            java.util.Arrays.fill(red, 0);
            java.util.Arrays.fill(green, 0);
            java.util.Arrays.fill(blue, 0);
            for (int j=0; j<4; j++) {
                int sy = h/8 + (int)((y + (j+.5)/4) * (h*.76/rows));
                bitmap.getPixels(row, 0, w, 0, sy, w, 1);
                for (int x=0; x<cols; x++) for (int k=0; k<4; k++) {
                    int sx = w/20 + (int)((x + (k+.5)/4) * (w*.90/cols));
                    int c = row[sx];
                    red[x] += c >> 16 & 255;
                    green[x] += c >> 8 & 255;
                    blue[x] += c & 255;
                }
            }
            for (int x=0; x<cols; x++)
                result[y*cols+x] = (red[x]/16 << 16) | (green[x]/16 << 8) | blue[x]/16;
        }
        return result;
    }

    private static int corner(int[] colors, int i, int cols) {
        int c = colors[i];
        int across = Math.max(distance(c, colors[i-1]), distance(c, colors[i+1]));
        int down = Math.max(distance(c, colors[i-cols]), distance(c, colors[i+cols]));
        return Math.min(across, down);
    }

    private static int distance(int a, int b) {
        int r = Math.abs((a >> 16 & 255) - (b >> 16 & 255));
        int g = Math.abs((a >> 8 & 255) - (b >> 8 & 255));
        return Math.max(Math.max(r,g), Math.abs((a & 255) - (b & 255)));
    }

    private static final class Tile {
        int detail, matched, columns, rows;
    }

    private static boolean hasDetail(int[] row, int x, int radius) {
        return !closeColor(row[x], row[x-radius]) || !closeColor(row[x], row[x+radius]);
    }

    private static boolean closeColor(int a, int b) {
        return distance(a, b) <= 20;
    }
}
