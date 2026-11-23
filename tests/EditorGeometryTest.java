package ru.dot.clipshot;

public final class EditorGeometryTest {
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    private static void near(float a,float b) { check(Math.abs(a-b)<.002f,a+" != "+b); }
    public static void main(String[] args) {
        ImageTransform identity = new ImageTransform(120,200);
        ImageTransform r = identity.rotate();
        check(r.width == 200 && r.height == 120, "rotation not swapping dimensions");
        near(r.x(12,23),177); near(r.y(12,23),12);
        near(r.sourceX(177,12),12); near(r.sourceY(177,12),23);
        ImageTransform four = r.rotate().rotate().rotate();
        check(four.isIdentity(), "four rotations must be identity");
        ImageTransform mixed = r.flipX().rotate().flipY();
        for (int x=0;x<=120;x+=15) for (int y=0;y<=200;y+=20) {
            float u=mixed.x(x,y),v=mixed.y(x,y);
            near(mixed.sourceX(u,v),x); near(mixed.sourceY(u,v),y);
        }
        check(r.flipX().flipX().rotate().rotate().rotate().isIdentity(), "horizontal reflection not involutive");
        check(identity.flipY().flipY().isIdentity(), "vertical reflection not involutive");
        near(r.flipX().x(12,23),23); near(r.flipY().y(12,23),108);
        float[] out=new float[4];
        CropAspect.fit(200,120,100,64,200,120,16f/9f,out);
        near((out[2]-out[0])/(out[3]-out[1]),16f/9f);
        CropAspect.resize(200,120,25,20,153,92,CropAspect.RIGHT|CropAspect.BOTTOM,500,500,32,16f/9f,out);
        check(out[2]<=200 && out[3]<=120,"corner resize leaves the image");
        near(out[0],25);near(out[1],20);
        near((out[2]-out[0])/(out[3]-out[1]),16f/9f);
        CropAspect.resize(200,120,25,20,153,92,CropAspect.LEFT,100,0,32,16f/9f,out);
        near(out[2],153);near((out[1]+out[3])/2,56);
        near((out[2]-out[0])/(out[3]-out[1]),16f/9f);
        int[] pixels=new int[4];
        CropAspect.pixels(180,305,2.7f,3.4f,115.1f,204.2f,9,16,pixels);
        check((pixels[2]-pixels[0])*16==(pixels[3]-pixels[1])*9,"copy is not integer 9:16");
        int wide = CropFormats.indexFor(7,3);
        check(CropFormats.NAMES[wide].equals("21:9"), "missing ultrawide preset");
        int tall = CropFormats.rotate(wide);
        check(CropFormats.NAMES[tall].equals("9:21") && CropFormats.rotate(tall)==wide, "missing reciprocal");
        CropAspect.pixels(1080,2400,30,50,1010,2310,CropFormats.W[tall],CropFormats.H[tall],pixels);
        check((pixels[2]-pixels[0])*7==(pixels[3]-pixels[1])*3,"wrong ultrawide portrait export");
        // Reported 16:9 selection used to include four extra columns on both sides.
        CropAspect.pixels(1080,2400,100,200,524,438.5f,16,9,pixels);
        check(pixels[0]==104 && pixels[1]==202 && pixels[2]==520 && pixels[3]==436,
                "export must be the centered, contained 416x234 rectangle");
        float[][] frames = {{0,0,233.75f,229.5f},{60.5f,0,1080,429.25f},
                {327.3f,1800.4f,1080,2400},{0,1999.6f,222.8f,2400}};
        for (float[] frame : frames) {
            for (int i=0;i<CropFormats.W.length;i++) {
                int n=CropFormats.W[i], d=CropFormats.H[i];
                CropAspect.pixels(1080,2400,frame[0],frame[1],frame[2],frame[3],n,d,pixels);
                check(pixels[0]>=frame[0] && pixels[1]>=frame[1] &&
                        pixels[2]<=frame[2] && pixels[3]<=frame[3],"export exposes a pixel outside the frame");
                if (n>0) check((pixels[2]-pixels[0])*d==(pixels[3]-pixels[1])*n, "wrong export ratio");
            }
        }
        CropAspect.pixels(10,10,2.2f,3,2.8f,8,0,0,pixels);
        check(pixels[0]==pixels[2], "subpixel crop must not select an outside pixel");
        CropAspect.pixels(10,10,1,1,9,5,16,9,pixels);
        check(pixels[0]==pixels[2] && pixels[1]==pixels[3], "cannot fit an integer 16:9 crop");
        System.out.println("Orientation inverses, exact 360°, crop constraints and export ratio: OK");
    }
}
