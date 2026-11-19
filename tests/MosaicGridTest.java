package ru.dot.clipshot;

public final class MosaicGridTest {
    private static void check(boolean ok, String error) {
        if (!ok) throw new AssertionError(error);
    }
    public static void main(String[] args) {
        MosaicGrid grid = new MosaicGrid(61, 55, 8);
        check(grid.isEmpty(), "new mask must be empty");
        grid.cover(4, 12, 57, 43, 7);
        for (int x = 4; x <= 57; ++x) {
            float y = 12 + (x - 4) * (31f / 53);
            check(grid.covers(x, (int)y), "gap in fast stroke at " + x);
            check(grid.covers(x, (int)y + 4), "part of brush radius missed");
        }
        check(!grid.covers(55, 4), "unrelated pixels changed");
        MosaicGrid tap = new MosaicGrid(61, 55, 8);
        tap.cover(60, 54, 60, 54, 3);
        check(tap.covers(60, 54), "edge tap missed");
        check(!tap.covers(60, 55), "out of bounds coverage");
        check(MosaicGrid.average(new int[]{0xffff0000, 0xff0000ff}, 2) == 0xff800080,
                "blocks must average real RGB rather than using a neutral palette");
        check(MosaicGrid.average(new int[]{0xfff80000, 0xfff80000, 0xfffffff0}, 2) == 0xfff80000,
                "pixels outside the current block must not enter its average");
        check(MosaicGrid.maxCell(1220) > MosaicGrid.minCell(1220), "no adjustable scale");
        System.out.println("Mosaic grid: coverage, edge tap, and real block color OK");
    }
}
