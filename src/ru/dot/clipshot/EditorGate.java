package ru.dot.clipshot;

/** Main-thread capture/editor negotiation, independent of the projection session. */
final class EditorGate<A,P> {
    private A active;
    private P pending;

    boolean occupied() { return active != null || pending != null; }
    boolean opening() { return pending != null; }
    A active() { return active; }

    boolean reserve(P preview) {
        if (occupied() || preview == null) return false;
        pending = preview;
        return true;
    }

    void opened(A activity) {
        active = activity;
        pending = null;
    }

    void closed(A activity) {
        if (active == activity) active = null;
    }

    P abandon(P preview) {
        if (pending != preview) return null;
        P abandoned = pending;
        pending = null;
        return abandoned;
    }
}
