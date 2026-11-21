package ru.dot.clipshot;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.PersistableBundle;
import java.util.UUID;

final class ShotMemory {
    private static final String AUTH = "ru.dot.clipshot.image";
    private static ScrollPage page;
    private static ScrollGestureService.Origin origin;
    private static volatile Entry entry;

    static void putPage(ScrollPage next) {
        if (page != next) origin = null;
        page = next;
    }
    static void setOrigin(ScrollGestureService.Origin value) { origin = value; }
    static ScrollGestureService.Origin origin() { return origin; }
    static ScrollPage page() { return page; }
    static Bitmap preview() { return page == null ? null : page.image; }
    static void clearPreview(Bitmap expected) { if (preview() == expected) putPage(null); }

    private static final class Entry {
        final Uri uri;
        final PngBuffer png;
        Entry(PngBuffer png) {
            this.uri = new Uri.Builder().scheme("content").authority(AUTH)
                    .appendPath(UUID.randomUUID().toString() + ".png").build();
            this.png = png;
        }
    }

    static PngBuffer find(Uri uri) {
        Entry current = entry;
        return current != null && current.uri.equals(uri) ? current.png : null;
    }

    static PngBuffer encode(Bitmap bitmap) {
        PngBuffer png = new PngBuffer();
        return bitmap.compress(Bitmap.CompressFormat.PNG, 100, png) ? png : null;
    }

    static boolean copy(Context context, PngBuffer png) {
        Entry next = new Entry(png);
        Entry old = entry;
        entry = next;
        try {
            ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
            ClipData clip = ClipData.newUri(context.getContentResolver(), "Скриншот", next.uri);
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras);
            clipboard.setPrimaryClip(clip);
            return true;
        } catch (RuntimeException ex) {
            entry = old;
            return false;
        }
    }
}
