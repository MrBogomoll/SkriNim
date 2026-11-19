package ru.dot.clipshot;

import java.util.Locale;

final class WheelColor {
    private WheelColor() {}

    static void setFromPoint(float dx, float dy, float radius, float[] hsv) {
        if (radius <= 0 || !Float.isFinite(dx) || !Float.isFinite(dy)) return;
        if (dx != 0 || dy != 0) {
            float hue = (float) Math.toDegrees(Math.atan2(dy, dx));
            hsv[0] = hue < 0 ? hue + 360f : hue;
        }
        hsv[1] = (float) Math.min(1d, Math.hypot(dx, dy) / radius);
    }

    static Integer parseHex(CharSequence input) {
        String s = input.toString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return null;
        int value = 0;
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(i);
            int digit = c >= '0' && c <= '9' ? c - '0' :
                    c >= 'A' && c <= 'F' ? c - 'A' + 10 :
                    c >= 'a' && c <= 'f' ? c - 'a' + 10 : -1;
            if (digit < 0) return null;
            value = value * 16 + digit;
        }
        return 0xff000000 | value;
    }

    static int parseChannel(CharSequence input) {
        String s = input.toString().trim();
        if (s.length() == 0 || s.length() > 3) return -1;
        int value = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return -1;
            value = value * 10 + c - '0';
        }
        return value <= 255 ? value : -1;
    }

    static String hex(int color) { return String.format(Locale.ROOT, "#%06X", color & 0xffffff); }
}
