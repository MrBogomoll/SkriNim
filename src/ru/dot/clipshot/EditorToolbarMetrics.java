package ru.dot.clipshot;

/** Panel geometry depends on the available window, never on the selected tool. */
final class EditorToolbarMetrics {
    static final int TOOLS_DP=56, PALETTE_DP=52, OPTIONS_DP=48, ACTIONS_DP=72;
    static final int CANVAS_RESERVE_DP=120;

    static int preferred(float density) {
        return Math.round(TOOLS_DP*density)+Math.round(PALETTE_DP*density)
                +Math.round(OPTIONS_DP*density)+Math.round(ACTIONS_DP*density);
    }

    static int viewport(int available,int preferred,int canvasReserve) {
        return Math.min(preferred,Math.max(available/2,available-canvasReserve));
    }

    private EditorToolbarMetrics() {}
}
