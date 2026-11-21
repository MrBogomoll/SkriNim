package ru.dot.clipshot;

public final class ScrollMotionTest {
    public static void main(String[] args) {
        ScrollMotion empty = ScrollMotion.empty();
        ScrollMotion first = empty.update("ScrollView", 7, 0, 105, 900, 5, -1, -1, -1);
        ScrollMotion next = first.update("ScrollView", 7, 0, 348, 900, 243, -1, -1, -1);
        expect(248, next.distanceFrom(empty, false)); // Coalesced deltas confirmed by absolute positions.
        expect(243, next.distanceFrom(first, false));
        expect(0, next.distanceFrom(first, true));
        ScrollMotion back = next.update("ScrollView", 7, 0, 106, 900, -242, -1, -1, -1);
        expect(242, back.distanceFrom(next, true));
        ScrollMotion unreported = empty.update("ScrollView", 7, 0, 100, 900, -1, -1, -1, -1);
        expect(0, unreported.distanceFrom(empty, true));
        expect(0, unreported.distanceFrom(empty, false));
        expect(0, next.distanceFrom(ScrollMotion.empty(), false)); // Another capture.
        expect(0, next.update("WebView", 7, 0, 400, 900, 52, -1, -1, -1).distanceFrom(next, false));
        expect(0, next.update("ScrollView", 8, 0, 400, 900, 52, -1, -1, -1).distanceFrom(next, false));
        expect(0, next.update("ScrollView", 7, 0, 400, 1100, 52, -1, -1, -1).distanceFrom(next, false));
        expect(0, next.update("ScrollView", 7, 30, 400, 900, 52, -1, -1, -1).distanceFrom(next, false));
        ScrollMotion bottom = next.update("ScrollView", 7, 0, 900, 900, 552, -1, -1, -1);
        if (bottom.hasMore(false) || !bottom.hasMore(true) || empty.hasMore(false)
                || !bottom.atEnd(false) || bottom.atEnd(true) || empty.atEnd(false))
            throw new AssertionError("invalid available range");
        ScrollMotion list = empty.update("RecyclerView", 5, 0, -1, -1, 0, 3, 8, 20);
        if (!list.hasMore(false) || !list.hasMore(true) || list.atEnd(false) || list.distanceFrom(empty, false) != 0)
            throw new AssertionError("visible item indices were mistaken for pixel distances");
        if (empty.update("RecyclerView", 5, 0, 0, 0, 0, 12, 19, 20).atEnd(false))
            throw new AssertionError("partly visible last item reported as end of page");
        System.out.println("ScrollMotionTest passed");
    }

    private static void expect(int expected, int actual) {
        if (actual != expected) throw new AssertionError("expected " + expected + ", got " + actual);
    }
}
