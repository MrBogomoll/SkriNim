package ru.dot.clipshot;

import android.graphics.Bitmap;
import java.util.HashSet;

/** Finds a verified vertical overlap with the already assembled document on editor re-entry. */
final class ScrollPosition {
    private static final int GROUPS = 8, COLS = 64;
    private final int height;
    private final int[] rows;
    private final boolean[] landmarks;

    private ScrollPosition(Bitmap image, boolean chooseLandmarks, int colorMask) {
        height = image.getHeight();
        int width = image.getWidth();
        int[] pixels = new int[height];
        rows = new int[height * GROUPS];
        landmarks = chooseLandmarks ? new boolean[rows.length] : null;
        for (int group = 0; group < GROUPS; group++) {
            int[] first = chooseLandmarks ? new int[height] : null;
            for (int col = 0; col < COLS / GROUPS; col++) {
                int n = group * (COLS / GROUPS) + col;
                int x = Math.min(width - 1, (int)((.04 + .92 * (n + .5) / COLS) * width));
                image.getPixels(pixels, 0, 1, x, 0, 1, height);
                for (int y = 0; y < height; y++) {
                    int i = y * GROUPS + group;
                    int pixel = pixels[y];
                    int color = colorMask != 0 ? pixel & colorMask
                            : ((pixel >> 16 & 255) + 2 * (pixel >> 8 & 255) + (pixel & 255)) / 512;
                    rows[i] = rows[i] * 31 + color;
                    if (chooseLandmarks) {
                        if (col == 0) first[y] = color;
                        else if (color != first[y]) landmarks[i] = true;
                    }
                }
            }
        }
    }

    static ScrollPage.Start find(Bitmap document, Bitmap live) {
        int h = live.getHeight(), w = live.getWidth();
        if (document.getWidth() != w || w < COLS || h < 96) return null;
        ScrollPage.Start strict = find(document, live, 0x00e0e0e0);
        // Fractional rendering in WebViews changes antialiasing along glyph
        // edges. A coarser fingerprint can locate the same strokes, while the
        // final color test still checks the actual pixels across the overlap.
        return strict != null ? strict : find(document, live, 0);
    }

    private static ScrollPage.Start find(Bitmap document, Bitmap live, int colorMask) {
        int h = live.getHeight();
        ScrollPosition current = new ScrollPosition(live, true, colorMask);
        int margin = h / 8;
        int[] candidates = new int[h * GROUPS];
        int count = 0, detailedRows = 0;
        // Fixed app and system bars contribute little to position. Horizontal
        // rules and solid strips alone cannot identify a block of page content.
        for (int y = margin; y < h - margin; y++) {
            int previousCount = count;
            for (int g = 0; g < GROUPS; g++) {
                int i = y * GROUPS + g;
                if (current.landmarks[i] && current.rows[i] != current.rows[i - GROUPS])
                    candidates[count++] = i;
            }
            if (count != previousCount) detailedRows++;
        }
        if (count < 12 || detailedRows < 5) return null;

        // Preserve thin lines by sampling informative rows, rather than walking
        // every Nth display row. Limit the search cost on full-resolution photos.
        int stride = Math.max(1, (count + 767) / 768);
        ScrollPosition saved = new ScrollPosition(document, false, colorMask);
        int outside = h * 2 / 3, high = saved.height - h + outside;
        float[] scores = new float[high + outside + 1];
        int best = -outside;
        for (int offset = -outside; offset <= high; offset++) {
            int hits = 0, compared = 0, first = h, last = -1;
            int delta = offset * GROUPS;
            int lower = (margin - offset) * GROUPS;
            int upper = (saved.height - margin - offset) * GROUPS;
            for (int k = 0; k < count; k += stride) {
                int i = candidates[k];
                if (i < lower || i >= upper) continue;
                compared++;
                if (current.rows[i] == saved.rows[i + delta]) {
                    hits++;
                    int y = i / GROUPS;
                    if (first == h) first = y;
                    last = y;
                }
            }
            if (hits < 12 || last - first < Math.max(8, h / 48)) continue;
            float score = (float)hits / (compared + 3);
            scores[offset + outside] = score;
            if (score > scores[best + outside]) best = offset;
        }
        float peak = scores[best + outside];
        if (peak < .78f) return null;
        for (int d = -outside; d <= high; d++)
            if (Math.abs(d - best) > 2 && scores[d + outside] >= peak - .065f) return null;

        HashSet<Integer> patterns = new HashSet<>();
        int first = h, last = -1, bestRow = -1;
        for (int k=0; k<count; k++) {
            int i = candidates[k], y = i / GROUPS;
            if (y + best < margin || y + best >= saved.height - margin
                    || current.rows[i] != saved.rows[i + best * GROUPS]) continue;
            if (patterns.size() < 6) patterns.add(current.rows[i]);
            if (first == h) first = y;
            last = y;
        }
        if (patterns.size() < 6 || !sameOverlapColors(document, live, best, margin, colorMask == 0)) return null;
        int mid = (first + last) / 2;
        for (int k=0; k<count; k++) {
            int i = candidates[k], y = i / GROUPS;
            if (y + best < margin || y + best >= saved.height - margin
                    || current.rows[i] != saved.rows[i + best * GROUPS]) continue;
            if (bestRow < 0 || Math.abs(y-mid) < Math.abs(bestRow-mid)) bestRow = y;
        }
        return bestRow < 0 ? null : new ScrollPage.Start(best, bestRow);
    }

