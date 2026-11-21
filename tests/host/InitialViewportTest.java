package ru.dot.clipshot;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class InitialViewportTest {
    public static void main(String[] args) {
        InitialViewport anchor = view(screen(0));
        check(!view(screen(0)).scrolledFrom(anchor), "still screenshot");
        check(view(screen(91)).scrolledFrom(anchor), "downward fling");
        check(view(screen(-38)).scrolledFrom(anchor), "upward fling");
        check(!view(screen(2300)).scrolledFrom(anchor), "unrelated viewport");
        ByteBuffer video = screen(0);
        for (int y=150; y<210; y++) for (int x=38; x<95; x++) video.putInt(12 + y * 512 + x * 4, 0xff6b1100);
        check(!view(video).scrolledFrom(anchor), "embedded video changed");
    }

    private static InitialViewport view(ByteBuffer frame) {
        return new InitialViewport(frame, 120, 480, 512);
    }

    private static ByteBuffer screen(int offset) {
        ByteBuffer frame = ByteBuffer.allocate(12 + 512 * 480).order(ByteOrder.LITTLE_ENDIAN);
        frame.position(12);
        for (int y=26; y<450; y++) for (int x=0; x<120; x++) {
            int token = (y + offset) * 1103515245 + x * 1664525;
            frame.putInt(12 + y * 512 + x * 4, 0xff000000 | (token ^ token >>> 16) & 0x00ffffff);
        }
        return frame;
    }

    private static void check(boolean ok, String test) {
        if (!ok) throw new AssertionError(test);
    }
}
