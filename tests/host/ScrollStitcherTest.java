package ru.dot.clipshot;

import android.graphics.Bitmap;
import android.graphics.Rect;

public final class ScrollStitcherTest {
    private static final int WIDTH = 180, HEIGHT = 400, HEADER = 64, FOOTER = 41;

    public static void main(String[] args) {
        ScrollStitcher s = new ScrollStitcher(8_000_000);
        Bitmap first = screen(0), second = screen(115), last = screen(122);
        expect(s.add(first), ScrollStitcher.Result.FIRST);
        expect(s.add(second), ScrollStitcher.Result.ADDED);
        expect(s.add(last), ScrollStitcher.Result.ADDED);
        Bitmap same = screen(122);
        expect(s.add(same), ScrollStitcher.Result.UNCHANGED);
        check(same.isRecycled(), "duplicate not released");
        Bitmap foreign = screen(3000);
        expect(s.add(foreign), ScrollStitcher.Result.NO_MATCH);
        check(foreign.isRecycled(), "unrelated frame not released");
        Bitmap out = s.finish();
        check(out.getHeight() == HEIGHT + 122, "wrong full height");
        verifyPage(out, 0, 122);
        check(first.isRecycled() && second.isRecycled() && last.isRecycled(), "retained screen");
        check(s.finish() == null, "finish transferred ownership twice");

        // On an undersized heap the last append should keep the part that fits.
        s = new ScrollStitcher(4L * WIDTH * (HEIGHT + 50));
        expect(s.add(screen(0)), ScrollStitcher.Result.FIRST);
        expect(s.add(screen(115)), ScrollStitcher.Result.LIMIT);
        out = s.finish();
        check(out.getHeight() == HEIGHT + 50, "capacity boundary");
        verifyPage(out, 0, 50);

        // If full assembly fails, preserve the prefix and stationary footer.
        s = new ScrollStitcher();
        first = screen(0);
        expect(s.add(first), ScrollStitcher.Result.FIRST);
        expect(s.add(screen(115)), ScrollStitcher.Result.ADDED);
        expect(s.add(screen(122)), ScrollStitcher.Result.ADDED);
        Bitmap.allocationHeightLimit = HEIGHT + 70;
        try {
            out = s.finish();
            check(s.shortenedOnFinish() && out.getHeight() == HEIGHT + 61, "shortened prefix");
            verifyPage(out, 0, 61);
        } finally {
            Bitmap.allocationHeightLimit = Integer.MAX_VALUE;
        }

        s = new ScrollStitcher();
        first = screen(0);
        expect(s.add(first), ScrollStitcher.Result.FIRST);
        expect(s.add(screen(115)), ScrollStitcher.Result.ADDED);
        Bitmap.allocationHeightLimit = HEIGHT;
        try {
            check(s.finish() == first && !first.isRecycled(), "low-memory single screen fallback");
            check(s.shortenedOnFinish(), "silent single-screen fallback");
        } finally {
            Bitmap.allocationHeightLimit = Integer.MAX_VALUE;
        }

        // Fractionally rasterized text moves by an integer plus a partial pixel.
        s = new ScrollStitcher();
        expect(s.add(screen(0)), ScrollStitcher.Result.FIRST);
        expect(s.add(subpixelScreen(115)), ScrollStitcher.Result.ADDED);
        check(s.finish().getHeight() == HEIGHT + 115, "fractional scroll distance");

        // A repeated tile pattern admits multiple equally plausible scroll distances.
        s = new ScrollStitcher();
        Bitmap before = repeated(0), after = repeated(11);
        expect(s.add(before), ScrollStitcher.Result.FIRST);
        expect(s.add(after), ScrollStitcher.Result.NO_MATCH);
        check(s.finish() == before, "the first screen should remain intact");

        s = new ScrollStitcher();
        before = screen(0);
        expect(s.add(before), ScrollStitcher.Result.FIRST);
        Bitmap rotated = Bitmap.createBitmap(HEIGHT, WIDTH, Bitmap.Config.ARGB_8888);
        expect(s.add(rotated), ScrollStitcher.Result.NO_MATCH);
        s.close();
        check(rotated.isRecycled() && before.isRecycled(), "cancel did not release images");
        upwardScreens();
        persistentStop();
        continueExistingPage();
        interruptedContinuation();
        relocateContinuation();
        rejectSharedChrome();
        System.out.println("ScrollStitcherTest passed");
    }

