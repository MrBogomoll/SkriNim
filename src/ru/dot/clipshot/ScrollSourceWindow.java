package ru.dot.clipshot;

/** Associates the visually verified first frame with its foreground window, when reported. */
final class ScrollSourceWindow {
    private final String homePackage;
    private String owner;
    private int windowId = -1;
    private int generation;
    private boolean confirmed, fullscreenSource;
    private boolean swiping;

    ScrollSourceWindow(String homePackage) { this.homePackage = homePackage; }

    int generation() { return generation; }

    boolean confirmed() { return confirmed; }

    boolean hasFullscreenSource() { return owner != null && fullscreenSource; }

    boolean acceptsScroll(String reportedOwner, int reportedWindow) {
        return owner != null && owner.equals(reportedOwner)
                && (windowId < 0 || reportedWindow < 0 || windowId == reportedWindow);
    }

    boolean matchesIdentity(String reportedOwner, int reportedWindow) {
        return reportedWindow >= 0 && reportedWindow == windowId && reportedOwner.equals(owner);
    }

    /** A scrolling widget can announce the source even without a new foreground activity event. */
    void noteScroll(String reportedOwner, int reportedWindow) {
        if (swiping || confirmed || reportedWindow < 0 || reportedOwner == null
                || reportedOwner.equals(homePackage) || reportedOwner.equals("com.android.systemui")) return;
        if (owner == null || (owner.equals(reportedOwner) && windowId < 0)) {
            owner = reportedOwner;
            windowId = reportedWindow;
            generation++;
        }
    }

    boolean confirm(int frameGeneration) {
        if (swiping || frameGeneration != generation) return false;
        confirmed = true;
        return true;
    }

    boolean startSwiping(int frameGeneration) {
        if (!confirmed || frameGeneration != generation) return false;
        swiping = true;
        return true;
    }

    /** Returns true when a verified page may have lost the destination of its next swipe. */
    boolean changed(String nextOwner, int nextWindowId, boolean fullScreen) {
        // Home or the notification shade can be the last platform announcement
        // even when returning to an app silently. Never bind either as its owner.
        if (nextOwner != null && (nextOwner.equals(homePackage) || nextOwner.equals("com.android.systemui"))) {
            nextOwner = null;
            fullScreen = true;
        }
        if (nextOwner == null && !fullScreen) return false;
        if (nextOwner != null && nextOwner.equals(owner)) {
            if (fullScreen) fullscreenSource = true;
            if (windowId < 0) windowId = nextWindowId;
            if (!fullScreen || nextWindowId < 0 || windowId == nextWindowId) return false;
        }
        // Without a reported owner at confirmation, the next foreground change
        // could be Home or another app. Never bind that new event retrospectively.
        if (swiping) return true;
        // During the transition out of the editor, even the launcher can appear
        // momentarily. Only commit the current candidate after checking the pixels.
        owner = nextOwner;
        fullscreenSource = nextOwner != null && fullScreen;
        windowId = nextWindowId;
        confirmed = false;
        generation++;
        return false;
    }
}
