package ru.dot.clipshot;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;

/** A numbered badge burned into the same source-space layer as the other annotations. */
final class StepStamp implements Markup.Stamp {
    private final Matrix intoSource = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final String label;
    private final float radius, baseline, extraWeight;
    private final int color, textColor, haloColor;

    StepStamp(int number, int color, int weight, float radius, float sourceX, float sourceY, float[] axes) {
        label = Integer.toString(number);
        this.radius = Math.max(.5f, radius);
        this.color = color;
        intoSource.setValues(new float[]{axes[0],axes[1],sourceX,axes[2],axes[3],sourceY,0,0,1});
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        extraWeight=this.radius*.12f*InkOpacity.clamp(weight)/100f;
        paint.setTextSize(this.radius * 1.27f);
        float width = paint.measureText(label), maxWidth = this.radius * 1.45f-extraWeight;
        if (width > maxWidth) paint.setTextSize(paint.getTextSize() * maxWidth / width);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        baseline = -(metrics.ascent + metrics.descent) / 2f;
        double luminance = .2126 * linear((color >>> 16) & 255)
                + .7152 * linear((color >>> 8) & 255) + .0722 * linear(color & 255);
        textColor = luminance > .179 ? 0xff161616 : 0xffffffff;
        haloColor = textColor == 0xffffffff ? 0xa8000000 : 0xa8ffffff;
    }

    private static double linear(int channel) {
        double s = channel / 255d;
        return s <= .04045 ? s / 12.92 : Math.pow((s + .055) / 1.055, 2.4);
    }

    @Override public void draw(Canvas canvas) {
        int alpha=color>>>24;
        if (alpha==0) return;
        int saved = canvas.save();
        try {
            canvas.concat(intoSource);
            if (alpha<255) {
                float bound=radius+1;
                canvas.saveLayerAlpha(-bound,-bound,bound,bound,alpha);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color|0xff000000);
            canvas.drawCircle(0f,0f,radius,paint);
            if (alpha<224) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(extraWeight+Math.max(.65f, Math.min(radius*.09f,paint.getTextSize()*.11f)));
                paint.setColor(haloColor);
                canvas.drawText(label,0f,baseline,paint);
            }
            paint.setStyle(extraWeight>0?Paint.Style.FILL_AND_STROKE:Paint.Style.FILL);
            paint.setStrokeWidth(extraWeight);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(textColor);
            canvas.drawText(label,0f,baseline,paint);
        } finally {
            canvas.restoreToCount(saved);
        }
    }
}
