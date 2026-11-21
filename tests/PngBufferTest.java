package ru.dot.clipshot;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class PngBufferTest {
    public static void main(String[] args) throws Exception {
        byte[] source = new byte[220_987];
        new java.util.Random(42).nextBytes(source);
        PngBuffer png = new PngBuffer();
        png.write(source, 0, 65_535);
        png.write(source[65_535]);
        png.write(source, 65_536, 0);
        png.write(source, 65_536, source.length - 65_536);
        for (int read = 0; read < 2; ++read) {
            ByteArrayOutputStream actual = new ByteArrayOutputStream();
            png.writeTo(actual);
            if (png.size() != source.length || !Arrays.equals(actual.toByteArray(), source))
                throw new AssertionError("Clipboard PNG bytes changed");
        }
        System.out.println("PngBufferTest OK");
    }
}
