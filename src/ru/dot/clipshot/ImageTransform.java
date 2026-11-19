package ru.dot.clipshot;

/** Exact source-to-editor transform, with integer pixel boundaries. */
final class ImageTransform {
    final int width, height;
    private final int a, b, c, d, tx, ty;

    ImageTransform(int width, int height) { this(width,height,1,0,0,1,0,0); }
    private ImageTransform(int w,int h,int a,int b,int c,int d,int tx,int ty) {
        width=w; height=h; this.a=a; this.b=b; this.c=c; this.d=d; this.tx=tx; this.ty=ty;
    }
    ImageTransform rotate() { return new ImageTransform(height,width,-c,-d,a,b,height-ty,tx); }
    ImageTransform flipX() { return new ImageTransform(width,height,-a,-b,c,d,width-tx,ty); }
    ImageTransform flipY() { return new ImageTransform(width,height,a,b,-c,-d,tx,height-ty); }
    float x(float x,float y) { return a*x+b*y+tx; }
    float y(float x,float y) { return c*x+d*y+ty; }
    float sourceX(float x,float y) { return a*(x-tx)+c*(y-ty); }
    float sourceY(float x,float y) { return b*(x-tx)+d*(y-ty); }
    boolean isIdentity() { return a==1 && b==0 && c==0 && d==1 && tx==0 && ty==0; }
    float[] matrixValues() { return new float[]{a,b,tx,c,d,ty,0,0,1}; }
}
