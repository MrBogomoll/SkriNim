package ru.dot.clipshot;

/** The four independent style bits belong to the current draft. */
final class CaptionStyle {
    static final int BOLD=1, ITALIC=2, UNDERLINE=4, STRIKE=8;
    static boolean has(int value,int flag) { return (value&flag)!=0; }
    static int toggle(int value,int flag) { return value^flag; }
    // The low two bits use the same values as Android's Typeface.NORMAL/BOLD/ITALIC.
    static int face(int value) { return value&(BOLD|ITALIC); }
    private CaptionStyle() {}
}
