package ru.dot.clipshot;

/** A caption's box in oriented image pixels. Glyph size is not a property of the box. */
final class CaptionFrame {
    float cx,cy,width,height,heightFloor;

    CaptionFrame(float x,float y,float w,float h) {
        cx=x; cy=y; width=w; height=h;
    }
    float heightFor(float layoutHeight) { return Math.max(heightFloor,layoutHeight); }

    // Keep the opposite edge fixed. Automatic height changes from wrapping grow downward.
    void resize(float x,float y,float w,float h,float nextW,float nextH,int sideX,int sideY,float angle) {
        double r=Math.toRadians(angle);
        float dx=(nextW-w)/2*(sideX<0?-1:1);
        float dy=(nextH-h)/2*(sideY<0?-1:1);
        cx=x+(float)(dx*Math.cos(r)-dy*Math.sin(r));
        cy=y+(float)(dx*Math.sin(r)+dy*Math.cos(r));
        width=nextW; height=nextH;
    }
}