    // A shared toolbar may contain every textured landmark on two very sparse
    // pages. Also verify the surrounding pixels throughout the proposed overlap.
    private static boolean sameOverlapColors(Bitmap document, Bitmap live, int offset, int margin, boolean subpixel) {
        int top = Math.max(margin, margin - offset);
        int bottom = Math.min(live.getHeight() - margin, document.getHeight() - margin - offset);
        if (bottom <= top) return false;
        int width = live.getWidth(), matches = 0, previousMatches = 0, nextMatches = 0, probes = 0;
        int[] oldRow = new int[width], nowRow = new int[width];
        int[] previous = subpixel ? new int[width] : null, next = subpixel ? new int[width] : null;
        for (int y=top; y<bottom; y+=Math.max(1, (bottom-top)/64)) {
            document.getPixels(oldRow, 0, width, 0, y+offset, width, 1);
            live.getPixels(nowRow, 0, width, 0, y, width, 1);
            if (subpixel) {
                document.getPixels(previous, 0, width, 0, y+offset-1, width, 1);
                document.getPixels(next, 0, width, 0, y+offset+1, width, 1);
            }
            for (int col=0; col<COLS; col++) {
                int x = (int)((.04 + .92*(col+.5)/COLS)*width);
                int a = oldRow[x], b = nowRow[x];
                if (near(a, b)) matches++;
                if (subpixel) {
                    if (near(blendQuarter(a, previous[x]), b)) previousMatches++;
                    if (near(blendQuarter(a, next[x]), b)) nextMatches++;
                }
                probes++;
            }
        }
        // Use one consistent phase across the entire viewport.
        return Math.max(matches, Math.max(previousMatches, nextMatches)) * 100 >= probes * 82;
    }

    private static int blendQuarter(int a, int b) {
        int rb = (((a & 0xff00ff) * 3 + (b & 0xff00ff)) >>> 2) & 0xff00ff;
        int green = (((a & 0xff00) * 3 + (b & 0xff00)) >>> 2) & 0xff00;
        return rb | green;
    }

    private static boolean near(int a, int b) {
        return Math.abs((a >> 16 & 255)-(b >> 16 & 255)) <= 30
                && Math.abs((a >> 8 & 255)-(b >> 8 & 255)) <= 30
                && Math.abs((a & 255)-(b & 255)) <= 30;
    }
}
