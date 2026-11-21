package android.graphics;

public class Canvas {
    private final Bitmap base;
    private Bitmap layer;
    public int bitmapDraws, rectDraws, lastShapeColor;
    public boolean lastShapeFilled;
    private void traceShape(Paint p) {
        lastShapeColor=p.color;
        lastShapeFilled=p.style==Paint.Style.FILL;
    }
    public Canvas(Bitmap bitmap) {base=bitmap;}
    public int saveLayer(Object r,Paint p){
        layer=Bitmap.createBitmap(base.width,base.height,Bitmap.Config.ARGB_8888);
        return 1;
    }
    public void restoreToCount(int n){
        Bitmap overlay=layer;
        layer=null;
        drawBitmap(overlay,0,0,null);
    }
    private Bitmap dest(){return layer==null?base:layer;}
    public void drawBitmap(Bitmap b,Rect src,Rect dst,Paint p){
        if (src==null) src=new Rect(0,0,b.width,b.height);
        if (dst.right-dst.left!=src.right-src.left || dst.bottom-dst.top!=src.bottom-src.top)
            throw new AssertionError("host canvas does not implement resampling");
        for(int y=src.top;y<src.bottom;y++) for(int x=src.left;x<src.right;x++)
            put(dst.left+x-src.left,dst.top+y-src.top,b.getPixel(x,y),null);
    }
    public void drawBitmap(Bitmap b,float x,float y,Paint p){
        bitmapDraws++;
        for(int cy=0;cy<b.height;cy++) for(int cx=0;cx<b.width;cx++)
            put((int)x+cx,(int)y+cy,b.pixels[cy*b.width+cx],null);
    }
    public void drawRect(float a,float b,float c,float d,Paint p){
        rectDraws++;
        traceShape(p);
        float edge=p.style==Paint.Style.FILL?0:p.width/2;
        for(int y=(int)(b-edge);y<d+edge;y++) for(int x=(int)(a-edge);x<c+edge;x++)
            if(p.style==Paint.Style.FILL||x+.5f<=a+edge||x+.5f>=c-edge||y+.5f<=b+edge||y+.5f>=d-edge)
                put(x,y,p.color,p.mode);
    }
    public void drawCircle(float x,float y,float r,Paint p){
        for(int cy=(int)Math.floor(y-r);cy<=y+r;cy++)
            for(int cx=(int)Math.floor(x-r);cx<=x+r;cx++)
                if((cx+.5f-x)*(cx+.5f-x)+(cy+.5f-y)*(cy+.5f-y)<=r*r) put(cx,cy,p.color,p.mode);
    }
    public void drawPath(Path path,Paint p) {traceShape(p);}
    public void drawOval(float a,float b,float c,float d,Paint p) {traceShape(p);}
    private void put(int x,int y,int c,BlendMode mode){
        Bitmap b=dest();
        if(x<0||x>=b.width||y<0||y>=b.height)return;
        int i=y*b.width+x;
        if(mode==BlendMode.CLEAR)b.pixels[i]=0;
        else if((c>>>24)!=0)b.pixels[i]=c;
    }
}
