package ru.dot.clipshot;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import java.util.ArrayList;

/** Owns the supplied captures. Access from one worker at a time. */
final class ScrollStitcher {
    enum Result { FIRST, ADDED, UNCHANGED, NO_MATCH, LIMIT, MEMORY }

    private static final int MAX_HEIGHT = 16384;
    private static final long MAX_IMAGE_BYTES = 64L * 1024 * 1024;
    private final long imageBudget;
    // Logical coordinates increase in the direction of the swipe. The starting
    // viewport may lie anywhere inside the previous capture after retracing it.
    private final ScrollPage editorPage;
    private ScrollPage base;
    private int seedOffset, origin, headerEnd;
    private boolean ownsBridge;
    private int retainedPrefix = -1;
    // Logical rows run from the starting edge toward newly captured content.
    // Bitmaps and their strips always stay in normal display orientation.
    private final boolean upward;
    private final ArrayList<Bitmap> strips = new ArrayList<>();
    private Bitmap first, tail;
    private ScrollOverlap.Frame previous;
    private int outputHeight, firstEnd, start, bodyEnd, footerStart, clippedRows;
    private boolean useFirstFooter;
    private boolean shortenedOnFinish;

    ScrollStitcher() { this(false); }

    ScrollStitcher(boolean upward) {
        this(upward, null);
    }

    ScrollStitcher(boolean upward, ScrollPage base) {
        this(upward, base, defaultImageBudget());
    }

    private static long defaultImageBudget() {
        return Math.min(MAX_IMAGE_BYTES, Runtime.getRuntime().maxMemory() / 6);
    }

    static int maxOutputHeight(int width) {
        return maxOutputHeight(width, defaultImageBudget());
    }

    private static int maxOutputHeight(int width, long budget) {
        return (int) Math.min(MAX_HEIGHT, budget / (4L * width));
    }

    ScrollStitcher(long imageBudget) { this(false, imageBudget); }

    ScrollStitcher(boolean upward, long imageBudget) {
        this(upward, null, imageBudget);
    }

    ScrollStitcher(boolean upward, ScrollPage base, long imageBudget) {
        this.editorPage = this.base = base;
        this.upward = upward;
        positionSeed(base == null ? 0 : base.offset);
        this.imageBudget = imageBudget;
    }

    private void positionSeed(int offset) {
        seedOffset = offset;
        origin = base != null && upward ? base.image.getHeight() - base.viewport.getHeight() - offset : offset;
    }

