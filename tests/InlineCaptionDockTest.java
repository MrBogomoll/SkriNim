package ru.dot.clipshot;

public final class InlineCaptionDockTest {
    public static void main(String[] args) {
        near(InlineCaptionDock.shift(240,160,60,0,40,610),0);
        near(InlineCaptionDock.shift(750,160,60,0,40,610),-170);
        near(InlineCaptionDock.shift(750,160,60,90,40,610),-220);
        near(InlineCaptionDock.shift(30,160,60,0,40,610),40);
        near(InlineCaptionDock.shift(750,160,60,90,500,600),-200);
        near(InlineCaptionDock.shift(750,160,60,90,610,40),0);
        near(InlineCaptionDock.bottom(800,800,300,22,0,500,48,2),302);
        near(InlineCaptionDock.bottom(800,800,0,22,24,532,48,2),294);
        near(InlineCaptionDock.bottom(500,500,0,24,0,500,48,2),26);
        near(InlineCaptionDock.bottom(300,300,280,0,0,20,48,2),252);
        near(InlineCaptionDock.bottom(300,300,0,20,100,0,48,2),22);
        near(InlineCaptionDock.bottom(500,800,300,0,0,500,48,2),2);
        near(InlineCaptionDock.bottom(620,800,300,0,0,505,48,2),122);
        System.out.println("InlineCaptionDockTest passed");
    }

    private static void near(float actual,float expected) {
        if (Math.abs(actual-expected)>.001f)
            throw new AssertionError(actual+" != "+expected);
    }
}
