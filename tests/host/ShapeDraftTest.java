package ru.dot.clipshot;

import android.graphics.Bitmap;
import android.graphics.Canvas;

public final class ShapeDraftTest {
    public static void main(String[] args) {
        Bitmap source=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
        Markup m=new Markup(source);
        int color=0x88aabbcc;
        int[] tools={Markup.OVAL,Markup.RECT,Markup.CIRCLE,Markup.SQUARE,Markup.HEART,
                Markup.DIAMOND,Markup.TRIANGLE,Markup.CHECK,Markup.CROSS};
        for (int tool : tools) {
            check(Markup.isShape(tool),"shape not recognized");
            m.begin(tool,20,10,color,3,8,null,true,ShapeGeometry.EDGE_TO_EDGE);
            check(m.takeShapeDraft()==null,"stationary shape should not persist");
            m.begin(tool,20,10,color,3,8,null,true,ShapeGeometry.EDGE_TO_EDGE);
            m.extend(60,50);
            Markup.ShapeDraft draft=m.takeShapeDraft();
            check(draft!=null,"missing draft for "+tool);
            check(!m.hasMarks(),"taking a draft prematurely committed it");
            check(draft.right>draft.left && draft.bottom>draft.top,"empty bounds");
            Canvas canvas=new Canvas(source);
            draft.draw(canvas);
            check(canvas.lastShapeColor==color,"style lost");
            check(canvas.lastShapeFilled!=Markup.isOpenShape(tool),"fill changed");
            float[] map={0,-2,100,2,0,0};
            float[] bounds=draft.bounds(map);
            Markup.ShapeDraft transformed=draft.transformed(map);
            near(bounds[0],transformed.left);
            near(bounds[1],transformed.top);
            near(bounds[2],transformed.right);
            near(bounds[3],transformed.bottom);
            near(draft.right-draft.left,(transformed.bottom-transformed.top)/2);
            near(transformed.strokePixels(),6);
            int restyledColor=0x446633ff;
            Markup.ShapeDraft restyled=draft.transformed(map,restyledColor,9,true);
            near(restyled.left,transformed.left); near(restyled.top,transformed.top);
            near(restyled.right,transformed.right); near(restyled.bottom,transformed.bottom);
            near(restyled.strokePixels(),9);
            restyled.draw(canvas);
            check(canvas.lastShapeColor==restyledColor,"live hue or alpha lost");
            check(canvas.lastShapeFilled!=Markup.isOpenShape(tool),"live fill excludes open shapes");
            Markup.ShapeDraft outline=draft.transformed(map,restyledColor,5,false);
            outline.draw(canvas);
            check(!canvas.lastShapeFilled,"cannot switch draft back to outline");
            near(outline.strokePixels(),5);
            m.addStamp(restyled);
            check(m.countStamps(Markup.ShapeDraft.class)==1,"lost stamp history");
            m.undo();
            check(m.countStamps(Markup.ShapeDraft.class)==0,"stamp count did not follow undo");
        }
        check(!Markup.isShape(Markup.STEP) && !Markup.isShape(Markup.CALLOUT),"pseudo tool is a shape");
        m.begin(Markup.TRIANGLE,0,0,color,3,8,null,true,ShapeGeometry.EDGE_TO_EDGE);
        m.extend(40,50);
        Markup.ShapeDraft triangle=m.takeShapeDraft();
        float[] flipped=triangle.bounds(new float[]{-1,0,40,0,1,0});
        near(flipped[0],0); near(flipped[1],0); near(flipped[2],40); near(flipped[3],50);
        System.out.println("ShapeDraftTest passed");
    }

    private static void near(float a,float b) { check(Math.abs(a-b)<.001f,a+" != "+b); }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
