package ru.dot.clipshot;

public final class FloatingVisibilityTest {
    public static void main(String[] args) {
        FloatingVisibility<Object> policy=new FloatingVisibility<>();
        Object home=new Object(), editor=new Object(), captureGate=new Object();
        check(!policy.blocked(),"external activity");
        policy.created(home,false);
        policy.started(home,true);
        check(!policy.blocked(),"main and settings tabs");
        policy.created(captureGate,true);
        check(policy.blocked(),"capture gate during launch");
        policy.started(captureGate,false);
        policy.stopped(captureGate,true);
        check(policy.blocked(),"system consent foreground, gate stopped");
        policy.created(editor,false);
        policy.started(editor,false);
        policy.destroyed(captureGate);
        check(policy.blocked(),"editor visible over main");
        policy.stopped(editor,false);
        check(!policy.blocked(),"background editor can be restored by bubble");
        policy.started(editor,false);
        check(policy.blocked(),"restored editor");
        policy.destroyed(editor);
        check(!policy.blocked(),"back on main");
        System.out.println("FloatingVisibilityTest passed");
    }

    private static void check(boolean ok,String step) { if (!ok) throw new AssertionError(step); }
}
