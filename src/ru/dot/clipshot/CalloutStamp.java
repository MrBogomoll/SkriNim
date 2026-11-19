package ru.dot.clipshot;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

/** A caption, outline and leader in one reusable source-space annotation. */
final class CalloutStamp implements Markup.Stamp {
    private final CaptionStamp text;
    private final Matrix intoSource = new Matrix();
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private float padding, fontScale;

    CalloutStamp(CaptionStamp text,int strokeColor,float stroke,float width,float height,float padding,
                 float tipX,float tipY,float fontScale) {
        this.text = text;
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        configure(strokeColor, stroke, width, height, padding, tipX, tipY, fontScale);
    }

    /** Reconfigure a draft only; callers must not mutate the committed instance. */
    void configure(int strokeColor,float stroke,float width,float height,float padding,
                   float tipX,float tipY,float fontScale) {
        width = Math.max(1f, width);
        height = Math.max(1f, height);
        this.padding = Math.max(0f, padding);
        this.fontScale = Math.max(.01f, fontScale);
        ink.setColor(strokeColor);
        ink.setStrokeWidth(Math.max(.5f, stroke));
        outline.rewind();
        outline.moveTo(0f, 0f);
        outline.lineTo(width, 0f);
        outline.lineTo(width, height);
        outline.lineTo(0f, height);
        outline.close();

        // Outside the box, the nearest point is on the tapped side or corner.
        // Suppress the leader within the box so it never crosses the caption.
        float jointX = Math.max(0f, Math.min(width, tipX));
        float jointY = Math.max(0f, Math.min(height, tipY));
        if (jointX == tipX && jointY == tipY) return;
        float[] head = ArrowGeometry.head(jointX,jointY,tipX,tipY,ink.getStrokeWidth());
        if (head == null) return;
        outline.moveTo(jointX,jointY);
        outline.lineTo(tipX,tipY);
        outline.moveTo(head[0],head[1]);
        outline.lineTo(tipX,tipY);
        outline.lineTo(head[2],head[3]);
    }

    void place(float sourceX,float sourceY,float[] axes) {
        intoSource.setValues(new float[]{axes[0],axes[1],sourceX,axes[2],axes[3],sourceY,0,0,1});
    }

    void drawLocal(Canvas canvas) {
        drawLocal(canvas,true);
    }

    void drawLocal(Canvas canvas,boolean drawText) {
        canvas.drawPath(outline,ink);
        if (!drawText) return;
        int saved = canvas.save();
        try {
            canvas.translate(padding,padding);
            if (fontScale != 1f) canvas.scale(fontScale,fontScale);
            text.drawLocal(canvas);
        } finally {
            canvas.restoreToCount(saved);
        }
    }

    @Override public void draw(Canvas canvas) {
        int saved = canvas.save();
        try {
            canvas.concat(intoSource);
            drawLocal(canvas);
        } finally {
            canvas.restoreToCount(saved);
        }
    }
}
