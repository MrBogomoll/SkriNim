package ru.dot.clipshot;

/** Pixel positions reported by one scrolling widget during an explicit capture. */
final class ScrollMotion {
    private final Object series;
    private final String widget;
    private final int window, x, y, maxY, firstY;
    private final boolean earlierItems, laterItems;

    private ScrollMotion(Object series, String widget, int window, int x, int y, int maxY, int firstY,
                         boolean earlierItems, boolean laterItems) {
        this.series = series;
        this.widget = widget;
        this.window = window;
        this.x = x;
        this.y = y;
        this.maxY = maxY;
        this.firstY = firstY;
        this.earlierItems = earlierItems;
        this.laterItems = laterItems;
    }

    static ScrollMotion empty() {
        return new ScrollMotion(new Object(), null, -1, -1, -1, -1, -1, false, false);
    }

    ScrollMotion update(String type, int id, int nextX, int nextY, int maximum, int delta,
                        int firstItem, int lastItem, int itemCount) {
        if (type.isEmpty()) return empty();
        // Native lists can publish only the range of visible items. Their
        // indices say whether more content exists, never how many pixels moved.
        boolean itemRange = firstItem >= 0 && lastItem >= firstItem && itemCount > lastItem;
        boolean earlier = itemRange && firstItem > 0;
        boolean later = itemRange && lastItem < itemCount - 1;
        if (nextY < 0 || maximum <= 0 || nextY > maximum)
            return new ScrollMotion(new Object(), type, id, nextX, -1, -1, -1, earlier, later);
        boolean first = widget == null;
        boolean same = type.equals(widget) && window == id && x == nextX && maxY == maximum;
        int initial = firstY;
        if (!same) {
            // Some widgets publish the first gesture's full delta together with
            // the absolute position. An omitted delta is commonly -1 or zero.
            long before = (long) nextY - delta;
            initial = first && delta != 0 && delta != -1 && before >= 0 && before <= maximum
                    ? (int) before : -1;
        }
        return new ScrollMotion(first || same ? series : new Object(), type, id, nextX, nextY, maximum,
                initial, earlier, later);
    }

    /** Provides a candidate only. The two screenshots must still support its pixels. */
    int distanceFrom(ScrollMotion anchor, boolean upward) {
        if (anchor == null || anchor.series != series || y < 0) return 0;
        int origin = anchor.widget == null ? firstY : anchor.y;
        if (origin < 0) return 0;
        return Math.max(0, upward ? origin - y : y - origin);
    }

    boolean hasMore(boolean upward) {
        return upward ? earlierItems || y > 0 : laterItems || (y >= 0 && y < maxY);
    }

    boolean atEnd(boolean upward) {
        // The last visible item can still extend below the viewport. Only an
        // absolute pixel boundary can establish that a direction is exhausted.
        return maxY > 0 && (upward ? y == 0 && !earlierItems : y == maxY && !laterItems);
    }
}
