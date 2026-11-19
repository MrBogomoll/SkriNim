package ru.dot.clipshot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.SweepGradient;
import android.view.View;

final class PaletteIcon extends View {
    private static final int[] RAINBOW={0xffff5454,0xffffda4d,0xff74ce55,0xff36c9d8,0xff6882ff,0xffce65df,0xffff5454};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path dropper=new Path();
    private final boolean wheel;
    private final Look look;
    private SweepGradient ring;
    private int color=0;
    private boolean showColor;

    PaletteIcon(Context host,Look look,boolean wheel) {
        super(host);
        this.look=look;
        this.wheel=wheel;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dropper.moveTo(-8,7); dropper.lineTo(-6,2); dropper.lineTo(1,-5);
        dropper.lineTo(0,-6); dropper.lineTo(2,-8); dropper.lineTo(4,-6);
        dropper.lineTo(6,-8); dropper.cubicTo(8,-10,11,-7,9,-5);
        dropper.lineTo(7,-3); dropper.lineTo(9,-1); dropper.lineTo(7,1);
        dropper.lineTo(5,-1); dropper.lineTo(-2,6); dropper.lineTo(-8,7); dropper.close();
    }

    void setPreview(int color,boolean visible) {
        this.color=color; showColor=visible; invalidate();
    }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        if (wheel) ring=new SweepGradient(w/2f,h/2f,RAINBOW,null);
    }

    @Override protected void onDraw(Canvas canvas) {
        float cx=getWidth()/2f,cy=getHeight()/2f,scale=Math.min(cx,cy)/13f;
        if (wheel) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4*scale);
            paint.setShader(ring);
            canvas.drawCircle(cx,cy,8.8f*scale,paint);
            paint.setShader(null);
            if (showColor) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(look.border);
                canvas.drawCircle(cx,cy,5.6f*scale,paint);
                paint.setColor(color);
                canvas.drawCircle(cx,cy,4.6f*scale,paint);
            }
        } else {
            int save=canvas.save();
            canvas.translate(cx,cy);
            canvas.scale(scale,scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.65f);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(showColor?look.accent:look.text);
            canvas.drawPath(dropper,paint);
            canvas.restoreToCount(save);
        }
    }
}