    private static ScrollPage extend(ScrollPage base, boolean up, int... offsets) {
        ScrollStitcher s = new ScrollStitcher(up, base);
        expect(s.add(screen(offsets[0])), ScrollStitcher.Result.FIRST);
        for (int i=1; i<offsets.length; i++) expect(s.add(screen(offsets[i])), ScrollStitcher.Result.ADDED);
        return s.finishPage();
    }

    private static void continueExistingPage() {
        ScrollPage original = new ScrollPage(screen(200));
        ScrollPage down = extend(original, false, 200, 312, 395);
        verifyPage(down.image, 200, 195);
        check(down.offset == 195 && !down.viewport.isRecycled(), "lost live bottom viewport");
        ScrollPage lower = extend(down, false, 395, 471);
        verifyPage(lower.image, 200, 271);
        check(lower.offset == 271, "incorrect continued bottom offset");
        ScrollPage retraced = extend(lower, true, 471, 389, 277);
        check(retraced.image == lower.image && retraced.offset == 77,
                "retracing existing rows must preserve the editor bitmap and update position");
        verifyPage(retraced.viewport, 277, 0);
        ScrollPage upper = extend(retraced, true, 277, 215, 150, 82);
        verifyPage(upper.image, 82, 389);
        check(upper.offset == 0, "incorrect top offset after reversing past the old top");
        ScrollPage whole = extend(upper, false, 82, 185, 268, 329, 419, 489, 545);
        verifyPage(whole.image, 82, 463);
        check(whole.offset == 463, "incorrect bottom after retracing the whole capture");
        ScrollPage still = extend(whole, false, 545);
        check(still == whole, "no-op replaced previous page and invalidated its source identity");
        ScrollPage returned = extend(whole, true, 545, 455);
        returned.discardComparedTo(whole);
        check(returned.viewport.isRecycled() && !whole.image.isRecycled() && !whole.viewport.isRecycled(),
                "discarded retrace invalidated editor image");
        whole.discardComparedTo(upper);
        check(whole.image.isRecycled() && whole.viewport.isRecycled() && !upper.image.isRecycled()
                && !upper.viewport.isRecycled(), "discarded result invalidated old preview");
    }

    private static void interruptedContinuation() {
        ScrollPage original = new ScrollPage(screen(200));
        ScrollPage down = extend(original, false, 200, 302);
        ScrollStitcher capped = new ScrollStitcher(false, down, 4L * WIDTH * (HEIGHT + 133));
        expect(capped.add(screen(302)), ScrollStitcher.Result.FIRST);
        expect(capped.add(screen(383)), ScrollStitcher.Result.LIMIT);
        ScrollPage result = capped.finishPage();
        verifyPage(result.image, 200, 133);
        check(result.offset == 133, "unsafe clipped endpoint");
        verifyPage(result.viewport, 333, 0);

        ScrollStitcher atLimit = new ScrollStitcher(true, result, 4L * WIDTH * result.image.getHeight());
        expect(atLimit.add(screen(302)), ScrollStitcher.Result.LIMIT);
        check(atLimit.finishPage().image == result.image, "changed image at global height limit");

        ScrollStitcher reversed = new ScrollStitcher(true, down);
        expect(reversed.add(screen(302)), ScrollStitcher.Result.FIRST);
        expect(reversed.add(screen(210)), ScrollStitcher.Result.ADDED);
        expect(reversed.add(screen(160)), ScrollStitcher.Result.ADDED);
        Bitmap.allocationHeightLimit = down.image.getHeight() + 23;
        try {
            ScrollPage small = reversed.finishPage();
            check(small.image.getHeight() == down.image.getHeight() + 20, "wrong shortened top extension");
            verifyPage(small.image, 180, 122);
            check(small.offset == 0, "lost top edge after OOM");
            verifyPage(small.viewport, 180, 0);
        } finally { Bitmap.allocationHeightLimit = Integer.MAX_VALUE; }

        ScrollStitcher interrupted = new ScrollStitcher(false, down);
        Rect stop = new Rect(66, 280, 163, 332);
        expect(interrupted.add(screen(302)), ScrollStitcher.Result.FIRST);
        expect(interrupted.add(painted(410,stop),stop), ScrollStitcher.Result.ADDED);
        ScrollPage partial = interrupted.finishPage();
        check(partial.offset == partial.image.getHeight() - HEIGHT, "lost edge after masked capture");
        verifyPage(partial.viewport, 200 + partial.offset, 0);
        verifyPage(partial.image, 200, partial.image.getHeight() - HEIGHT);
    }

