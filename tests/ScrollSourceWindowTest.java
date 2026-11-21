package ru.dot.clipshot;

/** Window events as seen by the service, after it filters its own editor and overlays. */
public final class ScrollSourceWindowTest {
    private static final String LAUNCHER = "com.miui.home";
    private static final String SOURCE = "example.reader";

    public static void main(String[] args) {
        editorReturnThroughLauncher();
        windowChangesDuringComparison();
        noInitialWindowEvent();
        delayedSourceAnnouncement();
        silentReturnAfterSystemWindow();
        differentWindowOfSameApp();
        anonymousWindowEvent();
        scrollWindowIdentity();
        System.out.println("ScrollSourceWindowTest passed");
    }

    private static void scrollWindowIdentity() {
        ScrollSourceWindow watch = new ScrollSourceWindow(LAUNCHER);
        watch.noteScroll(LAUNCHER, 9);
        check(!watch.matchesIdentity(LAUNCHER, 9), "launcher established page identity");
        watch.noteScroll(SOURCE, -1);
        check(!watch.matchesIdentity(SOURCE, -1), "redacted window ID established page identity");
        watch.changed(SOURCE, -1, true);
        check(watch.acceptsScroll(SOURCE, -1), "redacted ID prevented ordinary motion readings");
        int before = watch.generation();
        watch.noteScroll(SOURCE, 17);
        check(watch.matchesIdentity(SOURCE, 17) && before != watch.generation(), "late real ID not verified");
        watch.noteScroll("other.app", 20);
        check(!watch.acceptsScroll("other.app", 20) && !watch.matchesIdentity(SOURCE, 20),
                "metrics from unrelated window accepted");
    }

    private static void editorReturnThroughLauncher() {
        ScrollSourceWindow watch = new ScrollSourceWindow(LAUNCHER);
        check(!watch.changed(LAUNCHER, 3, true), "launcher interrupted return from editor");
        check(!watch.changed(SOURCE, 11, true), "source interrupted return from launcher");
        check(!watch.confirmed(), "window event alone confirmed the source");
        int frame = watch.generation();
        check(!watch.changed(SOURCE, 11, true), "repeated source announcement interrupted startup");
        check(watch.confirm(frame), "matched frame rejected after a repeated announcement");
        check(watch.confirmed(), "successful comparison failed to arm window tracking");
        check(watch.startSwiping(frame), "stable reader could not begin scrolling");
        check(!watch.changed(SOURCE, 11, true), "source interrupted its own capture");
        check(watch.changed(LAUNCHER, 3, true), "Home failed to stop a confirmed capture");
    }

    private static void windowChangesDuringComparison() {
        ScrollSourceWindow watch = new ScrollSourceWindow(LAUNCHER);
        watch.changed(SOURCE, 11, true);
        int reading = watch.generation();
        watch.changed(LAUNCHER, 3, true);
        watch.changed(SOURCE, 11, true);
        check(!watch.confirm(reading), "accepted a frame across a switch out and back");
        check(!watch.confirmed(), "stale frame armed window tracking");
        check(watch.confirm(watch.generation()), "next freshly matched frame was rejected");
    }

    private static void noInitialWindowEvent() {
        ScrollSourceWindow watch = new ScrollSourceWindow(LAUNCHER);
        int frame = watch.generation();
        check(watch.confirm(frame), "matched frame required an accessibility event");
        check(watch.startSwiping(frame), "verified page required an accessibility event to scroll");
        check(watch.changed(LAUNCHER, 4, true), "unidentified source allowed the next app to scroll");
    }

    private static void delayedSourceAnnouncement() {
        ScrollSourceWindow watch = new ScrollSourceWindow(LAUNCHER);
        int frame = watch.generation();
        check(watch.confirm(frame), "could not confirm before the first event");
        check(!watch.changed(SOURCE, 11, true), "late source announcement ended capture");
        check(!watch.startSwiping(frame), "started scrolling with a stale frame");
        int fresh = watch.generation();
        check(watch.confirm(fresh) && watch.startSwiping(fresh), "could not scroll after rechecking");
        check(!watch.changed(SOURCE, 11, true), "source duplicate interrupted the verified session");
    }

    private static void silentReturnAfterSystemWindow() {
        for (String transition : new String[]{LAUNCHER, "com.android.systemui"}) {
            ScrollSourceWindow watch = new ScrollSourceWindow(LAUNCHER);
            check(!watch.changed(transition, 5, true), "system transition interrupted startup");
            // The reader comes back silently. Its pixels match, but the previously
            // announced window must not become the owner of that verified screenshot.
            int frame = watch.generation();
            check(watch.confirm(frame) && watch.startSwiping(frame), "could not scroll on silent return");
            check(watch.changed(transition, 5, true), "return to stale system window was accepted");
        }
    }

    private static void differentWindowOfSameApp() {
        ScrollSourceWindow watch = new ScrollSourceWindow(LAUNCHER);
        watch.changed(SOURCE, 11, true);
        int frame = watch.generation();
        check(watch.confirm(frame) && watch.startSwiping(frame), "could not scroll reader");
        check(!watch.changed(SOURCE, 12, false), "temporary same-app window stopped capture");
        check(watch.changed(SOURCE, 12, true), "another full-screen reader window was accepted");
    }

    private static void anonymousWindowEvent() {
        ScrollSourceWindow watch = new ScrollSourceWindow(LAUNCHER);
        watch.changed(SOURCE, 11, true);
        int frame = watch.generation();
        check(!watch.changed(null, -1, false), "anonymous transient interrupted startup");
        check(watch.confirm(frame), "anonymous transient invalidated the matched frame");
        check(watch.startSwiping(frame), "anonymous transient prevented scrolling");
        check(watch.changed(null, -1, true), "anonymous full-screen change failed to stop capture");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
