package ru.dot.clipshot;

public final class CaptureGeometryTest {
    public static void main(String[] args) {
        CaptureGeometry full = CaptureGeometry.resolve(1220,2712,1220,2712,0,90,0,66,false);
        check(full != null && full.left == 0 && full.top == 0, "regular full-display capture");
        CaptureGeometry app = CaptureGeometry.resolve(1220,2712,1220,2556,0,90,0,66,true);
        check(app != null && app.top == 90 && app.displayX(.5f) == 610
                && app.displayY(.5f) == 1368, "maximized app missing both bars");
        CaptureGeometry bottom = CaptureGeometry.resolve(1220,2712,1220,2646,0,90,0,66,true);
        check(bottom != null && bottom.top == 0, "edge-to-edge under status bar");
        CaptureGeometry cutout = CaptureGeometry.resolve(2712,1220,2556,1220,90,0,66,0,true);
        check(cutout != null && cutout.left == 90 && cutout.displayX(.5f) == 1368,
                "landscape cutout and side navigation");
        check(CaptureGeometry.resolve(800,800,800,752,0,48,0,48,true)==null,
                "cannot tell which of the two equal bars was cropped");
        check(CaptureGeometry.resolve(1220,2712,1220,2556,0,90,0,66,false)==null,
                "no foreground full-screen activity to authorize cropped geometry");
        check(CaptureGeometry.resolve(1220,2712,610,2600,0,90,0,66,true)==null,"split window");
        check(CaptureGeometry.resolve(1220,2712,1160,2630,0,90,0,66,true)==null,"freeform size");
        check(CaptureGeometry.resolve(1220,2712,1220,1300,0,1412,0,0,true)==null,"implausible keyboard-sized inset");
        System.out.println("CaptureGeometryTest passed");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