    private static void relocateContinuation() {
        // The first screenshot was taken during a fling. The editor returns
        // to a new, overlapping position, before any previous long capture.
        ScrollPage first = new ScrollPage(screen(200));
        ScrollPage.Start fling = first.locate(screen(279));
        check(fling != null && fling.offset == 79, "lost fling from the original screen");
        ScrollStitcher seed = new ScrollStitcher(false, first);
        expect(seed.begin(screen(279), fling), ScrollStitcher.Result.FIRST);
        expect(seed.add(screen(394)), ScrollStitcher.Result.ADDED);
        verifyPage(seed.finishPage().image, 200, 194);

        ScrollPage base = extend(new ScrollPage(screen(200)), false, 200, 302, 410, 505);
        // A closing fling went beyond the last accepted frame. Recover the
        // overlapping rows immediately and join the next gesture without a gap.
        ScrollPage.Start softened = base.locate(subpixelScreen(579));
        check(softened != null && softened.offset == 379, "fractional rasterization lost the trailing viewport");
        ScrollPage.Start interiorSoftened = base.locate(subpixelScreen(324));
        check(interiorSoftened != null && interiorSoftened.offset == 124, "fractional rasterization lost an interior viewport");
        ScrollPage.Start beyond = base.locate(screen(579));
        check(beyond != null && beyond.offset == 379, "lost trailing fling viewport");
        ScrollStitcher s = new ScrollStitcher(false, base);
        expect(s.begin(screen(579), beyond), ScrollStitcher.Result.FIRST);
        expect(s.add(screen(656)), ScrollStitcher.Result.ADDED);
        ScrollPage continued = s.finishPage();
        verifyPage(continued.image, 200, 456);
        verifyPage(continued.viewport, 656, 0);

        ScrollPage.Start interior = continued.locate(screen(340));
        check(interior != null && interior.offset == 140, "could not relocate interior viewport");
        s = new ScrollStitcher(true, continued);
        expect(s.begin(screen(340), interior), ScrollStitcher.Result.FIRST);
        expect(s.add(screen(230)), ScrollStitcher.Result.ADDED);
        expect(s.add(screen(155)), ScrollStitcher.Result.ADDED);
        ScrollPage moved = s.finishPage();
        verifyPage(moved.image, 155, 501);
        verifyPage(moved.viewport, 155, 0);

        ScrollPage.Start above = moved.locate(screen(107));
        check(above != null && above.offset == -48, "missing earlier fling viewport");
        s = new ScrollStitcher(false, moved);
        expect(s.begin(screen(107), above), ScrollStitcher.Result.FIRST);
        expect(s.add(screen(190)), ScrollStitcher.Result.ADDED);
        ScrollPage reversed = s.finishPage();
        verifyPage(reversed.image, 107, 549);
        verifyPage(reversed.viewport, 190, 0);
        check(reversed.offset == 83, "lost offset after bridging and reversing");
        check(moved.locate(screen(2500)) == null, "unrelated page falsely rejoined");

        s = new ScrollStitcher(false, moved);
        expect(s.begin(screen(107), above), ScrollStitcher.Result.FIRST);
        s.close();
        expect(s.begin(screen(155), moved.here()), ScrollStitcher.Result.FIRST);
        ScrollPage retried = s.finishPage();
        check(retried.image == moved.image && !moved.viewport.isRecycled(), "retry failed to release bridge safely");
    }

    private static void rejectSharedChrome() {
        Bitmap old = screen(200), other = screen(200);
        for (int y=HEIGHT/3; y<HEIGHT-FOOTER; y++) for (int x=0; x<WIDTH; x++) {
            old.setPixel(x,y,0xffc0ffcc);
            other.setPixel(x,y,0xff181827);
        }
        check(!ScreenshotViewport.matches(old,other), "unrelated colors passed identity comparison");
        check(ScrollPosition.find(old, other)==null, "shared page header falsely identified a different app");
        check(ScrollPosition.find(repeated(0), repeated(11)) == null, "ambiguous repeat relocated a page");
    }

