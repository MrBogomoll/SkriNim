package ru.dot.clipshot;

public final class InkOpacityTest {
    public static void main(String[] args) {
        expect(0xff1476e7, InkOpacity.apply(0xff1476e7, 100));
        expect(0x801476e7, InkOpacity.apply(0xff1476e7, 50));
        expect(0x001476e7, InkOpacity.apply(0xff1476e7, 0));
        expect(0x001476e7, InkOpacity.apply(0xff1476e7, -1));
        expect(0xff1476e7, InkOpacity.apply(0xff1476e7, 103));
        System.out.println("Ink opacity: clamps percentage and preserves color");
    }
    private static void expect(int expected, int actual) {
        if (expected != actual) throw new AssertionError(Integer.toHexString(actual));
    }
}
