package ru.dot.clipshot;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Main-thread visibility of this app's own activities only; no inspection of other apps. */
public final class SkriNimApp extends Application implements Application.ActivityLifecycleCallbacks {
    private final FloatingVisibility<Activity> floatingVisibility=new FloatingVisibility<>();
    private final EditorGate<Activity,Bitmap> editors=new EditorGate<>();
    private final Handler main=new Handler(Looper.getMainLooper());

    final AppUpdates updates = new AppUpdates(this);

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    boolean blocksFloatingButton() { return floatingVisibility.blocked(); }
    boolean editorOpening() { return editors.opening(); }

    /**
     * An editor is independent of the capture service and remains occupied
     * while its task is backgrounded. A pending launch already counts.
     */
    boolean returnToEditor(Context caller) {
        if (!editors.occupied()) return false;
        Activity existing=editors.active();
        if (existing!=null && !existing.isFinishing() && !existing.isDestroyed()) {
            try { caller.startActivity(editorIntent(caller)); }
            catch (RuntimeException error) {
                Toast.makeText(caller,"Не удалось вернуться к открытому снимку.",Toast.LENGTH_SHORT).show();
            }
        }
        return true;
    }

    /** Always takes ownership of the new bitmap, even if another editor wins the race. */
    void openOrReturn(Context caller,Bitmap preview) {
        if (!editors.reserve(preview)) {
            returnToEditor(caller);
            preview.recycle();
            return;
        }
        ShotMemory.putPreview(preview);
        try {
            caller.startActivity(editorIntent(caller));
            main.postDelayed(() -> cancelLaunch(preview),2400);
        } catch (RuntimeException error) {
            cancelLaunch(preview);
        }
    }

    private static Intent editorIntent(Context caller) {
        return new Intent(caller,CropActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION);
    }

    private void cancelLaunch(Bitmap preview) {
        Bitmap abandoned=editors.abandon(preview);
        if (abandoned==null) return;
        ShotMemory.clearPreview(abandoned);
        abandoned.recycle();
        notifyChange(null);
        Toast.makeText(this,"Редактор не открылся. Попробуй ещё раз.",Toast.LENGTH_SHORT).show();
    }

    @Override public void onActivityStarted(Activity activity) {
        floatingVisibility.started(activity,activity instanceof SetupActivity);
        notifyChange(activity);
    }
    @Override public void onActivityStopped(Activity activity) {
        floatingVisibility.stopped(activity,activity instanceof CaptureActivity);
        notifyChange(null);
    }
    private void notifyChange(Activity started) {
        ProjectionService service=ProjectionService.current;
        if (service!=null) service.onOwnActivityChanged(started);
    }
    @Override public void onActivityCreated(Activity a,Bundle b) {
        if (a instanceof CropActivity) editors.opened(a);
        if (a instanceof CaptureActivity) {
            floatingVisibility.created(a,true);
            notifyChange(null);
        }
    }
    @Override public void onActivityResumed(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {
        if (a instanceof CropActivity) editors.closed(a);
        floatingVisibility.destroyed(a);
        notifyChange(null);
    }
}
