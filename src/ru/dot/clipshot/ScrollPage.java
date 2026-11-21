package ru.dot.clipshot;

import android.graphics.Bitmap;

/** Editor image plus a verified screen within it. The offset counts scrolled content rows. */
final class ScrollPage {
    final Bitmap image, viewport;
    final int offset;

    ScrollPage(Bitmap image) { this(image, image, 0); }

    ScrollPage(Bitmap image, Bitmap viewport, int offset) {
        if (image.getWidth() != viewport.getWidth() || offset < 0
                || offset > image.getHeight() - viewport.getHeight())
            throw new IllegalArgumentException("Viewport outside the captured page");
        this.image = image;
        this.viewport = viewport;
        this.offset = offset;
    }

    /** Viewport offset in document coordinates and a verified row inside the shared content. */
    static final class Start {
        final int offset, seam;
        Start(int offset, int seam) { this.offset = offset; this.seam = seam; }
    }

    Start here() { return new Start(offset, -1); }

    Start locate(Bitmap live) {
        if (live == null || live.getWidth() != viewport.getWidth() || live.getHeight() != viewport.getHeight())
            return null;
        if (ScreenshotViewport.matches(viewport, live)) return here();
        Start position = ScrollPosition.find(image, live);
        // The earlier identity check is authoritative for an unchanged plain
        // screenshot. Re-location can prove a distinct scroll position.
        return position != null && image == viewport && position.offset == 0 ? null : position;
    }

    // Discard an unpublished result. Its predecessor can still be drawn by the editor.
    void discardComparedTo(ScrollPage previous) {
        if (image != previous.image && image != previous.viewport) image.recycle();
        if (viewport != image && viewport != previous.image && viewport != previous.viewport)
            viewport.recycle();
    }
}
