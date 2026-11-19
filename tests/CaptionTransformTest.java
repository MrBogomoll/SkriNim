package ru.dot.clipshot;

public final class CaptionTransformTest {
    private static void near(float value,float expected) {
        if (Math.abs(value-expected)>0.0001f) throw new AssertionError(value+" != "+expected);
    }

    public static void main(String[] ignored) {
        CaptionTransform t=new CaptionTransform(100,200);
        float[] m=new float[4];
        t.axes(1,0,0,1,m);
        near(m[0],1); near(m[1],0); near(m[2],0); near(m[3],1);
        t.angle=90;
        near(t.x(20,10),90);
        near(t.y(20,10),220);
        // Inverse source axes are reflected in x; the displayed glyph axes still have unit length.
        t.axes(-1,0,0,1,m);
        near(m[0],0); near(m[1],1); near(m[2],1); near(m[3],0);
        near(CaptionTransform.rotated(30,10,0,-10,10),120);
        near(CaptionTransform.rotated(30,10,0,-10,0),30);
        near(t.localX(0,30),30); near(t.localY(-20,0),20);
        if (t.hit(90,220,20,10,3)!=CaptionTransform.CORNER ||
            t.hit(100,220,20,10,3)!=CaptionTransform.X_EDGE ||
            t.hit(90,200,20,10,3)!=CaptionTransform.Y_EDGE ||
            t.hit(100,200,20,10,3)!=CaptionTransform.INSIDE ||
            t.hit(75,200,20,10,3)!=CaptionTransform.NONE)
            throw new AssertionError("Rotated corners/edges/interior");
        near(CaptionTransform.fontFromGrip(1,10,0,10,0,.2f,4),2);
        near(CaptionTransform.fontFromGrip(1,10,0,-10,10,.2f,4),1);
        near(CaptionTransform.fontFromGrip(1,10,0,-10,0,.2f,4),.2f);
        near(CaptionTransform.symbolBoxScale(1,100,80,50,40,200),1);
        near(CaptionTransform.symbolBoxScale(3,100,80,50,40,200),2);
        near(CaptionTransform.symbolBoxScale(.1f,100,80,50,40,200),.5f);
        near(CaptionTransform.symbolBoxScale(1,20,20,60,60,30),1.5f);
        near(CaptionTransform.symbolBoxScale(1,20,20,12,12,2),1);
        near(CaptionTransform.fit(200,60,90,120),.45f);
        String example="one\r\n  two\nthree";
        if (!CaptionTransform.textForLayout(example,false).equals("one two three") ||
            !CaptionTransform.textForLayout(example,true).equals(example))
            throw new AssertionError("Line policy");
        CaptionFrame box=new CaptionFrame(100,50,200,80);
        // Right edge: x/y text origin stays anchored even when narrow width grows the box downward.
        box.resize(100,50,200,80,120,210,1,0,0);
        near(box.width,120); near(box.height,210); near(box.cx,60); near(box.cy,115);
        box.heightFloor=210; near(box.heightFor(40),210); near(box.heightFor(250),250);
        box.resize(100,50,200,80,120,210,-1,0,90);
        near(box.cx,35); near(box.cy,90);
        box.resize(100,50,200,80,200,60,0,-1,0);
        near(box.cx,100); near(box.cy,60);
        t.move(500,-30,0,0,150,300);
        near(t.cx,150); near(t.cy,0);
        // A callout tip is absolute in oriented image coordinates. Converting
        // anew after a box move, rotation or reflow must yield the same tip.
        float tipX=156, tipY=239;
        for (float angle : new float[]{0,90,-33,135}) {
            t.angle=angle;
            for (int width : new int[]{20,60,125}) {
                t.move(100+width,90-width/4,0,0,400,400);
                float halfW=width/2f, halfH=(width+17)/3f;
                float u=t.boxLocalX(tipX,tipY,halfW)-halfW;
                float v=t.boxLocalY(tipX,tipY,halfH)-halfH;
                near(t.x(u,v),tipX);
                near(t.y(u,v),tipY);
            }
        }
        System.out.println("CaptionTransformTest passed");
    }
}
