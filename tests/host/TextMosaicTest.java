package ru.dot.clipshot;

import android.graphics.Bitmap;
import android.graphics.Canvas;

public final class TextMosaicTest {
    private static final int GRAY = 0xff202020, WHITE = 0xffffffff, RED = 0xffff0000;
    private static Bitmap image() {
        Bitmap b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        b.eraseColor(GRAY);
        return b;
    }
    private static Bitmap render(Bitmap source, Markup m, boolean active) {
        Bitmap b = image();
        Canvas canvas = new Canvas(b);
        canvas.drawBitmap(source, 0, 0, null);
        m.draw(canvas, active);
        return b;
    }
    private static void start(Markup m,int x,int y,int block,int[] boxes) {
        m.begin(Markup.MOSAIC,x,y,0,1,block,null,false,ShapeGeometry.CENTER_OUT,boxes);
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    public static void main(String[] args) {
        Bitmap source = image();
        source.setPixel(7,7,WHITE);
        source.setPixel(20,6,WHITE);
        source.setPixel(16,16,WHITE);
        source.setPixel(6,7,RED);
        int[] words = {7,7,11,11, 10,7,14,11, 20,6,24,10, 16,16,19,19};
        Markup m = new Markup(source);
        start(m,2,20,8,words);m.extend(8,20);m.finish();
        check(!m.hasMarks(),"miss must not add an undo step");
        start(m,7,7,8,new int[0]);m.finish();
        check(!m.hasMarks(),"empty OCR must not fall back to ordinary mosaic");
        start(m,0,0,8,words);m.extend(18,18);
        Bitmap active = render(source,m,true);
        int fullWord = active.getPixel(7,7);
        check(fullWord!=WHITE&&fullWord!=GRAY,"detected word was not averaged");
        check(active.getPixel(9,10)==fullWord,"entire hit word must be changed");
        check(active.getPixel(6,7)==RED,"left exterior written");
        check(active.getPixel(14,10)==GRAY,"exclusive right outside was written");
        check(active.getPixel(20,6)==WHITE,"unhit word near diagonal incorrectly selected");
        check(active.getPixel(16,16)!=WHITE,"end of long brush segment was skipped");
        int samples=source.reads;
        m.extend(0,0);
        check(source.reads==samples,"retracing stroke resampled hit words");
        m.finish();
        check(m.colorAt(7,7)==fullWord,"preview differs from commit");
        m.begin(Markup.ERASER,7,7,0,2);m.finish();
        check(m.colorAt(7,7)==WHITE,"eraser failed to uncover the original pixel");
        m.undo();
        check(m.colorAt(7,7)==fullWord,"undo failed to replay text mosaic");
        m.undo();
        check(!m.hasMarks()&&m.colorAt(7,7)==WHITE,"undo failed to remove text mosaic");
        start(m,9,8,8,words);m.extend(12,8);
        Bitmap overlap=render(source,m,true);
        m.finish();
        m.begin(Markup.PEN,2,30,RED,2);m.finish();m.undo();
        check(m.colorAt(10,8)==overlap.getPixel(10,8),"overlapping word replay changed preview colors");
        m.undo();
        m.begin(Markup.MOSAIC,7,7,0,1,8);m.finish();
        check(m.colorAt(6,7)!=RED,"ordinary mosaic no longer covers surrounding pixels");
        System.out.println("TextMosaicTest passed");
    }
}