    private static void persistentStop() {
        Rect button = new Rect(66, 280, 163, 332);
        for (boolean up : new boolean[]{false, true}) {
            int origin = up ? 315 : 0, sign = up ? -1 : 1;
            for (int endStep : new int[]{0, 9}) {
                ScrollStitcher s = new ScrollStitcher(up);
                expect(s.add(screen(origin)), ScrollStitcher.Result.FIRST);
                for (int d : new int[]{77, 145, 226})
                    expect(s.add(painted(origin + sign*d, button), button), ScrollStitcher.Result.ADDED);
                Bitmap clean = screen(origin + sign*(226 + endStep));
                expect(s.add(clean), endStep == 0 ? ScrollStitcher.Result.UNCHANGED : ScrollStitcher.Result.ADDED);
                verifyPage(s.finish(), up ? origin - 226 - endStep : origin, 226 + endStep);
                check(!s.shortenedOnFinish(), "valid clean endpoint unexpectedly trimmed");
            }

            // A wrong-app final frame cannot replace the trusted tail.
            ScrollStitcher interrupted = new ScrollStitcher(up);
            expect(interrupted.add(screen(origin)), ScrollStitcher.Result.FIRST);
            for (int d : new int[]{77, 145, 226})
                expect(interrupted.add(painted(origin + sign*d, button), button), ScrollStitcher.Result.ADDED);
            Bitmap unrelated = screen(3000);
            expect(interrupted.add(unrelated), ScrollStitcher.Result.NO_MATCH);
            int safe = up ? 226 : 226 - (HEIGHT - FOOTER - button.top);
            verifyPage(interrupted.finish(), up ? origin - 226 : origin, safe);
            check(interrupted.shortenedOnFinish() == !up, "interrupted endpoint status");
        }

        // The button can overlap the footer. Its clean counterpart comes from frame one.
        Rect low = new Rect(65, 340, 161, 390);
        ScrollStitcher s = new ScrollStitcher();
        expect(s.add(screen(0)), ScrollStitcher.Result.FIRST);
        expect(s.add(painted(90, low), low), ScrollStitcher.Result.ADDED);
        expect(s.add(painted(183, low), low), ScrollStitcher.Result.ADDED);
        verifyPage(s.finish(), 0, 183 - (HEIGHT - FOOTER - low.top));

        // A clean final may arrive at the capped position or after a last in-flight gesture.
        for (int finalOffset : new int[]{145, 164}) {
            s = new ScrollStitcher(4L * WIDTH * (HEIGHT + 135));
            expect(s.add(screen(0)), ScrollStitcher.Result.FIRST);
            expect(s.add(painted(77, button), button), ScrollStitcher.Result.ADDED);
            expect(s.add(painted(145, button), button), ScrollStitcher.Result.LIMIT);
            expect(s.add(screen(finalOffset)), finalOffset == 145
                    ? ScrollStitcher.Result.UNCHANGED : ScrollStitcher.Result.LIMIT);
            verifyPage(s.finish(), 0, 135);
        }

        s = new ScrollStitcher();
        Bitmap unsafeFirst = painted(0, button);
        expect(s.add(unsafeFirst, button), ScrollStitcher.Result.NO_MATCH);
        check(unsafeFirst.isRecycled() && s.finish() == null, "accepted an overlaid start");
        Bitmap original = screen(0);
        expect(s.add(original), ScrollStitcher.Result.FIRST);
        expect(s.add(painted(18, button), button), ScrollStitcher.Result.ADDED);
        check(s.finish() == original && s.shortenedOnFinish(), "unsafe progress replaced clean original");
    }

    private static Bitmap painted(int offset, Rect button) {
        Bitmap b = screen(offset);
        for (int y=button.top; y<button.bottom; y++)
            for (int x=button.left; x<button.right; x++)
                b.setPixel(x,y, ((x / 4 + y / 3) % 2 == 0) ? 0xffff00ff : 0xff00cccc);
        return b;
    }

