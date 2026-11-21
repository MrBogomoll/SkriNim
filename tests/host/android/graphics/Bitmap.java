package android.graphics;

public class Bitmap {
    public enum Config {ARGB_8888}
    public static final int DENSITY_NONE=0;
    public int reads;
    private boolean recycled;
    public void recycle(){recycled=true;}
    public boolean isRecycled(){return recycled;}
    public static Bitmap createBitmap(Bitmap src,int x,int y,int w,int h){
        if (x==0 && y==0 && w==src.width && h==src.height) return src;
        Bitmap b=new Bitmap(w,h);
        src.getPixels(b.pixels,0,w,x,y,w,h);
        return b;
    }
    final int width, height;
    final int[] pixels;
    private Bitmap(int w, int h) {width=w;height=h;pixels=new int[w*h];}
    public static int allocationHeightLimit = Integer.MAX_VALUE;
    public static Bitmap createBitmap(int w,int h,Config cfg) {
        if (h>allocationHeightLimit) throw new OutOfMemoryError("test allocation limit");
        return new Bitmap(w,h);
    }
    public void setDensity(int density) {}
    public int getWidth(){return width;}
    public int getHeight(){return height;}
    public void eraseColor(int color){java.util.Arrays.fill(pixels,color);}
    public int getPixel(int x,int y){return pixels[y*width+x];}
    public void setPixel(int x,int y,int color){pixels[y*width+x]=color;}
    public void getPixels(int[] out,int offset,int stride,int x,int y,int w,int h){
        reads++;
        for(int r=0;r<h;r++) System.arraycopy(pixels,(y+r)*width+x,out,offset+r*stride,w);
    }
}