    /** The previously captured overlap also covers movement while the editor was open. */
    Result begin(Bitmap live, ScrollPage.Start location) {
        if (tail != null || base == null || live == null || location == null
                || live.getHeight() != base.viewport.getHeight() || live.getWidth() != base.image.getWidth()) {
            if (live != null) live.recycle();
            return Result.NO_MATCH;
        }
        int oldHeight = base.image.getHeight(), h = live.getHeight(), w = live.getWidth();
        int offset = location.offset;
        if (offset < 0 || offset > oldHeight - h) {
            // The user (or a still-settling scroll) has moved just past an edge.
            // Reconnect the shared moving content before allowing another gesture.
            int seam = location.seam, oldSeam = offset + seam;
            if (seam <= 0 || seam >= h || oldSeam <= 0 || oldSeam >= oldHeight) {
                live.recycle();
                return Result.NO_MATCH;
            }
            int combined = offset < 0 ? oldHeight - offset : offset + h;
            if (combined > maxOutputHeight(w, imageBudget)) {
                live.recycle();
                return Result.LIMIT;
            }
            Bitmap joined = null;
            try {
                joined = Bitmap.createBitmap(w, combined, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(joined);
                if (offset < 0) {
                    drawSlice(canvas, live, 0, seam, 0);
                    drawSlice(canvas, base.image, oldSeam, oldHeight, seam);
                    offset = 0;
                } else {
                    drawSlice(canvas, base.image, 0, oldSeam, 0);
                    drawSlice(canvas, live, seam, h, oldSeam);
                }
                base = new ScrollPage(joined, live, offset);
                ownsBridge = true;
            } catch (OutOfMemoryError exhausted) {
                if (joined != null) joined.recycle();
                live.recycle();
                return Result.MEMORY;
            } catch (RuntimeException failed) {
                if (joined != null) joined.recycle();
                live.recycle();
                throw failed;
            }
        }
        positionSeed(offset);
        return add(live);
    }

    private static void drawSlice(Canvas canvas, Bitmap source, int from, int to, int y) {
        if (to <= from) return;
        canvas.drawBitmap(source, new Rect(0, from, source.getWidth(), to),
                new Rect(0, y, source.getWidth(), y + to - from), null);
    }

    private int physicalTop(int height, int logicalStart, int count) {
        return upward ? height - logicalStart - count : logicalStart;
    }

    /** Takes ownership, even when the frame is rejected. */
    Result add(Bitmap frame) { return add(frame, null); }

    /** stopBounds is the on-screen control in the captured frame's physical coordinates. */
    Result add(Bitmap frame, Rect stopBounds) { return add(frame, stopBounds, 0); }

    Result add(Bitmap frame, Rect stopBounds, int expectedShift) {
        if (frame == null) return Result.NO_MATCH;
        int w = frame.getWidth(), h = frame.getHeight();
        try {
            if (tail == null) {
                if (stopBounds != null || (base != null && (base.viewport.getHeight() != h
                        || base.viewport.getWidth() != w))) return Result.NO_MATCH;
                first = tail = frame;
                outputHeight = firstEnd = bodyEnd = footerStart = h;
                shortenedOnFinish = false;
                if (Math.max(h, previousHeight()) >= maxOutputHeight(w, imageBudget)) return Result.LIMIT;
                previous = sample(frame, null);
                return Result.FIRST;
            }
            if (w != tail.getWidth() || h != tail.getHeight()) return Result.NO_MATCH;
            int limit = maxOutputHeight(w, imageBudget) - origin;
            if (previous == null) return Result.LIMIT;
            // Even a capped capture can replace its last overlaid frame with a clean one.
            if (outputHeight >= limit && (previous.mask == null || stopBounds != null)) return Result.LIMIT;
            ScrollOverlap.Frame current = sample(frame, stopBounds);
            ScrollOverlap.Match match = ScrollOverlap.find(previous, current, start, expectedShift);
            if (match == null) return Result.NO_MATCH;
            if (match.shift == 0) {
                if (previous.mask != null && current.mask == null) {
                    tail.recycle();
                    tail = frame;
                    previous = current;
                }
                return Result.UNCHANGED;
            }

            int keep = match.seam + match.shift - start;
            if (base != null && previousHeight() > h && retainedPrefix < 0) {
                // The editor image already contains the entire retraced interval.
                // At the first crossing, cut it exactly at this verified moving
                // row and retain only the new tail. No duplicated strips or bars.
                if (origin + outputHeight + match.shift > previousHeight())
                    retainedPrefix = origin + outputHeight - h + match.shift + match.seam;
                if (tail != first) tail.recycle();
            } else if (tail == first) {
                // Keep the first screen intact as a no-allocation fallback.
                firstEnd = keep;
            } else {
                Bitmap strip = null;
                if (keep > 0) {
                    strip = Bitmap.createBitmap(tail, 0, physicalTop(h, start, keep), w, keep);
                    try {
                        strips.add(strip);
                    } catch (OutOfMemoryError exhausted) {
                        if (strip != tail) strip.recycle();
                        throw exhausted;
                    }
                }
                if (strip != tail) tail.recycle();
            }
            tail = frame;
            previous = current;
            start = match.seam;
            int advance = Math.min(match.shift, limit - outputHeight);
            headerEnd = match.headerEnd;
            footerStart = match.footerStart;
            clippedRows += match.shift - advance;
            bodyEnd = Math.max(start, footerStart - clippedRows);
            outputHeight += advance;
            return outputHeight == limit ? Result.LIMIT : Result.ADDED;
        } catch (OutOfMemoryError exhausted) {
            return Result.MEMORY;
        } finally {
            if (tail != frame) frame.recycle();
        }
    }

    private ScrollOverlap.Frame sample(Bitmap bitmap, Rect bounds) {
        int h = bitmap.getHeight(), w = bitmap.getWidth();
        int[] pixels = new int[h * ScrollOverlap.COLUMNS];
        // Read a few vertical columns rather than allocating a second full frame.
        // Leave the scrollbar and edge gestures out of the alignment score.
        int coveredGroups = 0;
        for (int i = 0; i < ScrollOverlap.COLUMNS; i++) {
            int x = (int) ((.04 + .92 * (i + .5) / ScrollOverlap.COLUMNS) * w);
            if (bounds != null && x >= bounds.left && x < bounds.right)
                coveredGroups |= ScrollOverlap.groupAt(i);
            bitmap.getPixels(pixels, i, ScrollOverlap.COLUMNS, Math.min(x, w - 1), 0, 1, h);
        }
        ScrollOverlap.Mask mask = null;
        if (bounds != null && bounds.left < w && bounds.right > 0 && bounds.left < bounds.right) {
            int top = Math.max(0, bounds.top), bottom = Math.min(h, bounds.bottom);
            if (top < bottom) mask = new ScrollOverlap.Mask(
                    upward ? h - bottom : top, upward ? h - top : bottom, coveredGroups);
        }
        return new ScrollOverlap.Frame(pixels, upward, mask);
    }

    private int previousHeight() { return base == null ? first.getHeight() : base.image.getHeight(); }

    boolean needsShortSwipe() { return previous != null && previous.needsShortSwipe(); }

    /** Transfers the assembled image. Standalone captures own all of their source frames. */
    Bitmap finish() {
        if (first == null) return null;
        try {
            Bitmap output = buildResult();
            if (output == first) {
                if (tail == first) tail = null;
                first = null;
            }
            return output;
        } finally {
            close();
        }
    }

    /** Returns both the assembled page and the verified position for the next editor action. */
    ScrollPage finishPage() {
        if (first == null) return null;
        try {
            Bitmap output = buildResult();
            int growth = output.getHeight() - previousHeight();
            int extra = output.getHeight() - first.getHeight();
            int shiftedSeed = seedOffset + (upward ? growth : 0);
            int travel = outputHeight - first.getHeight() + clippedRows;
            int actualOffset = shiftedSeed + (upward ? -travel : travel);
            int offset = Math.max(0, Math.min(extra, actualOffset));
            Bitmap anchor;
            if (travel == 0 && seedOffset == base.offset && output == base.image) {
                // A user-authorized fresh start can be visually different from
                // the single screenshot. Keep the editor intact if nothing grew.
                anchor = base.viewport;
                offset = base.offset;
            } else if (offset == actualOffset && previous != null && previous.mask == null) {
                anchor = tail;
            } else {
                // Model the nearest retained viewport with each fixed bar taken
                // once from the assembled image. This keeps Stop out of the seed.
                anchor = recoveryViewport(output, offset);
                if (anchor == null) {
                    anchor = base.viewport;
                    offset = base.offset + (upward ? growth : 0);
                }
            }
            ScrollPage next = output == editorPage.image && anchor == editorPage.viewport && offset == editorPage.offset
                    ? editorPage : new ScrollPage(output, anchor, offset);
            if (output == base.image) ownsBridge = false;
            if (anchor == tail) tail = null;
            if (anchor == first) first = null;
            return next;
        } finally {
            close();
        }
    }

    private Bitmap recoveryViewport(Bitmap output, int offset) {
        int h = first.getHeight(), extra = output.getHeight() - h;
        if (extra == 0) return output;
        if (headerEnd >= footerStart) return null;
        int top = upward ? h - footerStart : headerEnd;
        int bottom = upward ? h - headerEnd : footerStart;
        Bitmap recovered = null;
        try {
            recovered = Bitmap.createBitmap(output.getWidth(), h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(recovered);
            drawSlice(canvas, output, 0, top, 0);
            drawSlice(canvas, output, offset + top, offset + bottom, top);
            drawSlice(canvas, output, extra + bottom, extra + h, bottom);
            return recovered;
        } catch (RuntimeException | OutOfMemoryError failure) {
            if (recovered != null) recovered.recycle();
            return null;
        }
    }

    private Bitmap buildResult() {
        int minimum = previousHeight();
        int goal = origin + outputHeight;
        if (previous != null && previous.mask != null) {
            ScrollOverlap.Mask mask = previous.mask;
            useFirstFooter = mask.end > footerStart;
            if (!upward && mask.start < bodyEnd && mask.end > start) {
                int removed = bodyEnd - Math.max(start, mask.start);
                bodyEnd -= removed;
                goal -= removed;
                shortenedOnFinish = true;
            }
        }
        while (goal > minimum) {
            try {
                return assemble(goal);
            } catch (OutOfMemoryError exhausted) {
                shortenedOnFinish = true;
                goal = minimum + (goal - minimum) / 2;
            }
        }
        return base == null ? first : base.image;
    }

    boolean shortenedOnFinish() { return shortenedOnFinish; }

    private Bitmap assemble(int height) {
        int w = first.getWidth();
        // The logical footer is the fixed top bar on an upward capture.
        int footerHeight = tail.getHeight() - footerStart;
        int bodyHeight = height - footerHeight;
        Bitmap result = Bitmap.createBitmap(w, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(result);
            int y = retainedPrefix >= 0
                    ? drawPart(canvas, base.image, 0, retainedPrefix, 0, bodyHeight, height)
                    : drawPart(canvas, first, 0, firstEnd, 0, bodyHeight, height);
            for (Bitmap strip : strips) {
                if (y == bodyHeight) break;
                y = drawPart(canvas, strip, 0, strip.getHeight(), y, bodyHeight, height);
            }
            y = drawPart(canvas, tail, start, bodyEnd, y, bodyHeight, height);
            Bitmap footer = useFirstFooter ? first : tail;
            drawPart(canvas, footer, footerStart, footer.getHeight(), y, height, height);
            return result;
        } catch (RuntimeException | OutOfMemoryError error) {
            result.recycle();
            throw error;
        }
    }

    private int drawPart(Canvas canvas, Bitmap src, int top, int bottom, int y, int limit, int height) {
        int count = Math.min(bottom - top, limit - y);
        if (count > 0) {
            int w = src.getWidth();
            int srcY = physicalTop(src.getHeight(), top, count);
            int dstY = physicalTop(height, y, count);
            canvas.drawBitmap(src, new Rect(0, srcY, w, srcY + count),
                    new Rect(0, dstY, w, dstY + count), null);
            y += count;
        }
        return y;
    }

    void close() {
        if (tail != null && tail != first) tail.recycle();
        if (first != null) first.recycle();
        for (Bitmap strip : strips) strip.recycle();
        strips.clear();
        if (ownsBridge) base.image.recycle();
        ownsBridge = false;
        base = editorPage;
        positionSeed(base == null ? 0 : base.offset);
        first = tail = null;
        previous = null;
        start = bodyEnd = footerStart = firstEnd = outputHeight = clippedRows = 0;
        useFirstFooter = false;
        retainedPrefix = -1;
        headerEnd = 0;
    }
}