    private static void upwardScreens() {
        ScrollStitcher s = new ScrollStitcher(true);
        Bitmap bottom = screen(165), middle = screen(50), top = screen(43);
        expect(s.add(bottom), ScrollStitcher.Result.FIRST);
        Bitmap duplicate = screen(165);
        expect(s.add(duplicate), ScrollStitcher.Result.UNCHANGED);
        expect(s.add(middle), ScrollStitcher.Result.ADDED);
        expect(s.add(top), ScrollStitcher.Result.ADDED);
        Bitmap out = s.finish();
        verifyPage(out, 43, 122);
        check(duplicate.isRecycled() && bottom.isRecycled() && middle.isRecycled() && top.isRecycled(),
                "upward frames were not released");

        // Keep the nearest earlier rows and both toolbars when the cap falls within a swipe.
        s = new ScrollStitcher(true, 4L * WIDTH * (HEIGHT + 50));
        expect(s.add(screen(165)), ScrollStitcher.Result.FIRST);
        expect(s.add(screen(50)), ScrollStitcher.Result.LIMIT);
        verifyPage(s.finish(), 115, 50);

        s = new ScrollStitcher(true);
        expect(s.add(screen(165)), ScrollStitcher.Result.FIRST);
        expect(s.add(screen(50)), ScrollStitcher.Result.ADDED);
        expect(s.add(screen(43)), ScrollStitcher.Result.ADDED);
        Bitmap.allocationHeightLimit = HEIGHT + 70;
        try {
            verifyPage(s.finish(), 104, 61);
            check(s.shortenedOnFinish(), "silent shortened upward result");
        } finally {
            Bitmap.allocationHeightLimit = Integer.MAX_VALUE;
        }

        // Never append rows in the opposite direction.
        s = new ScrollStitcher(true);
        bottom = screen(0);
        expect(s.add(bottom), ScrollStitcher.Result.FIRST);
        expect(s.add(screen(115)), ScrollStitcher.Result.NO_MATCH);
        check(s.finish() == bottom, "wrong-direction frame replaced the starting screen");
    }

    private static void verifyPage(Bitmap out, int startOffset, int addedRows) {
        check(out.getHeight() == HEIGHT + addedRows, "incorrect page height");
        for (int y=0; y < out.getHeight(); y++) {
            for (int x=0; x < WIDTH; x++) {
                int expected = y < HEADER ? header(x,y)
                        : y >= HEIGHT - FOOTER + addedRows ? footer(x,y-addedRows)
                        : content(x, y+startOffset);
                if (out.getPixel(x,y) != expected)
                    throw new AssertionError("incorrect stitch at " + x + "," + y);
            }
        }
    }

    private static Bitmap subpixelScreen(int offset) {
        Bitmap b = screen(offset);
        for (int y = HEADER; y < HEIGHT - FOOTER; y++) for (int x = 0; x < WIDTH; x++) {
            int a = content(x, y + offset), next = content(x, y + offset + 1);
            int color = 0xff000000;
            for (int shift = 0; shift <= 16; shift += 8)
                color |= ((3 * (a >> shift & 255) + (next >> shift & 255)) / 4) << shift;
            b.setPixel(x, y, color);
        }
        return b;
    }

    private static Bitmap repeated(int shift) {
        Bitmap b=screen(0);
        for (int y=HEADER; y<HEIGHT-FOOTER; y++) for (int x=0; x<WIDTH; x++)
            b.setPixel(x,y,content(x, (shift+y)%31));
        return b;
    }

    private static Bitmap screen(int offset) {
        Bitmap b=Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        for (int y=0; y<HEIGHT; y++) for (int x=0;x<WIDTH;x++)
            b.setPixel(x,y, y<HEADER ? header(x,y) : y>=HEIGHT-FOOTER ? footer(x,y)
                    : content(x,y+offset));
        return b;
    }

    private static int header(int x, int y) { return 0xff888888 + (x%3) * 512 + (y%5)*64; }
    private static int footer(int x, int y) { return 0xffbbbbbb + (x%5) * 64 + (y%3)*512; }

    // Page of irregular, dark strokes on a white background with line spacing.
    private static int content(int x, int y) {
        if (y%21<7 || x<7 || x>WIDTH-16) return 0xfffafafa;
        int v = (y/21)*1103515245 ^ (x/4)*4813423 ^ ((x+y%21)/3)*22039;
        return (v & (1 << ((y/11)%23))) == 0 ? 0xfffafafa : 0xff213459;
    }

    private static void expect(ScrollStitcher.Result actual, ScrollStitcher.Result expected) {
        check(actual == expected, "expected "+expected+", got "+actual);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
