package ru.dot.clipshot;

public final class EditorGateTest {
    public static void main(String[] args) {
        EditorGate<Object,Object> gate=new EditorGate<>();
        Object screenshot=new Object(), duplicate=new Object(), editor=new Object();
        require(!gate.occupied() && !gate.opening());
        require(gate.reserve(screenshot));
        require(gate.occupied() && gate.opening() && gate.active()==null);
        require(!gate.reserve(duplicate));
        require(gate.abandon(duplicate)==null && gate.opening());
        gate.opened(editor);
        require(gate.occupied() && !gate.opening() && gate.active()==editor);
        // Projection stop, app backgrounding, and unrelated activity teardown
        // make no mutation to the editor reservation/ownership.
        gate.closed(new Object());
        require(gate.active()==editor && !gate.reserve(duplicate));
        require(gate.abandon(screenshot)==null && gate.active()==editor);
        gate.closed(editor);
        require(!gate.occupied() && gate.reserve(duplicate));
        require(gate.abandon(duplicate)==duplicate && !gate.occupied());
        System.out.println("EditorGateTest passed");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Editor reservation state");
    }
}
