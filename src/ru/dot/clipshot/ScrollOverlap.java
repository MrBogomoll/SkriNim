package ru.dot.clipshot;

/** Finds forward motion along the capture direction using sampled rows of consecutive screenshots. */
final class ScrollOverlap {
    static final int COLUMNS = 64;
    private static final int GROUPS = 8;
    private static final int GROUP_WIDTH = COLUMNS / GROUPS;

    static final class Mask {
        // Logical rows [start, end) and the groups of sample columns touched by the overlay.
        final int start, end, groups;
        Mask(int start, int end, int groups) {
            this.start = start;
            this.end = end;
            this.groups = groups;
        }
    }

    static final class Frame {
        final int height;
        final int[] hashes;
        final int[] texture;
        final byte[] smoothed;
        final Mask mask;
        final boolean upward;

        Frame(int[] pixels, boolean upward, Mask mask) {
            this.upward = upward;
            this.mask = mask;
            height = pixels.length / COLUMNS;
            hashes = new int[height * GROUPS];
            texture = new int[height];
            smoothed = new byte[pixels.length];
            for (int y = 0; y < height; y++) {
                int sourceY = upward ? height - 1 - y : y;
                for (int group = 0; group < GROUPS; group++) {
                    int start = sourceY * COLUMNS + group * GROUP_WIDTH;
                    int first = pixels[start] & 0x00f8f8f8;
                    int hash = 1;
                    boolean varied = false;
                    for (int x = start; x < start + GROUP_WIDTH; x++) {
                        int color = pixels[x] & 0x00f8f8f8;
                        hash = 31 * hash + color;
                        varied |= color != first;
                        int above = sourceY > 0 ? x - COLUMNS : x;
                        int below = sourceY + 1 < height ? x + COLUMNS : x;
                        int logicalIndex = (y - sourceY) * COLUMNS + x;
                        smoothed[logicalIndex] = (byte) ((luma(pixels[above]) + luma(pixels[x])
                                + luma(pixels[below])) / 3);
                    }
                    int index = y * GROUPS + group;
                    hashes[index] = hash;
                    if (varied || (y > 0 && hash != hashes[index - GROUPS]))
                        texture[y] |= 1 << group;
                }
            }
        }

        int excluded(int y) {
            // Include the neighbours used for fractional-pixel smoothing.
            return mask != null && y >= mask.start - 1 && y <= mask.end ? mask.groups : 0;
        }

        // A short block of text can leave the overlap entirely on a normal
        // swipe. Count vertical detail, so a fixed side border alone contributes none.
        boolean needsShortSwipe() {
            int top = Math.max(1, height / 8), bottom = height - top;
            int detailed = 0;
            for (int y = top; y < bottom; y++) {
                int hidden = excluded(y) | excluded(y - 1);
                for (int group = 0; group < GROUPS; group++) {
                    int i = y * GROUPS + group;
                    if ((hidden & (1 << group)) == 0 && hashes[i] != hashes[i - GROUPS]) {
                        detailed++;
                        break;
                    }
                }
            }
            return detailed * 100 < (bottom - top) * 18;
        }
    }

    static int groupAt(int column) { return 1 << (column / GROUP_WIDTH); }

    private static int luma(int color) {
        return ((color >> 16 & 255) * 77 + (color >> 8 & 255) * 150 + (color & 255) * 29) >> 8;
    }

    static final class Match {
        final int shift, seam, headerEnd, footerStart;
        Match(int shift, int seam, int headerEnd, int footerStart) {
            this.shift = shift;
            this.seam = seam;
            this.headerEnd = headerEnd;
            this.footerStart = footerStart;
        }
    }

    // A zero shift means that no meaningful movement was visible. Null means
    // that there was movement, but there is no unique, supported alignment.
    static Match find(Frame before, Frame after, int previousStart) {
        return find(before, after, previousStart, 0);
    }

