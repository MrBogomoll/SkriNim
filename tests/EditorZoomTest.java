package ru.dot.clipshot;

public final class EditorZoomTest {
    private static void eq(float want,float actual) {
        if (!(Math.abs(want-actual)<0.0001f)) throw new AssertionError(want+" != "+actual);
    }
    private static void yes(boolean condition) { if (!condition) throw new AssertionError(); }

    public static void main(String[] args) {
        // Midpoint moves from (70, 90) to (80, 100) while span doubles.
        EditorZoom zoom=new EditorZoom();
        zoom.update(180,160,10,20,200,200,70,90,80,100,2);
        eq(2,zoom.scale);
        eq(-40,zoom.left);
        eq(-40,zoom.top);
        eq(1f/3f,(80-zoom.left)/(180*zoom.scale));
        eq(.4375f,(100-zoom.top)/(160*zoom.scale));
        eq(100,zoom.left+(80-10)*(zoom.scale/1));
        zoom.update(180,160,zoom.left,zoom.top,200,200,80,100,130,120,1);
        eq(10,zoom.left); // Pan capped by the original outer padding.
        eq(-20,zoom.top);
        zoom.update(180,160,zoom.left,zoom.top,200,200,130,120,1,1,100);
        eq(8,zoom.scale);
        zoom.update(180,160,zoom.left,zoom.top,200,200,1,1,130,120,.001f);
        eq(1,zoom.scale);
        eq(10,zoom.left);
        eq(20,zoom.top);

        EditorZoom.Gesture gesture = new EditorZoom.Gesture();
        yes(!gesture.capture(true,false,1));
        yes(gesture.capture(false,false,2));
        yes(gesture.capture(false,false,1)); // Pointer-up with a remaining finger.
        yes(gesture.capture(false,true,1));  // Its final up must not draw or pick.
        yes(!gesture.capture(true,false,1)); // A fresh down can edit.
        yes(gesture.capture(false,false,3)); // Adding a third finger changes nothing.
        yes(gesture.capture(false,true,0));  // System cancellation is also consumed.
        yes(!gesture.capture(true,false,1));
        System.out.println("EditorZoomTest OK");
    }
}
