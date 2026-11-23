package ru.dot.clipshot;

public final class UpdateSignersTest {
    public static void main(String[] args) {
        check(UpdateSigners.SAME, certs("A"), certs("A"), certs("A"));
        check(UpdateSigners.INCOMPATIBLE, certs("A"), certs("B"), certs("B"));
        check(UpdateSigners.ROTATED, certs("A"), certs("C"), certs("A", "B", "C"));
        check(UpdateSigners.ROTATED, certs("B"), certs("C"), certs("A", "B", "C"));
        check(UpdateSigners.INCOMPATIBLE, certs("C"), certs("B"), certs("A", "B"));
        check(UpdateSigners.INCOMPATIBLE, certs("C"), certs("D"), certs("A", "B", "D"));
        check(UpdateSigners.SAME, certs("A", "B"), certs("B", "A"), null);
        check(UpdateSigners.INCOMPATIBLE, certs("A", "B"), certs("A", "C"), null);
        check(UpdateSigners.INCOMPATIBLE, certs("A", "B"), certs("A"), certs("A"));
        check(UpdateSigners.INCOMPATIBLE, certs("A"), certs("A", "B"), null);
        check(UpdateSigners.UNKNOWN, null, certs("A"), certs("A"));
        check(UpdateSigners.UNKNOWN, certs("A"), certs(), null);
        check(UpdateSigners.UNKNOWN, certs("A"), certs("B"), null);
        check(UpdateSigners.UNKNOWN, certs("A"), certs("B"), certs("A"));
        System.out.println("UpdateSignersTest passed");
    }

    private static String[] certs(String... names) { return names; }

    private static void check(UpdateSigners expected, String[] installed, String[] candidate, String[] history) {
        UpdateSigners actual = UpdateSigners.compare(installed, candidate, history);
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
