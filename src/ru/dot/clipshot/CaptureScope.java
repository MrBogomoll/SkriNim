package ru.dot.clipshot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;

final class CaptureScope {
    private static final String FULL_SCREEN = "full_screen";

    static boolean supportsAppCapture() { return Build.VERSION.SDK_INT >= 34; }

    static boolean fullScreen(Context context) {
        return preferences(context).getBoolean(FULL_SCREEN, true);
    }

    static void setFullScreen(Context context, boolean fullScreen) {
        preferences(context).edit().putBoolean(FULL_SCREEN, fullScreen).apply();
    }

    static Intent consentIntent(Context context) {
        MediaProjectionManager manager = context.getSystemService(MediaProjectionManager.class);
        if (supportsAppCapture() && fullScreen(context)) {
            return manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        }
        return manager.createScreenCaptureIntent();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("capture_scope", Context.MODE_PRIVATE);
    }
}
