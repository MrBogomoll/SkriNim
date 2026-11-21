package ru.dot.clipshot;

import android.graphics.Bitmap;

public final class ScreenshotViewportTest {
    private static final int W = 360, H = 720;

    public static void main(String[] args) {
        Bitmap original = page(7), fresh = page(7);
        require(ScreenshotViewport.matches(original, fresh), "same viewport");
        // Clock, system bars and a small animation changed while the editor was open.
        fill(fresh, 0, 0, W, 55, 0xff343578);
        fill(fresh, 0, H-36, W, H, 0xff334532);
        fill(fresh, W/2, H/2, W/2+36, H/2+36, 0xff994422);
        require(ScreenshotViewport.matches(original, fresh), "minor visual changes");
        require(!ScreenshotViewport.matches(original, page(19)), "different text on same theme");
        // A closing shade can blur the original. Sparse lists are particularly
        // vulnerable because their recognizable content may occupy one column.
        Bitmap list = sparseList(0), softened = blur(list);
        require(ScreenshotViewport.matches(softened, list), "same sparse list after shade blur");
        require(!ScreenshotViewport.matches(softened, sparseList(7)), "different rows in same template");
        Bitmap shortList = narrowText(3);
        require(ScreenshotViewport.matches(shortList, narrowText(3)), "sparse text within one band");
        require(!ScreenshotViewport.matches(shortList, narrowText(17)), "different sparse text");
        Bitmap faded = narrowText(3);
        for (int y=0; y<H; y++) for (int x=0; x<W; x++) {
            int c = faded.getPixel(x,y);
            int r = 250 + ((c >> 16 & 255)-250)*2/3;
            int g = 250 + ((c >> 8 & 255)-250)*2/3;
            int b = 250 + ((c & 255)-250)*2/3;
            faded.setPixel(x,y,0xff000000 | (r << 16) | (g << 8) | b);
        }
        require(ScreenshotViewport.matches(faded, shortList), "same sparse glyphs at reduced contrast");
        require(!ScreenshotViewport.matches(faded, narrowText(17)), "different sparse glyphs at reduced contrast");
        Bitmap oldFeed = page(7), liveFeed = page(7);
        fill(oldFeed, 16, 232, W-16, 448, 0xff386b9b);
        fill(liveFeed, 16, 232, W-16, 448, 0xffa9b342);
        require(ScreenshotViewport.matches(oldFeed, liveFeed), "same page with changing media");
        Bitmap otherFeed = page(19);
        fill(otherFeed, 16, 232, W-16, 448, 0xffa9b342);
        require(!ScreenshotViewport.matches(oldFeed, otherFeed), "different feed using the same layout");
        Bitmap otherCard = page(7), firstCard = page(7);
        fill(otherCard, 0, 155, W, 566, 0xfffafafa);
        fill(firstCard, 0, 155, W, 566, 0xfffafafa);
        fill(firstCard, 16, 232, W-16, 448, 0xff386b9b);
        fill(otherCard, 16, 232, W-16, 448, 0xffa9b342);
        require(!ScreenshotViewport.matches(firstCard, otherCard), "only header and footer match");
        Bitmap flat = bitmap();
        flat.eraseColor(0xfff4f4f4);
        require(!ScreenshotViewport.matches(flat, flat), "flat screenshot has no visual identity");
        require(ScreenshotViewport.settled(flat, flat), "verified window can display an unchanged blank page");
        require(!ScreenshotViewport.settled(flat, original), "page still moving during rebase");
        fill(flat, 0, 0, W, 55, 0xff345632);
        fill(flat, 0, H-36, W, H, 0xff444444);
        fill(flat, 47, 50, 50, H-36, 0xff777777);
        require(!ScreenshotViewport.matches(flat, flat), "fixed chrome and divider");
        require(!ScreenshotViewport.matches(original, flat), "blank app replaced editor target");
        require(!ScreenshotViewport.matches(original, Bitmap.createBitmap(H,W,Bitmap.Config.ARGB_8888)),
                "different dimensions");
        require(!original.isRecycled() && !fresh.isRecycled(), "comparison must borrow both images");
        System.out.println("ScreenshotViewportTest passed");
    }

    private static Bitmap page(int seed) {
        Bitmap image = bitmap();
        image.eraseColor(0xfff4f4f4);
        // Pages share scaffold, margins and line spacing; only the glyph patterns differ.
        for (int y = 72; y < H-60; y++) for (int x=20; x<W-20; x++) {
            if (y%22<8 || x%12<2) continue;
            int v = (y/22)*1103515245 ^ (x/12)*4813423 ^ seed*905309;
            if ((v >>> ((x%12+y%22)%23) & 1) != 0) image.setPixel(x,y,0xff263144);
        }
        fill(image, 0, 0, W, 55, 0xff294763);
        fill(image, 0, H-36, W, H, 0xff444444);
        return image;
    }

    private static Bitmap sparseList(int offset) {
        Bitmap b = bitmap();
        b.eraseColor(0xfffafafa);
        for (int i = 0; i < 12; i++) {
            int y = 30 + i * 58;
            if (i % 2 == 0) fill(b, 0, y, W, y+58, 0xffedf1fa);
            int swatch = 0xff000000 | ((i + offset + 1) * 7925417 & 0xffffff);
            fill(b, 12, y+8, 46, y+44, swatch);
            for (int k=0; k<8; k++)
                fill(b, 55 + k*4, y+18, 57 + k*4, y+21+(k*i%7), 0xff26262b);
        }
        return b;
    }

    private static Bitmap narrowText(int seed) {
        Bitmap image = bitmap();
        image.eraseColor(0xfffafafa);
        for (int line=0; line<2; line++) for (int letter=0; letter<10; letter++) {
            int pattern = (seed + line*19 + letter*53)*1103515245;
            for (int y=0; y<9; y++) for (int x=0; x<5; x++)
                if ((pattern >>> ((x + y*7) % 29) & 1) != 0)
                    image.setPixel(39 + letter*7 + x, 270 + line*22 + y, 0xff232832);
        }
        return image;
    }

    private static Bitmap blur(Bitmap src) {
        Bitmap out = bitmap();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int r = 0, g = 0, b = 0, count = 0;
            for (int sy = Math.max(0, y-2); sy <= Math.min(H-1, y+2); sy++)
                for (int sx = Math.max(0, x-2); sx <= Math.min(W-1, x+2); sx++) {
                    int c = src.getPixel(sx, sy);
                    r += c >> 16 & 255;
                    g += c >> 8 & 255;
                    b += c & 255;
                    count++;
                }
            out.setPixel(x, y, 0xff000000 | (r/count << 16) | (g/count << 8) | b/count);
        }
        return out;
    }

    private static Bitmap bitmap() { return Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); }

    private static void fill(Bitmap image,int left,int top,int right,int bottom,int color) {
        for (int y=top;y<bottom;y++) for (int x=left;x<right;x++) image.setPixel(x,y,color);
    }

    private static void require(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
    }
}
