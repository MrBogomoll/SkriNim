package ru.dot.clipshot;

final class CropFormats {
    static final String[] NAMES = {"Свободно", "Оригинал", "1:1", "4:3", "3:4",
            "16:9", "9:16", "3:2", "2:3", "4:5", "5:4", "21:9", "9:21"};
    static final int[] W = {0,0,1,4,3,16,9,3,2,4,5,7,3};
    static final int[] H = {0,0,1,3,4,9,16,2,3,5,4,3,7};
    private CropFormats() {}
    static int indexFor(int width,int height) {
        for (int i=2;i<W.length;i++) if (W[i]==width && H[i]==height) return i;
        return -1;
    }
    static int rotate(int format) {
        if (format<2) return format;
        int rotated=indexFor(H[format],W[format]);
        return rotated>=0 ? rotated : format;
    }
}
