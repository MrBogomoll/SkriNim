package ru.dot.clipshot;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.PersistableBundle;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

final class ShotMemory {
    private static final String AUTH = "ru.dot.clipshot.image";
    private static Bitmap preview;
    private static volatile Entry entry;

    static void putPreview(Bitmap bitmap) { preview = bitmap; }
    static Bitmap preview() { return preview; }
    static void clearPreview(Bitmap expected) { if (preview == expected) preview = null; }

    private static final class Entry {
        final Uri uri;
        final byte[] png;
        Entry(byte[] png) {
            this.uri = new Uri.Builder().scheme("content").authority(AUTH)
                    .appendPath(UUID.randomUUID().toString() + ".png").build();
            this.png = png;
        }
    }

    static byte[] find(Uri uri) {
        Entry current = entry;
        return current != null && current.uri.equals(uri) ? current.png : null;
    }

    static boolean copy(Context context, Bitmap bitmap) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)) return false;
        Entry next = new Entry(buffer.toByteArray());
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
