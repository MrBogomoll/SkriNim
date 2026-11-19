package ru.dot.clipshot;

public final class ShapeGeometryTest {
    public static void main(String[] args) {
        ShapeGeometry.Bounds b = new ShapeGeometry.Bounds();
        for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
            b.set(20, 20, 20 + sx * 10, 20 + sy * 6, false);
            assertNear(b.right - b.left, 10, "free width");
            assertNear(b.bottom - b.top, 6, "free height");
            b.set(20, 20, 20 + sx * 10, 20 + sy * 6, true);
            assertNear(b.right - b.left, 6, "equal width");
            assertNear(b.bottom - b.top, 6, "equal height");
            assertNear(sx == 1 ? b.left : b.right, 20, "anchor x");
            assertNear(sy == 1 ? b.top : b.bottom, 20, "anchor y");
        }
        b.set(20, 20, 30, 20, true);
        if (b.nonempty()) throw new AssertionError("flat drag should not create a box");
        b.setCircle(20, 20, 30, 20);
        if (!b.nonempty()) throw new AssertionError("horizontal radius must create a circle");
        assertNear(b.left, 10, "left radius");
        assertNear(b.top, 10, "top radius");
        assertNear(b.right, 30, "right radius");
        assertNear(b.bottom, 30, "bottom radius");
        b.setCircle(20, 20, 17, 24);
        assertNear(b.left, 15, "diagonal radius");
        assertNear(b.bottom, 25, "diagonal diameter");
        b.setCircle(20, 20, 20, 20);
        if (b.nonempty()) throw new AssertionError("zero radius must not create a circle");
        assertRegularGestures(b);
        assertPolygonGestures();
        assertFrameGestures();
        assertNativeSymbolFrame();
        float[] points = ShapeGeometry.heartPoints();
        if (points.length < 128) throw new AssertionError("smooth outline needs enough points");
        assertNear(points[0], .5f, "notch x");
        assertNear(points[1], .25f, "notch y");
        assertNear(points[points.length / 2], .5f, "tip x");
        assertNear(points[points.length / 2 + 1], 1f, "tip y");
        for (int i = 0; i < points.length; i += 2) {
            float x = points[i], y = points[i + 1];
            if (x < 0 || x > 1 || y < 0 || y > 1) throw new AssertionError("point outside normalized heart");
            int mirror = (points.length - i) % points.length;
            assertNear(x + points[mirror], 1f, "symmetric x");
            assertNear(y, points[mirror+1], "symmetric y");
        }
        System.out.println("ShapeGeometryTest passed");
    }

    private static void assertRegularGestures(ShapeGeometry.Bounds b) {
        final int edge = ShapeGeometry.EDGE_TO_EDGE, out = ShapeGeometry.CENTER_OUT, in = ShapeGeometry.EDGE_IN;
        b.setCircle(20, 20, 30, 20, edge);
        assertBounds(b, 20, 15, 30, 25, "circle horizontal diameter");
        b.setCircle(20, 30, 20, 20, edge);
        assertBounds(b, 15, 20, 25, 30, "circle vertical diameter reverse");
        b.setCircle(20, 20, 26, 28, edge);
        assertBounds(b, 18, 19, 28, 29, "circle diagonal diameter");
        b.setCircle(26, 28, 20, 20, edge);
        assertBounds(b, 18, 19, 28, 29, "circle diagonal diameter reverse");
        b.setCircle(20, 20, 20, 10, out);
        assertBounds(b, 10, 10, 30, 30, "circle center outward vertical");
        b.setCircle(20, 20, 14, 12, out);
        assertBounds(b, 10, 10, 30, 30, "circle center outward diagonal");
        b.setCircle(14, 12, 20, 20, in);
        assertBounds(b, 10, 10, 30, 30, "circle edge inward diagonal");
        b.setCircle(30, 20, 20, 20, in);
        assertBounds(b, 10, 10, 30, 30, "circle edge inward horizontal");
        b.setSquare(20, 20, 26, 28, edge);
        assertBounds(b, 20, 20, 26, 26, "square corner outward");
        b.setSquare(26, 28, 20, 20, edge);
        assertBounds(b, 20, 22, 26, 28, "square reverse corner stays anchored");
        b.setSquare(20, 20, 30, 20, edge);
        if (b.nonempty()) throw new AssertionError("horizontal corner drag must remain empty");
        b.setSquare(20, 20, 20, 30, edge);
        if (b.nonempty()) throw new AssertionError("vertical corner drag must remain empty");
        b.setSquare(20, 20, 26, 28, out);
        assertBounds(b, 12, 12, 28, 28, "square center outward diagonal");
        b.setSquare(20, 20, 10, 20, out);
        assertBounds(b, 10, 10, 30, 30, "square center outward horizontal");
        b.setSquare(26, 28, 20, 20, in);
        assertBounds(b, 12, 12, 28, 28, "square edge inward diagonal reverse");
        b.setSquare(20, 30, 20, 20, in);
        assertBounds(b, 10, 10, 30, 30, "square edge inward vertical");
        for (int gesture : new int[]{edge,out,in}) {
            b.setSquare(20, 20, 20, 20, gesture);
            if (b.nonempty()) throw new AssertionError("empty square drag: " + gesture);
            b.setCircle(20, 20, 20, 20, gesture);
            if (b.nonempty()) throw new AssertionError("empty circle drag: " + gesture);
        }
    }

    private static void assertPolygonGestures() {
        float[] v = new float[8];
        final int edge = ShapeGeometry.EDGE_TO_EDGE, out = ShapeGeometry.CENTER_OUT, in = ShapeGeometry.EDGE_IN;
        for (boolean diamond : new boolean[]{false, true}) {
            for (int mode : new int[]{edge, out, in}) {
                for (boolean free : new boolean[]{false, true}) {
                    if (ShapeGeometry.regular(5, 8, 5, 8, mode, diamond, free, null, 0f, v))
                        throw new AssertionError("zero vector produced polygon");
                    if (!ShapeGeometry.regular(5, 8, 19, 8, mode, diamond, free, null, 0f, v))
                        throw new AssertionError("horizontal vector must produce polygon");
                    assertEquilateral(v);
                    assertGestureAnchors(v, 5, 8, 19, 8, mode, diamond);
                }
                ShapeGeometry.regular(20, 30, 14, 22, mode, diamond, true, null, 0f, v);
                assertEquilateral(v);
                assertGestureAnchors(v, 20, 30, 14, 22, mode, diamond);
            }
        }

        // Snapped screen alignment survives a 90-degree orientation, flip and arbitrary editor tilt.
        ImageTransform o = new ImageTransform(120, 200).rotate().flipX();
        float ax = 30, ay = 40, bx = 44, by = 51, tilt = 17;
        double radians = Math.toRadians(tilt);
        for (boolean diamond : new boolean[]{false,true}) {
            ShapeGeometry.regular(ax, ay, bx, by, out, diamond, false, o, tilt, v);
            for (int i = 0; i < 8; i += 2) {
                float ox = o.x(v[i],v[i+1]), oy = o.y(v[i],v[i+1]);
                v[i] = (float)(ox * Math.cos(radians) - oy * Math.sin(radians));
                v[i+1] = (float)(ox * Math.sin(radians) + oy * Math.cos(radians));
            }
            float dx = diamond ? v[0]-v[4] : v[0]-v[2];
            float dy = diamond ? v[1]-v[5] : v[1]-v[3];
            if (Math.min(Math.abs(dx),Math.abs(dy)) > .0001f)
                throw new AssertionError("not aligned to the visible editor axes");
        }
    }

    /** Front/back points are square edge midpoints or diamond tips. */
    private static void assertGestureAnchors(float[] v, float ax, float ay, float bx, float by,
                                             int mode, boolean diamond) {
        float frontX = diamond ? v[0] : (v[0]+v[2])/2f;
        float frontY = diamond ? v[1] : (v[1]+v[3])/2f;
        float backX = diamond ? v[4] : (v[4]+v[6])/2f;
        float backY = diamond ? v[5] : (v[5]+v[7])/2f;
        float centerX = (frontX+backX)/2f, centerY = (frontY+backY)/2f;
        assertNear(mode == ShapeGeometry.CENTER_OUT ? centerX : backX, ax, "first x");
        assertNear(mode == ShapeGeometry.CENTER_OUT ? centerY : backY, ay, "first y");
        assertNear(mode == ShapeGeometry.EDGE_IN ? centerX : frontX, bx, "last x");
        assertNear(mode == ShapeGeometry.EDGE_IN ? centerY : frontY, by, "last y");
    }

    private static void assertEquilateral(float[] v) {
        float length = segmentLength(v,0,2);
        for (int i = 2; i < 8; i += 2)
            assertNear(segmentLength(v,i,(i+2)%8), length, "same side length");
        float diag = segmentLength(v,0,4);
        assertNear(diag, segmentLength(v,2,6), "same diagonal length");
        assertNear(diag, (float)Math.sqrt(2)*length, "right angles");
    }

    private static float segmentLength(float[] points,int a,int b) {
        return (float)Math.hypot(points[a]-points[b],points[a+1]-points[b+1]);
    }

    private static void assertFrameGestures() {
        int edge=ShapeGeometry.EDGE_TO_EDGE, out=ShapeGeometry.CENTER_OUT, in=ShapeGeometry.EDGE_IN;
        ShapeGeometry.Frame f=new ShapeGeometry.Frame();
        f.set(15,20,5,4,edge,false,false,null,0);
        assertFramePoint(f,-1,-1,5,4,"axis box minimum");
        assertFramePoint(f,1,1,15,20,"axis box maximum");
        f.set(10,8,15,20,out,false,false,null,0);
        assertFramePoint(f,0,0,10,8,"out center");
        assertFramePoint(f,1,1,15,20,"out extent");
        f.set(15,20,10,8,in,false,false,null,0);
        assertFramePoint(f,0,0,10,8,"in center");
        assertFramePoint(f,1,1,15,20,"in extent");
        f.set(10,8,19,8,out,false,false,null,0);
        assertFramePoint(f,1,1,19,14,"centered axial fallback");
        f.set(0,0,6,8,edge,true,false,null,0);
        assertFramePoint(f,-1,0,0,0,"free first midpoint");
        assertFramePoint(f,1,0,6,8,"free last midpoint");
        assertNear((float)Math.hypot(f.sourceX(0,1)-f.sourceX(0,-1),
                f.sourceY(0,1)-f.sourceY(0,-1)),20f/3f,"free 3:2 proportion");
        f.set(0,0,6,8,out,true,true,null,0);
        assertFramePoint(f,0,1,6,8,"free heart tip follows outward drag");
        f.set(6,8,0,0,in,true,true,null,0);
        assertFramePoint(f,0,1,6,8,"free heart tip follows initial edge");
        if (f.set(6,8,6,8,edge,true,true,null,0)) throw new AssertionError("zero free frame");
        ImageTransform orientation=new ImageTransform(30,40).rotate().flipY();
        f.set(8,7,22,21,edge,false,true,orientation,17);
        double angle=Math.toRadians(17);
        float baseX=f.sourceX(-1,1), baseY=f.sourceY(-1,1);
        float farX=f.sourceX(1,1), farY=f.sourceY(1,1);
        float dx=orientation.x(farX,farY)-orientation.x(baseX,baseY);
        float dy=orientation.y(farX,farY)-orientation.y(baseX,baseY);
        assertNear((float)(dx*Math.sin(angle)+dy*Math.cos(angle)),0,"heart aligned in rotated screen");
    }

    private static void assertFramePoint(ShapeGeometry.Frame f,float u,float v,float x,float y,String label) {
        assertNear(f.sourceX(u,v),x,label+" x");
        assertNear(f.sourceY(u,v),y,label+" y");
    }

    private static void assertNativeSymbolFrame() {
        ShapeGeometry.Frame f = new ShapeGeometry.Frame();
        ShapeGeometry.Frame reversed = new ShapeGeometry.Frame();
        final int edge = ShapeGeometry.EDGE_TO_EDGE;
        for (boolean free : new boolean[]{false,true}) {
            for (int mode : new int[]{edge,ShapeGeometry.CENTER_OUT,ShapeGeometry.EDGE_IN}) {
                for (int[] end : new int[][]{{10,10},{10,0},{0,10},{-9,2}}) {
                    if (!f.set(0,0,end[0],end[1],mode,free,false,null,0f,true))
                        throw new AssertionError("axial or diagonal symbol did not draw");
                    float ux = f.sourceX(1,0)-f.sourceX(0,0), uy = f.sourceY(1,0)-f.sourceY(0,0);
                    float vx = f.sourceX(0,1)-f.sourceX(0,0), vy = f.sourceY(0,1)-f.sourceY(0,0);
                    assertNear((float)Math.hypot(ux,uy),(float)Math.hypot(vx,vy),"native 1:1 glyph frame");
                    assertNear(ux*vx + uy*vy,0,"perpendicular native axes");
                    if (!(ux*vy - uy*vx > 0)) throw new AssertionError("glyph mirrored");
                }
            }
            f.set(1,2,11,9,edge,free,false,null,0f,true);
            reversed.set(11,9,1,2,edge,free,false,null,0f,true);
            assertFramePoint(reversed,1,1,f.sourceX(1,1),f.sourceY(1,1),"reverse remains upright");
            if (f.set(0,0,0,0,edge,free,false,null,0f,true))
                throw new AssertionError("tap alone must not create symbol");
        }
    }

    private static void assertBounds(ShapeGeometry.Bounds b, float l, float t, float r, float d, String label) {
        assertNear(b.left, l, label + ": left");
        assertNear(b.top, t, label + ": top");
        assertNear(b.right, r, label + ": right");
        assertNear(b.bottom, d, label + ": bottom");
    }

    private static void assertNear(float actual, float expected, String label) {
        if (Math.abs(actual - expected) > .0001f)
            throw new AssertionError(label + ": " + actual + ", expected " + expected);
    }
}