    static Match find(Frame before, Frame after, int previousStart, int expectedShift) {
        int height = before.height;
        if (after.height != height || before.upward != after.upward || height < 16) return null;
        int[] changed = new int[height];
        int[] changedRows = new int[height];
        int activeRows = 0, count = 0, footerStart = 0;
        for (int y = 0; y < height; y++) {
            for (int g = 0; g < GROUPS; g++) {
                int i = y * GROUPS + g;
                if (before.hashes[i] != after.hashes[i]) changed[y] |= 1 << g;
            }
            changed[y] &= ~(before.excluded(y) | after.excluded(y));
            if (changed[y] != 0) {
                changedRows[count++] = y;
                footerStart = y + 1;
                if (y >= height / 32 && y < height - height / 32) activeRows++;
            }
        }
        boolean measured = expectedShift > 0 && expectedShift <= height * 4 / 5;
        if (activeRows < 3) {
            if (expectedShift == 0) return new Match(0, 0, 0, height);
            return measured ? measuredMatch(before, after, previousStart, expectedShift) : null;
        }

        boolean approximate = false;
        int best = findShift(before, after, changed, changedRows, count, false);
        if (best == 0) {
            approximate = true;
            best = findShift(before, after, changed, changedRows, count, true);
        }
        if (best <= 0) {
            return measured ? measuredMatch(before, after, previousStart, expectedShift) : null;
        }

        boolean[] seams = new boolean[height - best];
        for (int y = 0; y < seams.length; y++)
            seams[y] = seamCandidate(before, after, changed, y, best, approximate);
        return chooseSeam(before, after, previousStart, best, changedRows[0], footerStart, seams);
    }

    // A repeating pattern can have several perfect visual alignments, including
    // a whole-period shift that looks stationary. Check the offset reported by
    // the scrolling view against pairs of actual vertical edges at that offset.
    private static Match measuredMatch(Frame before, Frame after, int previousStart, int shift) {
        int height = before.height;
        boolean[] seams = new boolean[height - shift];
        int first = height, last = -1, centralFirst = height, centralLast = -1;
        int comparisons = 0, matches = 0, coverage = 0, bands = 0;
        int margin = Math.max(1, height / 10);
        for (int y = 1; y < seams.length; y++) {
            int other = y + shift;
            int hidden = after.excluded(y) | after.excluded(y - 1)
                    | before.excluded(other) | before.excluded(other - 1);
            int edges = 0, aligned = 0;
            for (int g = 0; g < GROUPS; g++) {
                if ((hidden & (1 << g)) != 0) continue;
                int a = y * GROUPS + g, b = other * GROUPS + g;
                boolean afterEdge = after.hashes[a] != after.hashes[a - GROUPS];
                boolean beforeEdge = before.hashes[b] != before.hashes[b - GROUPS];
                if (!afterEdge && !beforeEdge) continue;
                edges++;
                if (afterEdge && beforeEdge && sameEdge(after, a, before, b))
                    aligned |= 1 << g;
            }
            int hits = Integer.bitCount(aligned);
            seams[y] = hits > 0 && hits * 4 >= edges * 3;
            if (seams[y]) {
                first = Math.min(first, y);
                last = y;
            }
            if (y < margin || other >= height - margin) continue;
            comparisons += edges;
            matches += hits;
            if (seams[y]) {
                centralFirst = Math.min(centralFirst, y);
                centralLast = y;
                coverage |= aligned;
                bands |= 1 << (y * 12 / height);
            }
        }
        // A toolbar, a flat background or one shared icon cannot verify a shift.
        if (matches < 24 || matches * 100 < comparisons * 78
                || Integer.bitCount(coverage) < 2 || Integer.bitCount(bands) < 3
                || centralLast - centralFirst < height / 5) return null;
        return chooseSeam(before, after, previousStart, shift, first, last + shift + 1, seams);
    }

    // WebView can rerasterize at a fractional scroll position. For an
    // approximate edge, verify both neighbouring rows and the direction of
    // vertical contrast at corresponding sample columns.
    private static boolean sameEdge(Frame a, int ai, Frame b, int bi) {
        if (a.hashes[ai] == b.hashes[bi] && a.hashes[ai - GROUPS] == b.hashes[bi - GROUPS]) return true;
        if (!similar(a, ai, b, bi) || !similar(a, ai - GROUPS, b, bi - GROUPS)) return false;
        for (int k = 0; k < GROUP_WIDTH; k++) {
            int x = ai * GROUP_WIDTH + k, y = bi * GROUP_WIDTH + k;
            int da = (a.smoothed[x] & 255) - (a.smoothed[x - COLUMNS] & 255);
            int db = (b.smoothed[y] & 255) - (b.smoothed[y - COLUMNS] & 255);
            if ((da >= 12 && db >= 12) || (da <= -12 && db <= -12)) return true;
        }
        return false;
    }

