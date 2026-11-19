package ru.dot.clipshot;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextDirectionHeuristics;

/** One annotation in source pixels, including the editor axes at the moment it was placed. */
final class CaptionStamp implements Markup.Stamp {
    static final int MAX_CHARS = 240;
    private StaticLayout layout;
    private float width;
    private int layoutWidth=-1;
    private final TextPaint ink;
    private final String value;
    final boolean wrap;
    final int naturalWidth;
    private final Matrix intoSource = new Matrix();

    CaptionStamp(String text, int color, float pixels, int width, boolean wrap,int style) {
        this.wrap=wrap;
        ink = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        ink.setColor(color);
        ink.setTextSize(pixels);
        ink.setTypeface(typeface(style));
        ink.setUnderlineText(CaptionStyle.has(style,CaptionStyle.UNDERLINE));
        ink.setStrikeThruText(CaptionStyle.has(style,CaptionStyle.STRIKE));
        value=CaptionTransform.textForLayout(text,wrap);
        naturalWidth=Math.max(1,(int)Math.ceil(Layout.getDesiredWidth(value,ink))+2);
        reflow(width);
    }

    static Typeface typeface(int style) {
        return Typeface.create("sans-serif-medium",CaptionStyle.face(style));
    }

    // Only a draft is allowed to reflow. An applied stamp is never handed back to the editor.
    void reflow(int width) {
        int limit=wrap?Math.max(1,width):naturalWidth;
        if (limit==layoutWidth) return;
        layoutWidth=limit;
        layout=StaticLayout.Builder.obtain(value,0,value.length(),ink,limit)
                .setTextDirection(TextDirectionHeuristics.LTR)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true)
                .setMaxLines(wrap?Integer.MAX_VALUE:1).build();
        this.width=1;
        for (int i=0;i<layout.getLineCount();i++)
            this.width=Math.max(this.width,layout.getLineRight(i));
    }

    int height() { return layout.getHeight(); }
    float width() { return width; }

    void place(float sourceX,float sourceY,float[] axes) {
        intoSource.setValues(new float[]{axes[0],axes[1],sourceX,axes[2],axes[3],sourceY,0,0,1});
    }

    void drawLocal(Canvas canvas) {
        layout.draw(canvas);
    }

    @Override public void draw(Canvas canvas) {
        int saved=canvas.save();
        canvas.concat(intoSource);
        drawLocal(canvas);
        canvas.restoreToCount(saved);
    }
}
