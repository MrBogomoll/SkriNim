package ru.dot.clipshot;

public final class EditorToolbarMetricsTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        int standard=56+52+48+72;
        check(EditorToolbarMetrics.preferred(1)==standard);
        check(EditorToolbarMetrics.preferred(2.625f)==
              Math.round(56*2.625f)+Math.round(52*2.625f)+Math.round(48*2.625f)+Math.round(72*2.625f));
        check(EditorToolbarMetrics.viewport(600,standard,120)==standard);
        check(EditorToolbarMetrics.viewport(348,standard,120)==standard);
        check(EditorToolbarMetrics.viewport(300,standard,120)==180);
        check(EditorToolbarMetrics.viewport(200,standard,120)==100);
        check(EditorToolbarMetrics.viewport(100,standard,120)==50);
        check(EditorToolbarMetrics.viewport(0,standard,120)==0);
        check(EditorToolbarMetrics.viewport(600,standard,120)==EditorToolbarMetrics.viewport(601,standard,120));
        System.out.println("EditorToolbarMetricsTest passed");
    }
}
