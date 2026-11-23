package ru.dot.clipshot;

public final class TiltGeometryTest {
    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        float[] b = new float[4];
        int[] pixels = new int[4];
        int[][] images = {{1080,2400},{2400,1080},{1125,1125},{997,2011},{4013,911}};
        for (int[] size : images) {
            int w=size[0], h=size[1];
            TiltGeometry zero=new TiltGeometry(0);
            zero.fit(w,h,(float)w/h,w,h,b);
            check(b[0]==0 && b[1]==0 && b[2]==w && b[3]==h,"zero preserves whole image");
            for(float angle : new float[]{-45,-30,-13,-1.25f,-.001f,.001f,1.25f,13,30,45}) {
                TiltGeometry tilt=new TiltGeometry(angle);
                for (int preset=0;preset<CropFormats.W.length;preset++) {
                    int n=CropFormats.W[preset], d=CropFormats.H[preset];
                    float ratio=n==0?(float)w/h:(float)n/d;
                    tilt.fit(w,h,ratio,w,h,b);
                    check(Math.abs((b[2]-b[0])/(b[3]-b[1])-ratio)<.0001,
                            "preview preserves ratio");
                    check(tilt.fits(w,h,b[0],b[1],b[2],b[3]),"unsafe preview frame");
                    check(!tilt.fits(w,h,b[0]-.3f,b[1]-.3f,b[2]+.3f,b[3]+.3f), "frame was not maximal");
                    tilt.pixels(w,h,b[0],b[1],b[2],b[3],n,d,pixels);
                    check(pixels[0]>=b[0] && pixels[1]>=b[1] &&
                            pixels[2]<=b[2] && pixels[3]<=b[3], "tilted export escapes visible crop");
                    if (n>0) check((pixels[2]-pixels[0])*d==(pixels[3]-pixels[1])*n,"rounded export ratio");
                    check(pixels[2]-pixels[0]>10 && pixels[3]-pixels[1]>10,"unexpected export collapse");
                    for (int x : new int[]{pixels[0],pixels[2]}) {
                        for (int y : new int[]{pixels[1],pixels[3]}) {
                            float u=tilt.inverseX(x,y,w,h), v=tilt.inverseY(x,y,w,h);
                            check(u>=-.0005 && v>=-.0005 && u<=w+.0005 && v<=h+.0005,
                                    "export corner outside source: "+angle+", "+u+", "+v);
                        }
                    }
                    float l=b[0],t=b[1],r=b[2],down=b[3];
                    float delta=(float)w*.3f;
                    b[0]+=delta; b[2]+=delta;
                    tilt.constrain(w,h,l,t,r,down,b);
                    check(tilt.fits(w,h,b[0],b[1],b[2],b[3]),"drag escaped source");
                    check(Math.abs((b[2]-b[0])/(b[3]-b[1])-ratio)<.0001,"drag lost ratio");
                }
            }
        }
        new TiltGeometry(12).pixels(1080,2400,400.25f,700,400.75f,710,0,0,pixels);
        check(pixels[0]==pixels[2], "tiny tilted frame must not expose an unrelated pixel");
        System.out.println("TiltGeometry: centered max size, anchored drag and integer crop safety passed");
    }
}
