package ru.dot.clipshot;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

/** A mark, rather than a drag-defined shape. Size is always proportional to the fixed glyph. */
final class MarkSymbolStamp implements Markup.Stamp {
    static final float CHECK_ASPECT=2f/1.63f;
    private final Matrix intoSource=new Matrix();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();

    MarkSymbolStamp(int tool,int color,float radius,float weight,float x,float y,float[] axes,boolean fitNominalBounds) {
        intoSource.setValues(new float[]{axes[0],axes[1],x,axes[2],axes[3],y,0,0,1});
        boolean cross=tool==Markup.CROSS;
        float[] points=cross?ShapeGeometry.CROSS_POINTS:ShapeGeometry.CHECK_POINTS;
        float stroke=Math.max(1,Math.min(radius*.48f,weight));
        if (fitNominalBounds) stroke=Math.min(stroke,radius*1.63f);
        float pathHalfW=fitNominalBounds?Math.max(0,radius-stroke*.5f):radius;
        float pathHalfH=fitNominalBounds&&!cross?
                Math.max(0,radius-stroke/1.63f):pathHalfW;
        float offset=cross?0:.055f; // Center the check's actual vertical extent.
        for (int i=0;i<points.length;i+=2) {
            float u=points[i]*pathHalfW,v=(points[i+1]+offset)*pathHalfH;
            if (i==0 || (cross && i==4)) path.moveTo(u,v);
            else path.lineTo(u,v);
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(stroke);
    }

    @Override public void draw(Canvas canvas) {
        int saved=canvas.save();
        canvas.concat(intoSource);
        canvas.drawPath(path,paint);
        canvas.restoreToCount(saved);
    }
}
