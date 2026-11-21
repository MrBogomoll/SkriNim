package ru.dot.clipshot;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class ScreenshotStorage {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);

    /** Saves the editor's rendered selection. Runs on a worker and borrows the bitmap. */
    static Uri save(Context context, Bitmap edited) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                "Screenshot_" + LocalDateTime.now().format(FILE_TIME) + "_SkriNim.png");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, albumPath());
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        // Each insert reserves its own item. MediaStore disambiguates identical display names.
        Uri item = resolver.insert(collection, values);
        if (item == null) throw new IOException("Could not create screenshot");
        boolean published = false;
        try {
            ParcelFileDescriptor descriptor = resolver.openFileDescriptor(item, "w");
            if (descriptor == null) throw new IOException("Could not open screenshot");
            try (ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
                if (!edited.compress(Bitmap.CompressFormat.PNG, 100, out))
                    throw new IOException("PNG encoding failed");
                out.flush();
                if (descriptor.getStatSize() == 0) throw new IOException("Empty PNG file");
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(item, values, null, null) != 1)
                throw new IOException("Could not publish screenshot");
            published = true;
            return item;
        } finally {
            if (!published) {
                try {
                    resolver.delete(item, null, null);
                } catch (RuntimeException cleanupFailed) {
                    Log.w("SkriNim", "Could not remove pending screenshot", cleanupFailed);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static String albumPath() {
        String screenshots = Environment.DIRECTORY_SCREENSHOTS;
        String pictures = Environment.DIRECTORY_PICTURES;
        String camera = Environment.DIRECTORY_DCIM;
        // Some phones keep their built-in screenshot album beside the camera
        // folder. Only check the two directory entries. MediaStore owns the write.
        try {
            if (!new File(Environment.getExternalStoragePublicDirectory(pictures), screenshots).isDirectory()
                    && new File(Environment.getExternalStoragePublicDirectory(camera), screenshots).isDirectory())
                return camera + "/" + screenshots;
        } catch (SecurityException ignored) {
            // Scoped storage can hide an existing directory from the app.
        }
        return pictures + "/" + screenshots;
    }
}
