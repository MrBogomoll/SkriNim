package ru.dot.clipshot;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;

final class Look {
    private static final String PREFS = "appearance";
    private static final String DARK = "dark";
    final boolean dark;
    final int canvas, panel, text, muted, accent, onAccent, tint, danger, dangerTint, border, frame;

    static boolean isDark(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DARK, false);
    }

    Look(Context context) {
        dark = isDark(context);
        canvas = dark ? 0xff12151b : 0xffeef2f7;
        panel = dark ? 0xff1c212b : 0xfffdfdff;
        text = dark ? 0xffeef3ff : 0xff172235;
        muted = dark ? 0xffb5c1d5 : 0xff465872;
        accent = dark ? 0xff90c9ff : 0xff005aba;
        onAccent = dark ? 0xff002c51 : 0xffffffff;
        tint = dark ? 0xff2b3748 : 0xffe4edfa;
        danger = dark ? 0xffffb4b0 : 0xff8f252a;
        dangerTint = dark ? 0xff40252b : 0xffffe9e8;
        border = dark ? 0xff394453 : 0xffd5dfed;
        frame = dark ? 0xff64b8ff : 0xff0872db;
    }

    static void save(Context context, boolean dark) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DARK, dark).apply();
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(Context c, int fill, int radius, boolean outlined) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(c, radius));
        if (outlined) bg.setStroke(dp(c, 1), border);
        return bg;
    }

    Button button(Context c, String label, int bg, int fg) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(fg);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(dp(c, 48));
        b.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setTranslationZ(0);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x34ffffff : 0x24001c40),
                shape(c, bg, 16, false), shape(c, 0xffffffff, 16, false)));
        return b;
    }

    void bars(Activity activity, View content) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT < 35) {
            window.setStatusBarColor(canvas);
            window.setNavigationBarColor(canvas);
        }
        WindowInsetsController controller = window.getInsetsController();
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        if (controller != null) controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
        if (Build.VERSION.SDK_INT >= 35) {
            content.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
            content.requestApplyInsets();
        }
    }
}
