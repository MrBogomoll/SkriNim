package ru.dot.clipshot;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/** Encodes directly into the chunks later streamed by the clipboard provider. */
final class PngBuffer extends OutputStream {
    private static final int CHUNK = 64 * 1024;
    private final ArrayList<byte[]> chunks = new ArrayList<>();
    private int size;

    int size() { return size; }

    private byte[] writableChunk() {
        if (size % CHUNK == 0) chunks.add(new byte[CHUNK]);
        return chunks.get(chunks.size() - 1);
    }

    @Override public void write(int value) {
        writableChunk()[size % CHUNK] = (byte)value;
        size++;
    }

    @Override public void write(byte[] bytes, int offset, int count) {
        if (offset < 0 || count < 0 || offset > bytes.length - count)
            throw new IndexOutOfBoundsException();
        while (count > 0) {
            int position = size % CHUNK;
            int length = Math.min(count, CHUNK - position);
            System.arraycopy(bytes, offset, writableChunk(), position, length);
            size += length;
            offset += length;
            count -= length;
        }
    }

    void writeTo(OutputStream output) throws IOException {
        int remaining = size;
        for (byte[] chunk : chunks) {
            int length = Math.min(remaining, CHUNK);
            output.write(chunk, 0, length);
            remaining -= length;
        }
    }
}
