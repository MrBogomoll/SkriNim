package ru.dot.clipshot;

import android.graphics.Bitmap;
import android.graphics.Canvas;

public final class CompositeMosaicTest {
    private static final int BASE=0xff202020,YELLOW=0xffffee00;
    private static Bitmap image(int w,int h){return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);}
    private static Bitmap render(Markup m,boolean preview){
        Bitmap out=image(64,64);
        out.eraseColor(BASE);
        m.draw(new Canvas(out),preview);
        return out;
    }
    private static void check(boolean ok,String issue){if(!ok)throw new AssertionError(issue);}
    public static void main(String[] args){
        Bitmap source=image(64,64);source.eraseColor(BASE);
        Markup m=new Markup(source);
        m.begin(Markup.PEN,12,12,YELLOW,10,0);m.finish();
        m.begin(Markup.MOSAIC,12,12,0,10,16);m.finish();
        Bitmap coarse=render(m,false);
        check(m.colorAt(12,12)==coarse.getPixel(12,12),"eyedropper ignored the mosaic");
        int avg=coarse.getPixel(8,8);
        check(avg!=BASE&&avg!=YELLOW,"mosaic ignored the yellow annotation");
        check(avg==coarse.getPixel(12,12)&&avg==coarse.getPixel(15,15),"expected one flat coarse block");
        m.begin(Markup.MOSAIC,12,12,0,6,4);
        check(render(m,true).getPixel(12,12)==avg,"fine pass revealed prior detail in preview");
        m.finish();
        check(render(m,false).getPixel(12,12)==avg,"fine pass revealed prior detail in output");
        m.begin(Markup.ERASER,11,11,0,6,0);m.finish();
        check(render(m,false).getPixel(11,11)==BASE,"eraser must reveal only the source");
        check(m.colorAt(11,11)==BASE,"eyedropper saw through eraser incorrectly");
        Markup mix=new Markup(source);
        mix.begin(Markup.PEN,16,16,0x80ff0000,4,0);mix.finish();
        check(mix.colorAt(16,16)==0xff901010,"eyedropper did not blend translucent ink");
        check(mix.colorAt(-3,100)==BASE,"eyedropper edge clamp");
        Markup figures=new Markup(source);
        figures.begin(Markup.RECT,4,4,YELLOW,2,16,null,true);
        figures.extend(24,24);
        check(render(figures,true).getPixel(10,10)==YELLOW,"filled shape preview left an empty center");
        figures.finish();
        figures.begin(Markup.RECT,30,30,YELLOW,2,16,null,false);
        figures.extend(50,50);figures.finish();
        check(figures.colorAt(40,40)==BASE,"outline unexpectedly filled its center");
        check(figures.colorAt(30,40)==YELLOW,"outline lost its border");
        check(figures.colorAt(10,10)==YELLOW,"style change modified an existing shape");
        figures.undo();
        check(figures.colorAt(10,10)==YELLOW,"undo replay lost filled shape style");
        figures.begin(Markup.ERASER,10,10,0,6);figures.finish();
        check(figures.colorAt(10,10)==BASE,"eraser did not remove the fill");
        figures.undo();
        check(figures.colorAt(10,10)==YELLOW,"undo did not restore erased fill");
        for (int tool : new int[]{Markup.OVAL,Markup.CIRCLE,Markup.SQUARE,Markup.HEART}) {
            Markup preview=new Markup(source);
            preview.begin(tool,4,4,0x80ffee00,2,16,null,true);
            preview.extend(24,24);
            Canvas trace=new Canvas(image(64,64));
            preview.draw(trace,true);
            check(trace.lastShapeFilled&&trace.lastShapeColor==0x80ffee00,
                    "shape should preview its fill with the original alpha: "+tool);
        }
        Markup radial=new Markup(source);
        radial.begin(Markup.CIRCLE,20,20,YELLOW,2,16,null,true);
        radial.extend(30,20);
        Canvas round=new Canvas(image(64,64));
        radial.draw(round,true);
        check(round.lastShapeFilled,"horizontal radius failed to preview a circle");
        radial.finish();
        check(radial.hasMarks(),"horizontal radius failed to commit a circle");
        radial.begin(Markup.CIRCLE,20,20,YELLOW,2,16,null,true);
        radial.extend(25,25);
        radial.extend(20,20);
        radial.finish();
        radial.undo();
        check(!radial.hasMarks(),"a circle dragged back to zero should not add an undo step");
        Bitmap stripe=image(3,2);
        stripe.setPixel(0,0,0xffff0000);
        stripe.setPixel(1,0,0xff00ff00);
        stripe.setPixel(0,1,0xff0000ff);
        Markup stripeMarkup=new Markup(stripe);
        ImageTransform rotated=new ImageTransform(3,2).rotate();
        int[] loupe=new int[9];
        check(stripeMarkup.colorGrid(rotated,1,0,3,loupe)==0xffff0000,"rotated top-right pixel selected incorrectly");
        check(loupe[4]==0xffff0000&&loupe[3]==0xff0000ff&&loupe[7]==0xff00ff00,
                "loupe neighbors did not rotate with visible image");
        check(loupe[5]==loupe[4]&&loupe[1]==loupe[4],"loupe should clamp the edge");
        check(stripeMarkup.colorGrid(rotated.flipX(),0,0,3,loupe)==0xffff0000,
                "loupe selected wrong pixel after horizontal reflection");
        check(m.colorGrid(new ImageTransform(64,64),14,14,3,loupe)==avg,
                "preview center must show committed mosaic colors");
        int previousReads=source.reads;
        for(int i=0;i<10;i++){
            Canvas frame=new Canvas(image(64,64));m.draw(frame,true);
            check(frame.rectDraws==0,"a frame replayed old mosaic blocks");
            check(frame.bitmapDraws<=1,"expected one flattened committed overlay");
        }
        check(source.reads==previousReads,"cached frames reread source pixels");
        m.undo();
        check(render(m,false).getPixel(11,11)==avg,"undo did not restore mosaic");
        Bitmap large=image(304,160);large.eraseColor(BASE);
        Markup big=new Markup(large);
        big.begin(Markup.MOSAIC,8,80,0,110,5);
        big.extend(295,80);
        int samples=large.reads;
        check(samples>1000,"test did not cover a wide brush with fine blocks");
        Canvas frame=new Canvas(image(304,160));
        big.draw(frame,true);
        check(frame.rectDraws==0&&frame.bitmapDraws<=6,"active frame replayed tiles instead of chunks");
        check(large.reads==samples,"preview reread active blocks");
        big.extend(8,80);
        check(large.reads==samples,"repeat of the same path resampled tiles");
        big.finish();
        Canvas flat=new Canvas(image(304,160));
        big.draw(flat,false);
        check(flat.bitmapDraws==1&&flat.rectDraws==0,"large stroke wasn't flattened");
        System.out.println("Composite mosaic: annotations included, no detail recovery, cached draws and undo");
    }
}
