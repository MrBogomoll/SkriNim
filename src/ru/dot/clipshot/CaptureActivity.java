package ru.dot.clipshot;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Toast;

public final class CaptureActivity extends Activity {
    static final String CONSENT_ONLY = "consentOnly";
    private static final int CAPTURE_CONSENT = 45;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean resumed, requested, asking;
    private final Runnable tryCapture = this::captureAfterCollapse;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarContrastEnforced(false);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        if (saved != null) asking = saved.getBoolean("asking");
        ui.postDelayed(() -> {
            if (!requested && !asking && !isFinishing()) {
                Toast.makeText(this, "Шторка не закрылась. Снимок не сделан.", Toast.LENGTH_SHORT).show();
                finishQuietly();
            }
        }, 3500);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("asking", asking);
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
        if (resumed && hasWindowFocus() && !asking && !requested && !isFinishing()) {
            ui.removeCallbacks(tryCapture);
            ui.postDelayed(tryCapture, 450);
        }
    }
    private boolean canCapture() {
        return resumed && hasWindowFocus() && !isFinishing()
            && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    private void captureAfterCollapse() {
        if (requested || asking || !canCapture()) return;
        SkriNimApp app=(SkriNimApp)getApplication();
        if (app.returnToEditor(this)) {
            requested=true;
            finishQuietly();
            return;
        }
        ProjectionService service = ProjectionService.current;
        if (service == null || !service.ready()) {
            asking = true;
            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            Intent prompt = Build.VERSION.SDK_INT >= 34
                ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                : manager.createScreenCaptureIntent();
            try { startActivityForResult(prompt, CAPTURE_CONSENT); }
            catch (RuntimeException e) {
                Toast.makeText(this, "Не удалось открыть системный запрос", Toast.LENGTH_SHORT).show();
                finishQuietly();
            }
            return;
        }
        if (getIntent().getBooleanExtra(CONSENT_ONLY, false)) { finishQuietly(); return; }
        requested = true;
        service.capture(bitmap -> {
            if (canCapture() && app.returnToEditor(this)) {
                if (bitmap!=null) bitmap.recycle();
                finishQuietly();
                return;
            }
            if (bitmap == null || !canCapture()) {
                if (bitmap != null) bitmap.recycle();
                if (!isFinishing()) {
                    Toast.makeText(this, "Снимок не сделан. Попробуй ещё раз.", Toast.LENGTH_SHORT).show();
                    finishQuietly();
                }
                return;
            }
            app.openOrReturn(this,bitmap);
            finishQuietly();
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CAPTURE_CONSENT) return;
        if (((SkriNimApp)getApplication()).returnToEditor(this)) {
            finishQuietly();
            return;
        }
        if (result == RESULT_OK && data != null) {
            try {
                startForegroundService(ProjectionService.startIntent(this, result, data));
                Toast.makeText(this, FloatingCapture.enabled(this)
                        ? "Сеанс запущен. Для снимка нажми плитку или плавающую кнопку."
                        : "Сеанс запущен. Нажми плитку для снимка.", Toast.LENGTH_LONG).show();
            } catch (RuntimeException ex) {
                Toast.makeText(this, "Не удалось начать сеанс захвата.", Toast.LENGTH_LONG).show();
            }
        }
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