    private static Match chooseSeam(Frame before, Frame after, int previousStart, int best,
                                    int headerEnd, int footerStart, boolean[] seams) {
        int height = before.height;
        // Pick a seam inside the verified moving region, clear of the overlay.
        int low = Math.max(0, previousStart - best);
        int high = height - best - 1;
        if (after.upward && after.mask != null) {
            // The incoming tail is retained from the seam onward.
            low = Math.max(low, after.mask.end);
        } else if (!after.upward) {
            // Already retained strips must never include rows touched by Stop.
            if (before.mask != null) high = Math.min(high, before.mask.start - best);
            if (after.mask != null) high = Math.min(high, after.mask.start);
        }
        int first = height, last = -1;
        for (int y = low; y <= high; y++) {
            if (seams[y]) {
                first = Math.min(first, y);
                last = y;
            }
        }
        if (last < first) return null;
        int middle = (first + last) / 2;
        for (int radius = 0; radius <= last - first; radius++) {
            int lower = middle - radius, upper = middle + radius;
            if (lower >= first && seams[lower]) return new Match(best, lower, headerEnd, footerStart);
            if (upper <= last && seams[upper]) return new Match(best, upper, headerEnd, footerStart);
        }
        return null;
    }

    /** Positive for a unique shift, zero for weak evidence, negative for ambiguity. */
    private static int findShift(Frame before, Frame after, int[] changed, int[] changedRows,
                                 int count, boolean approximate) {
        int height = before.height;
        int maxShift = height * 4 / 5;
        float[] scores = new float[maxShift + 1];
        // Sampling every Nth display row can miss all the thin strokes on a
        // sparse page. Spend the comparison budget on rows that changed.
        int step = Math.max(1, count / 360);
        int best = 0;
        for (int d = 1; d <= maxShift; d++) {
            int hits = 0, comparisons = 0, first = height, last = -1;
            for (int i = 0; i < count; i += step) {
                int y = changedRows[i];
                if (y + d >= height) break;
                int groups = informative(before, after, changed, y, d);
                if (groups == 0) continue;
                int matched = matchingGroups(before, after, y, d, groups, approximate);
                comparisons += Integer.bitCount(groups);
                if (matched != 0) {
                    hits += matched;
                    first = Math.min(first, y);
                    last = y;
                }
            }
            if (hits < 12 || last - first < Math.max(5, height / 40)) continue;
            // A tiny matching feature should not beat a well-supported match.
            scores[d] = (float) hits / (comparisons + 3);
            if (scores[d] > scores[best]) best = d;
        }
        if (scores[best] < .78f) return 0;
        for (int d = 1; d <= maxShift; d++) {
            // Rasterized edges can make a neighbouring pixel nearly as good.
            if (Math.abs(d - best) > 2 && scores[d] >= scores[best] - .075f) return -1;
        }

        return best;
    }

    private static int informative(Frame before, Frame after, int[] changed, int y, int d) {
        // Static bars and flat background cannot provide evidence of motion.
        return changed[y] & changed[y + d] & (after.texture[y] | before.texture[y + d]);
    }

    private static boolean seamCandidate(Frame before, Frame after, int[] changed, int y,
                                         int d, boolean approximate) {
        int groups = informative(before, after, changed, y, d);
        return groups != 0 && matchingGroups(before, after, y, d, groups, approximate) * 4
                >= Integer.bitCount(groups) * 3;
    }

    private static int matchingGroups(Frame before, Frame after, int y, int d, int groups, boolean approximate) {
        int count = 0;
        while (groups != 0) {
            int g = Integer.numberOfTrailingZeros(groups);
            int a = y * GROUPS + g, b = (y + d) * GROUPS + g;
            if (after.hashes[a] == before.hashes[b] || (approximate && similar(after, a, before, b))) count++;
            groups &= groups - 1;
        }
        return count;
    }

    // A browser may rasterize a settled document at a fractional vertical offset.
    // A small error across a smoothed group tolerates that subpixel difference.
    private static boolean similar(Frame a, int ai, Frame b, int bi) {
        ai *= GROUP_WIDTH;
        bi *= GROUP_WIDTH;
        int difference = 0;
        for (int i = 0; i < GROUP_WIDTH; i++) {
            difference += Math.abs((a.smoothed[ai+i] & 255) - (b.smoothed[bi+i] & 255));
            if (difference > GROUP_WIDTH * 12) return false;
        }
        return true;
    }
}
