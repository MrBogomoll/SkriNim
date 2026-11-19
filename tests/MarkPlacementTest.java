package ru.dot.clipshot;

public final class MarkPlacementTest {
    private static void same(float a,float b) {
        if (Math.abs(a-b)>.001f) throw new AssertionError(a+" != "+b);
    }
    public static void main(String[] args) {
        CaptionTransform pose=MarkPlacement.place(312.75f,127.5f);
        same(pose.cx,312.75f);
        same(pose.cy,127.5f);
        same(pose.angle,0);
        same(pose.x(10,0),322.75f);
        same(pose.y(10,0),127.5f);
        same(MarkPlacement.radius(1080,2400),31.32f);
        same(MarkPlacement.radius(2400,1080),31.32f);
        same(MarkPlacement.radius(280,820),13);
        same(MarkPlacement.radius(1080,2400,0),31.32f);
        same(MarkPlacement.radius(280,820,-5),13f);
        same(MarkPlacement.radius(1080,2400,20),10f);
        same(MarkPlacement.radius(820,280,20),10f);
        same(MarkPlacement.radius(1080,2400,21),10.5f);
        same(MarkPlacement.radius(1080,2400,1),.5f);
        same(MarkPlacement.radius(1080,2400,Integer.MAX_VALUE),MarkPlacement.MAX_INITIAL_WIDTH_PX/2f);
        same(MarkPlacement.handleHalfExtent(3,18),18);
        same(MarkPlacement.handleHalfExtent(19,18),19);
        same(MarkPlacement.cornerScale(1,3,18),1);
        same(MarkPlacement.cornerScale(1.1f,3,18),1.6f);
        same(MarkPlacement.cornerScale(.9f,3,18),.4f);
        same(MarkPlacement.cornerScale(.2f,3,18),.05f);
        same(MarkPlacement.cornerScale(1.5f,18,18),1.5f);
        same(MarkPlacement.cornerScale(Float.NaN,3,18),1);
        System.out.println("MarkPlacementTest passed");
    }
}
