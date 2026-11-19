package ru.dot.clipshot;

public final class ThicknessPlacementTest {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    private static void verifyFit(int left, int top, int right, int bottom, int anchorX, int anchorY) {
        ThicknessPlacement p = ThicknessPlacement.fit(left, top, right, bottom,
                anchorX, anchorY, 98, 237, 125, 6);
        check(p.x >= left && p.x + p.width <= right, "panel outside horizontal safe area");
        check(p.y >= top && p.y + p.height <= bottom, "panel outside vertical safe area");
        if (anchorY - top >= 131) {
            check(p.y + p.height <= anchorY - 6, "panel protrudes below anchor");
        }
    }

    public static void main(String[] args) {
        verifyFit(10, 32, 384, 852, 376, 714);
        verifyFit(48, 24, 811, 232, 808, 146);
        verifyFit(8, 14, 335, 310, 345, 171);
        verifyFit(10, 35, 90, 192, 85, 83);
        ThicknessPlacement normal = ThicknessPlacement.fit(10, 30, 390, 850,
                378, 716, 98, 237, 125, 6);
        check(normal.x == 280 && normal.y == 473 && normal.height == 237, "portrait placement changed");
        System.out.println("Thickness placement: fits portrait, landscape, and reduced windows");
    }
}
