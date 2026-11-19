package ru.dot.clipshot;

import android.graphics.Bitmap;
import android.graphics.Paint;

public final class CaptionLayerTest {
    public static void main(String[] args) {
        int background=0xffeeeeee, ink=0xff2345ab;
        Bitmap image=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
        image.eraseColor(background);
        Markup markup=new Markup(image);
        Paint paint=new Paint();
        paint.setColor(ink);
        markup.addStamp(canvas -> canvas.drawRect(8,10,24,20,paint));
        if (!markup.hasMarks() || markup.colorAt(15,15)!=ink || markup.colorAt(3,3)!=background)
            throw new AssertionError("caption was not composited on the annotation layer");
        markup.begin(Markup.ERASER,15,15,0,4); markup.finish();
        if (markup.colorAt(15,15)!=background) throw new AssertionError("caption is not erasable");
        markup.undo();
        if (markup.colorAt(15,15)!=ink) throw new AssertionError("caption vanished after undo replay");
        markup.undo();
        if (markup.hasMarks() || markup.colorAt(15,15)!=background) throw new AssertionError("undo left a caption");
        System.out.println("Caption layer: commit, eraser, replay and undo OK");
    }
}
