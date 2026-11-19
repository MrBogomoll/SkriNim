package ru.dot.clipshot;

public final class CaptionStyleTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        int value=0;
        for (int flag : new int[]{CaptionStyle.BOLD,CaptionStyle.ITALIC,CaptionStyle.UNDERLINE,CaptionStyle.STRIKE}) {
            value=CaptionStyle.toggle(value,flag);
            check(CaptionStyle.has(value,flag));
        }
        check(CaptionStyle.face(value)==3);
        value=CaptionStyle.toggle(value,CaptionStyle.ITALIC);
        check(!CaptionStyle.has(value,CaptionStyle.ITALIC));
        check(CaptionStyle.face(value)==1);
        check(CaptionStyle.has(value,CaptionStyle.UNDERLINE));
        check(CaptionStyle.has(value,CaptionStyle.STRIKE));
        check(CaptionStyle.face(CaptionStyle.STRIKE)==0);
        System.out.println("CaptionStyleTest passed");
    }
}
