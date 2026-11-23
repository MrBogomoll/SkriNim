package ru.dot.clipshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AtomicFile;

import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** On-device OCR. Only public model files are persisted; no pixels or text leave memory. */
final class TextOcr {
    private static final String MODEL_VERSION = "ocr-tessdata-fast-4.1.0";
    private static final String ALL_LANGUAGES = "rus+eng+chi_sim+jpn+kor+ukr";
    private static final String[] MODEL_CODES = {"rus", "eng", "chi_sim", "jpn", "kor", "ukr"};
    private static final int[] MODEL_BYTES = {3861738, 4113088, 2469156, 2471260, 1677415, 3825102};
    private static final int WORD = TessBaseAPI.PageIteratorLevel.RIL_WORD;

    private TextOcr() {}

    /** Worker-thread call. Returns [left, top, right, bottom, ...], with exclusive right/bottom. */
    static int[] detect(Context context, Bitmap bitmap) throws IOException, InterruptedException {
        if (bitmap == null || bitmap.isRecycled()) throw new IllegalArgumentException("Invalid input bitmap");
        File dataPath = installModels(context);
        // The primary pass is intentionally identical to 0.22. Additional language
        // packs and keypad segmentation must never change these result rectangles.
        int[] primary = recognize(dataPath, bitmap, "rus+eng", TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT, false);
        int[] numeric;
        try {
            numeric = recognize(dataPath, bitmap, ALL_LANGUAGES, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, true);
        } catch (IOException | RuntimeException supplementalFailure) {
            checkCancelled();
            return primary; // A supplemental model error must not lose the main result.
        }
        checkCancelled();
        return OcrNumeric.append(primary, numeric);
    }

    private static int[] recognize(File dataPath, Bitmap bitmap, String languages, int segmentation,
                                   boolean numericOnly) throws IOException, InterruptedException {
        checkCancelled();
        TessBaseAPI tess = new TessBaseAPI();
        try {
            if (!tess.init(dataPath.getAbsolutePath(), languages, TessBaseAPI.OEM_LSTM_ONLY)) {
                throw new IOException("Unable to initialize offline OCR");
            }
            checkCancelled();
            tess.setPageSegMode(segmentation);
            tess.setImage(bitmap);
            tess.getUTF8Text(); // Starts recognition; the transient return value is discarded.
            checkCancelled();
            ResultIterator iter = tess.getResultIterator();
            if (iter == null) return new int[0];
            try {
                int[] boxes = new int[128];
                int size = 0;
                int width = bitmap.getWidth(), height = bitmap.getHeight();
                iter.begin();
                do {
                    checkCancelled();
                    int[] bounds = iter.getBoundingBox(WORD);
                    if (bounds == null || bounds.length != 4) continue;
                    float confidence = iter.confidence(WORD);
                    if (!(confidence >= 30f) && !numericOnly) continue;
                    int left = clamp(bounds[0], width), top = clamp(bounds[1], height);
                    int right = clamp(bounds[2], width), bottom = clamp(bounds[3], height);
                    if (left >= right || top >= bottom) continue;
                    if (numericOnly && !OcrNumeric.accept(iter.getUTF8Text(WORD),confidence,
                            right - left,bottom - top)) continue;
                    if (size == boxes.length) boxes = Arrays.copyOf(boxes, boxes.length * 2);
                    boxes[size++] = left;
                    boxes[size++] = top;
                    boxes[size++] = right;
                    boxes[size++] = bottom;
                } while (iter.next(WORD));
                return Arrays.copyOf(boxes, size);
            } finally {
                iter.delete();
            }
        } finally {
            tess.recycle();
        }
    }

    private static int clamp(int coordinate, int max) {
        return Math.max(0, Math.min(max, coordinate));
    }

    private static void checkCancelled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    private static synchronized File installModels(Context context) throws IOException, InterruptedException {
        checkCancelled();
        File parent = new File(context.getNoBackupFilesDir(), MODEL_VERSION);
        File languages = new File(parent, "tessdata");
        if (!languages.isDirectory() && !languages.mkdirs()) throw new IOException("Unable to create model directory");
        for (int i = 0; i < MODEL_CODES.length; i++) {
            checkCancelled();
            install(context, languages, MODEL_CODES[i] + ".traineddata", MODEL_BYTES[i]);
        }
        return parent;
    }

    private static void install(Context context, File directory, String name, int expectedBytes) throws IOException {
        File path = new File(directory, name);
        if (path.isFile() && path.length() == expectedBytes) return;
        AtomicFile model = new AtomicFile(path);
        try (InputStream input = context.getAssets().open("tessdata/" + name)) {
            FileOutputStream output = model.startWrite();
            try {
                byte[] buffer = new byte[32768];
                int total = 0;
                for (int count; (count = input.read(buffer)) != -1; ) {
                    output.write(buffer, 0, count);
                    total += count;
                }
                if (total != expectedBytes) throw new IOException("Incomplete offline OCR model: " + name);
                model.finishWrite(output);
            } catch (IOException | RuntimeException e) {
                model.failWrite(output);
                throw e;
            }
        }
    }
}
