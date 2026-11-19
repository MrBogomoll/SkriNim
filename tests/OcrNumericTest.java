package ru.dot.clipshot;

import java.util.Arrays;

public final class OcrNumericTest {
    public static void main(String[] args) {
        check(OcrNumeric.accept("7",25,13,20),"isolated digit");
        check(OcrNumeric.accept("09:43",80,65,20),"clock");
        check(OcrNumeric.accept("−089,219",85,95,20),"decimal");
        check(OcrNumeric.accept("１２",65,35,20),"unicode decimal digits");
        check(!OcrNumeric.accept("АВ-71",95,65,20),"alphanumeric noise");
        check(!OcrNumeric.accept("©6",85,15,20),"unconfirmed numeric glyph");
        check(!OcrNumeric.accept("––",80,31,10),"punctuation alone");
        check(!OcrNumeric.accept("7",15,12,20),"weak numeric");
        check(!OcrNumeric.accept("7",99,50,6),"line-shaped false positive");
        int[] text={4,4,15,15, 4,30,35,45};
        int[] numeric={5,5,12,15, 26,32,34,42, 16,4,20,15, 16,5,21,15, 42,30,48,45};
        check(Arrays.equals(OcrNumeric.append(text,numeric),
                new int[]{4,4,15,15, 4,30,35,45, 16,4,20,15, 42,30,48,45}),
                "primary geometry and order must be unchanged; deduplicate extras");
        check(OcrNumeric.append(text,new int[0])==text,"empty supplemental pass should preserve primary");
        System.out.println("Numeric OCR: digit filtering and primary-safe merge passed");
    }

    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
