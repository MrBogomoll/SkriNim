package ru.dot.clipshot;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Which app-owned windows must suppress the floating button. Main-thread only. */
final class FloatingVisibility<T> {
    private final Set<T> blockers=Collections.newSetFromMap(new IdentityHashMap<>());

    boolean blocked() { return !blockers.isEmpty(); }

    void created(T screen, boolean captureGate) {
        if (captureGate) blockers.add(screen);
    }

    void started(T screen, boolean mainPage) {
        if (!mainPage) blockers.add(screen);
    }

    void stopped(T screen, boolean captureGate) {
        // A full-screen system consent prompt may stop the capture gate,
        // but it must still suppress the bubble until the gate is destroyed.
        if (!captureGate) blockers.remove(screen);
    }

    void destroyed(T screen) { blockers.remove(screen); }
}
