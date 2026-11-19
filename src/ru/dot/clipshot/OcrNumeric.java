package ru.dot.clipshot;

import java.util.Arrays;

/** Geometry and filtering for digit-only additions to the original OCR result. */
final class OcrNumeric {
    private OcrNumeric() {}

    static boolean accept(String token,float confidence,int width,int height) {
        if (!(confidence >= 25f) || token == null || token.isEmpty() || token.length() > 32 ||
                width < 2 || height < 5) return false;
        int digits = 0, symbols = 0;
        for (int i = 0; i < token.length();) {
            int c = token.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isDigit(c)) digits++;
            else if (".,:/\\+-−×÷=()%％# ".indexOf(c) >= 0) symbols++;
            else return false;
        }
        return digits > 0 && symbols <= digits + 1 &&
                width <= height * (1.5f * digits + .85f * symbols);
    }

    static int[] append(int[] primary,int[] numeric) {
        if (numeric.length == 0) return primary;
        int[] output = Arrays.copyOf(primary, primary.length + numeric.length);
        int used = primary.length;
        for (int i = 0; i < numeric.length; i += 4) {
            int l = numeric[i], t = numeric[i + 1], r = numeric[i + 2], b = numeric[i + 3];
            if (l >= r || t >= b) continue;
            boolean occupied = false;
            long area = (long)(r-l) * (b-t);
            for (int j = 0; j < used; j += 4) {
                int w = Math.min(r,output[j+2]) - Math.max(l,output[j]);
                int h = Math.min(b,output[j+3]) - Math.max(t,output[j+1]);
                if (w <= 0 || h <= 0) continue;
                long other = (long)(output[j+2]-output[j]) * (output[j+3]-output[j+1]);
                if (3L * w * h >= Math.min(area,other)) { occupied = true; break; }
            }
            if (!occupied) {
                System.arraycopy(numeric,i,output,used,4);
                used += 4;
            }
        }
        return used == primary.length ? primary : Arrays.copyOf(output,used);
    }
}
