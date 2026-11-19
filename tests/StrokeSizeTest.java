package ru.dot.clipshot;

public final class StrokeSizeTest {
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
    public static void main(String[] args) {
        for (int side : new int[]{320, 720, 1080, 1220, 1440, 3840}) {
            StrokeSize size = new StrokeSize(side);
            check(StrokeSize.atY(size.min, size.max, 20f, 20f, 180f) == size.max);
            check(StrokeSize.atY(size.min, size.max, 180f, 20f, 180f) == size.min);
            check(StrokeSize.atY(size.min, size.max, -30f, 20f, 180f) == size.max);
            check(StrokeSize.atY(size.min, size.max, 500f, 20f, 180f) == size.min);
            for (int width = size.min; width <= size.max; width++) {
                float y = 180f - size.fraction(width) * 160f;
                check(StrokeSize.atY(size.min, size.max, y, 20f, 180f) == width);
                check(size.fromProgress(size.progress(width)) == width);
            }
            check(size.fromLegacy(2) == Math.round(Math.max(2f, side * 0.0048f)));
            check(size.fromLegacy(0) < size.fromLegacy(4));
            StrokeSize oldCaption = new StrokeSize(side,8f);
            StrokeSize caption = StrokeSize.caption(side);
            check(caption.min==oldCaption.min && caption.max==5*oldCaption.max);
            check(StrokeSize.captionWidth(side,-1,240)==oldCaption.fromProgress(240));
            check(StrokeSize.captionWidth(side,oldCaption.max+11,240)==oldCaption.max+11);
            check(StrokeSize.sliderValue(caption.min,caption.max,oldCaption.max,0)==caption.min);
            check(StrokeSize.sliderValue(caption.min,caption.max,oldCaption.max,1)==caption.max);
            check(StrokeSize.sliderValue(caption.min,caption.max,oldCaption.max,.6f)==oldCaption.max);
            check(StrokeSize.sliderFraction(caption.min,caption.max,oldCaption.max,oldCaption.max)==.6f);
            StrokeSize oldEraser = new StrokeSize(side, 6f);
            StrokeSize eraser = StrokeSize.eraser(side);
            check(eraser.min == oldEraser.min);
            check(eraser.max == 3 * oldEraser.max);
            check(StrokeSize.atY(eraser.min, eraser.max, 20f, 20f, 180f) == eraser.max);
            for (int saved : new int[]{0, 300, 750, 1000}) {
                check(StrokeSize.eraserWidth(side, -1, saved) == oldEraser.fromProgress(saved));
                check(StrokeSize.eraserWidth(side, saved, 0) == eraser.fromProgress(saved));
            }
        }
        System.out.println("Stroke diameter range, migration and vertical drag: OK");
    }
}
