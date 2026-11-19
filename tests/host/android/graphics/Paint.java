package android.graphics;
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1, FILTER_BITMAP_FLAG = 2;
    public enum Cap {ROUND}
    public enum Join {ROUND}
    public enum Style {STROKE,FILL}
    int color;
    Style style=Style.FILL;
    float width;
    BlendMode mode;
    public Paint() {}
    public Paint(int flags) {}
    public void setStrokeCap(Cap x) {}
    public void setStrokeJoin(Join x) {}
    public void setStyle(Style x) { style=x; }
    public void setStrokeWidth(float x) { width=x; }
    public float getStrokeWidth() { return width; }
    public void setColor(int x) { color=x; }
    public void setBlendMode(BlendMode x) { mode=x; }
}
