package ru.dot.clipshot;

import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** An in-place choice during a paused scroll capture. Main thread only. */
final class ScrollRecoveryOverlay {
    private final ScrollGestureService service;
    private WindowManager windows;
    private LinearLayout panel;

    ScrollRecoveryOverlay(ScrollGestureService service) {
        this.service = service;
    }

    boolean show(String message, String positiveLabel, Runnable check, Runnable cancel) {
        if (panel != null) return false;
        try {
            windows = service.getSystemService(WindowManager.class);
            WindowMetrics metrics = windows.getMaximumWindowMetrics();
            int safeTypes = WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout();
            Insets safe = metrics.getWindowInsets().getInsetsIgnoringVisibility(safeTypes);
            int margin = Look.dp(service, 8);
            int available = metrics.getBounds().width() - safe.left - safe.right - 2*margin;
            int width = Math.min(Look.dp(service, 306), available);
            if (width <= 0) return false;

            panel = new LinearLayout(service);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setAccessibilityPaneTitle("Прокрутка SkriNim");
            Look look = new Look(service);
            panel.setBackground(look.shape(service, look.panel, 16, true));
            int pad = Look.dp(service, 12);
            panel.setPadding(pad, pad, pad, pad);

            TextView instruction = new TextView(service);
            instruction.setTextColor(look.text);
            instruction.setTextSize(14);
            instruction.setText(message);
            panel.addView(instruction, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout buttons = new LinearLayout(service);
            boolean stacked = width < Look.dp(service, 236 * service.getResources().getConfiguration().fontScale);
            buttons.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams buttonRow = new LinearLayout.LayoutParams(-1, -2);
            buttonRow.topMargin = margin;
            panel.addView(buttons, buttonRow);
            Button back = look.button(service, "В редактор", look.tint, look.text);
            Button accept = look.button(service, positiveLabel, look.accent, look.onAccent);
            LinearLayout.LayoutParams backSize = stacked ? new LinearLayout.LayoutParams(-1, -2)
                    : new LinearLayout.LayoutParams(0, -2, 1);
            LinearLayout.LayoutParams acceptSize = new LinearLayout.LayoutParams(backSize);
            if (stacked) acceptSize.topMargin = margin;
            else acceptSize.setMarginStart(margin);
            buttons.addView(back, backSize);
            buttons.addView(accept, acceptSize);
            back.setOnClickListener(v -> cancel.run());
            accept.setOnClickListener(v -> check.run());

            // Only the card itself has a touchable region. The page underneath
            // must remain interactive so the user can scroll it into position.
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(width,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.setFitInsetsTypes(0);
            params.gravity = Gravity.TOP | Gravity.RIGHT;
            params.x = safe.right + margin;
            params.y = safe.top + margin;
            params.setTitle("SkriNim — продолжить прокрутку");
            windows.addView(panel, params);
            return true;
        } catch (RuntimeException unavailable) {
            hide();
            return false;
        }
    }

    /** True only after the card has been detached, before a clean frame is requested. */
    boolean hide() {
        if (panel == null) return true;
        panel.setVisibility(View.GONE);
        try {
            windows.removeViewImmediate(panel);
        } catch (IllegalArgumentException removedBySystem) {
            // addView can also fail before attaching the card.
        } catch (RuntimeException unavailable) {
            if (panel.getParent() != null || panel.isAttachedToWindow()) return false;
        }
        if (panel.getParent() != null || panel.isAttachedToWindow()) return false;
        panel = null;
        windows = null;
        return true;
    }
}
