package ru.dot.clipshot;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ShotProvider extends ContentProvider {
    private final ExecutorService writes = Executors.newFixedThreadPool(2);
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                   String[] selectionArgs, String sortOrder) {
        String[] columns = projection == null
            ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        byte[] png = ShotMemory.find(uri);
        if (png != null) {
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = "Скриншот.png";
                if (OpenableColumns.SIZE.equals(columns[i])) values[i] = png.length;
            }
            cursor.addRow(values);
        }
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        byte[] png = ShotMemory.find(uri);
        if (!"r".equals(mode) || png == null) throw new FileNotFoundException("Снимок недоступен");
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            writes.execute(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                        new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(png);
                } catch (IOException ignored) {}
            });
            return pipe[0];
        } catch (IOException ex) {
            throw new FileNotFoundException("Не удалось открыть снимок");
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
}
