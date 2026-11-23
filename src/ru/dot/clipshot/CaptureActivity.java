package ru.dot.clipshot;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.Toast;

public final class CaptureActivity extends Activity {
    static final String CONSENT_ONLY = "consentOnly";
    private static final int CAPTURE_CONSENT = 45;
    private static final int NOTIFICATION_PERMISSION = 46;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean resumed, requested, asking, askingNotifications;
    private long waitForSessionUntil;
    private final Runnable tryCapture = this::captureAfterCollapse;
    private final Runnable shadeTimeout = () -> {
        if (!isFinishing()) {
            Toast.makeText(this, "Шторка не закрылась. Снимок не сделан.", Toast.LENGTH_SHORT).show();
            finishQuietly();
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarContrastEnforced(false);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        if (saved != null) {
            asking = saved.getBoolean("asking");
            askingNotifications = saved.getBoolean("askingNotifications");
            waitForSessionUntil = saved.getLong("waitForSession");
        }
        if (!asking && !askingNotifications && waitForSessionUntil == 0) ui.postDelayed(shadeTimeout, 3500);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("asking", asking);
        out.putBoolean("askingNotifications", askingNotifications);
        out.putLong("waitForSession", waitForSessionUntil);
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        schedule();
    }
    @Override protected void onPause() {
        resumed = false;
        ui.removeCallbacks(tryCapture);
        super.onPause();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) schedule(); else ui.removeCallbacks(tryCapture);
    }
    private void schedule() {
        if (resumed && hasWindowFocus() && !asking && !askingNotifications && !requested && !isFinishing()) {
            ui.removeCallbacks(tryCapture);
            ui.postDelayed(tryCapture, 450);
        }
    }
    private boolean canCapture() {
        return resumed && hasWindowFocus() && !isFinishing()
            && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    private void captureAfterCollapse() {
        if (requested || asking || askingNotifications || !canCapture()) return;
        ui.removeCallbacks(shadeTimeout);
        SkriNimApp app=(SkriNimApp)getApplication();
        // The app picker can leave an explicit start awaiting a foreground action.
        // A tile tap must dispatch that grant before it returns to the open editor.
        if (!consentOnly() && !ProjectionService.hasPendingConsent() && app.returnToEditor(this)) {
            requested=true;
            finishQuietly();
            return;
        }
        ProjectionService service = ProjectionService.current;
        if (service == null || !service.ready()) {
            if (waitForSessionUntil == 0 && ProjectionService.hasPendingConsent()) {
                if (!ProjectionService.resumeConsent(this)) {
                    finishQuietly();
                    return;
                }
                waitForSessionUntil = SystemClock.uptimeMillis() + 4000;
            }
            if (waitForSessionUntil != 0) {
                if (SystemClock.uptimeMillis() < waitForSessionUntil) ui.postDelayed(tryCapture, 100);
                else {
                    Toast.makeText(this, "Сеанс пока не запущен. Нажми плитку ещё раз.", Toast.LENGTH_LONG).show();
                    finishQuietly();
                }
                return;
            }
            requestConsent();
            return;
        }
        if (consentOnly()) { finishQuietly(); return; }
        requested = true;
        service.capture((bitmap, origin) -> {
            if (canCapture() && app.returnToEditor(this)) {
                if (bitmap!=null) bitmap.recycle();
                finishQuietly();
                return;
            }
            if (bitmap == null || !canCapture()) {
                if (bitmap != null) bitmap.recycle();
                if (!isFinishing()) {
                    Toast.makeText(this, service.captureFailureMessage(), Toast.LENGTH_SHORT).show();
                    finishQuietly();
                }
                return;
            }
            app.openOrReturn(this,bitmap,origin);
            finishQuietly();
        });
    }
    private boolean consentOnly() { return getIntent().getBooleanExtra(CONSENT_ONLY, false); }

    private void requestConsent() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences prefs = getSharedPreferences("capture_notifications", MODE_PRIVATE);
            if (!prefs.getBoolean("requested", false)) {
                prefs.edit().putBoolean("requested", true).apply();
                askingNotifications = true;
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
                return;
            }
        }
        asking = true;
        try {
            startActivityForResult(CaptureScope.consentIntent(this), CAPTURE_CONSENT);
        } catch (RuntimeException failure) {
            Toast.makeText(this, "Не удалось открыть системный запрос", Toast.LENGTH_SHORT).show();
            finishQuietly();
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(request, permissions, grantResults);
        if (request == NOTIFICATION_PERMISSION) {
            // Notification consent only controls the Stop action in the shade.
            askingNotifications = false;
            schedule();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CAPTURE_CONSENT || !asking) return;
        requested = true;
        asking = false;
        if (!consentOnly() && ((SkriNimApp)getApplication()).returnToEditor(this)) {
            finishQuietly();
            return;
        }
        if (result == RESULT_OK && data != null) ProjectionService.acceptConsent(this, result, data);
        finishQuietly();
    }
    @SuppressWarnings("deprecation")
    private void finishQuietly() {
        finish();
        overridePendingTransition(0, 0);
    }
    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
