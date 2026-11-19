package ru.dot.clipshot;

public final class ColorWheelTest {
    private static void near(float a,float b) { if(Math.abs(a-b)>.0001f) throw new AssertionError(a+" != "+b); }
    public static void main(String[] args) {
        float[] hsv={42,1,.6f};
        WheelColor.setFromPoint(40,0,40,hsv); near(hsv[0],0); near(hsv[1],1);
        WheelColor.setFromPoint(0,40,40,hsv); near(hsv[0],90);
        WheelColor.setFromPoint(-40,0,40,hsv); near(hsv[0],180);
        WheelColor.setFromPoint(0,-50,40,hsv); near(hsv[0],270); near(hsv[1],1);
        WheelColor.setFromPoint(0,0,40,hsv); near(hsv[0],270); near(hsv[1],0); near(hsv[2],.6f);
        if (WheelColor.parseHex("#fea012") != 0xfffea012 || WheelColor.parseHex("00Fe26") != 0xff00fe26
                || WheelColor.parseHex("#123") != null || WheelColor.parseHex("#10ggff") != null
                || WheelColor.parseHex("12345678") != null || !WheelColor.hex(0x12fea012).equals("#FEA012"))
            throw new AssertionError("hex parse/format");
        if (WheelColor.parseChannel("255") != 255 || WheelColor.parseChannel("000") != 0
                || WheelColor.parseChannel("256") != -1 || WheelColor.parseChannel("-1") != -1
                || WheelColor.parseChannel("") != -1 || WheelColor.parseChannel("3a") != -1)
            throw new AssertionError("RGB range/format");
        System.out.println("Color wheel hue, saturation, center and drag clamping OK");
    }
}
