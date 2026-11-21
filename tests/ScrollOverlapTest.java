package ru.dot.clipshot;

import java.util.Arrays;

public final class ScrollOverlapTest {
    private static final int HEIGHT = 2400, SHIFT = 348;

    public static void main(String[] args) {
        for (boolean upward : new boolean[]{false, true}) {
            int from = upward ? SHIFT : 0;
            int to = upward ? 0 : SHIFT;
            ScrollOverlap.Match match = ScrollOverlap.find(lines(from, upward, false), lines(to, upward, false), 0);
            if (match == null || match.shift != SHIFT)
                throw new AssertionError("missed thin, widely spaced rows, upward=" + upward);
            match = ScrollOverlap.find(lines(from, upward, false), lines(to, upward, false), 0, SHIFT + 27);
            if (match == null || match.shift != SHIFT)
                throw new AssertionError("weak telemetry overrode unique visual match");
            match = ScrollOverlap.find(lines(from, upward, true), lines(to, upward, true), 0);
            if (match != null)
                throw new AssertionError("accepted a repeating pattern, upward=" + upward);
            if (!lines(from, upward, false).needsShortSwipe())
                throw new AssertionError("overshot sparse rows");
            int[] dense = new int[HEIGHT * ScrollOverlap.COLUMNS];
            for (int i = 0; i < dense.length; i++) dense[i] = i * 0x1f230b;
            if (new ScrollOverlap.Frame(dense, upward, null).needsShortSwipe())
                throw new AssertionError("shortened a swipe on a detailed page");
            measuredRepeats(upward);
        }
        System.out.println("ScrollOverlapTest passed");
    }

    private static void measuredRepeats(boolean upward) {
        for (int shift : new int[]{348, 360}) {
            // 360 is four complete row periods: the before/after viewport looks identical.
            int from = upward ? shift : 0, to = upward ? 0 : shift;
            ScrollOverlap.Frame before = webList(from, upward, false, false);
            ScrollOverlap.Frame after = webList(to, upward, false, false);
            ScrollOverlap.Match m = ScrollOverlap.find(before, after, 0, shift);
            if (m == null || m.shift != shift) throw new AssertionError("missed measured repeating rows");
            int header = upward ? 124 : 210, footer = HEIGHT - (upward ? 210 : 124);
            if (m.seam < header || m.seam + shift >= footer)
                throw new AssertionError("seam crossed fixed toolbar");
            if (m.headerEnd < header - 1 || m.footerStart > footer + 1)
                throw new AssertionError("fixed toolbar counted as moving content");

            // A narrow animated panel repainted while the document rerasterized
            // at a fractional pixel position and Stop covered part of the page.
            after = webList(to, upward, true, false);
            m = ScrollOverlap.find(before, after, 0, shift);
            if (m == null || m.shift != shift) throw new AssertionError("repaint hid measured motion");
            if (upward ? m.seam < after.mask.end : m.seam >= after.mask.start)
                throw new AssertionError("kept a row painted by Stop at a measured seam");
            m = ScrollOverlap.find(before, after, 0, shift + 13);
            if (m != null && m.shift == shift + 13) throw new AssertionError("trusted stale telemetry");
        }
        ScrollOverlap.Frame toolbar = webList(0, upward, false, true);
        if (ScrollOverlap.find(toolbar, toolbar, 0, 90) != null)
            throw new AssertionError("accepted header and empty body as positive displacement");
    }

    private static ScrollOverlap.Frame webList(int offset, boolean upward, boolean repaint, boolean blank) {
        int width = ScrollOverlap.COLUMNS;
        int[] pixels = new int[HEIGHT * width];
        int stopTop = 1660, stopBottom = 1860;
        ScrollOverlap.Mask mask = repaint ? new ScrollOverlap.Mask(
                upward ? HEIGHT - stopBottom : stopTop, upward ? HEIGHT - stopTop : stopBottom, 0xc0) : null;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < width; x++) {
                int row = (y + offset) % 90;
                int color = 0xffeeeeee;
                if (y < 210 || y >= HEIGHT - 124) {
                    color = 0xff000000 | (y * 193 + x * 11039) & 0xffffff;
                } else if (!blank && row % 17 < 11 && x % 7 < (row % 4) + 2) {
                    color = 0xff000000 | (row * 19203 + x * 84017) & 0xffffff;
                }
                if (repaint && y >= 500 && y < 870 && x >= 48 && x < 56)
                    color = 0xff000000 | (y * 3947 + x * 1737) & 0xffffff;
                if (repaint && y >= stopTop && y < stopBottom && x >= 48) color = 0xff880044;
                pixels[y * width + x] = color;
            }
        }
        if (repaint) {
            for (int y = HEIGHT - 125; y > 210; y--) for (int x = 0; x < width; x++) {
                int i = y * width + x, color = 0xff000000;
                for (int channel = 0; channel < 24; channel += 8) {
                    int current = (pixels[i] >> channel) & 255;
                    int previous = (pixels[i - width] >> channel) & 255;
                    color |= ((7 * current + previous) / 8) << channel;
                }
                pixels[i] = color;
            }
        }
        return new ScrollOverlap.Frame(pixels, upward, mask);
    }

    // Four-pixel rules on a light background, with fixed padding at the edges.
    // A six-pixel sampling grid can land entirely on the background.
    private static ScrollOverlap.Frame lines(int offset, boolean upward, boolean repeat) {
        int columns = ScrollOverlap.COLUMNS;
        int[] pixels = new int[HEIGHT * columns];
        Arrays.fill(pixels, 0xffeeeeee);
        for (int y = 62; y < HEIGHT - 74; y++) {
            int position = y + offset;
            int stroke = position % 90;
            if (stroke < 1 || stroke > 4) continue;
            int line = repeat ? 0 : position / 90;
            int length = 18 + line % 32;
            for (int x = 2; x < length; x++) {
                if (x % 4 == 0) continue;
                pixels[y * columns + x] = 0xff000000 | (stroke * 32 << 16) | (line * 8 << 8);
            }
        }
        return new ScrollOverlap.Frame(pixels, upward, null);
    }
}
