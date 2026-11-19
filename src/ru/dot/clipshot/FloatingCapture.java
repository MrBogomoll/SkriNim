package ru.dot.clipshot;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ImageButton;

/** Draggable, opt-in app-overlay window. It never owns or stores a screenshot. */
final class FloatingCapture {
    private static final String PREFS="floating_capture", ENABLED="enabled", X="x", Y="y";
    private final Context app;
    private final Runnable tap;
    private WindowManager windows;
    private ImageButton button;
    private WindowManager.LayoutParams params;
    private int size;
    private float fx,fy,startX,startY;
    private int startLeft,startTop,pointer=-1;
    private boolean dragging;

    FloatingCapture(Context app,Runnable tap) {
        this.app=app;
        this.tap=tap;
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(ENABLED,false);
    }

    static void setEnabled(Context c,boolean enabled) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(ENABLED,enabled).apply();
    }

    static boolean allowed(Context c) { return Settings.canDrawOverlays(c); }

    boolean visible() { return button!=null; }

    void show() {
        if (visible() || !allowed(app)) return;
        try {
            DisplayManager displays=app.getSystemService(DisplayManager.class);
            Display display=displays==null?null:displays.getDisplay(Display.DEFAULT_DISPLAY);
            if (display==null) return;
            Context window=app.createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
            windows=window.getSystemService(WindowManager.class);
            if (windows==null) return;
            size=Look.dp(window,48);
            fx=clamp(app.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getFloat(X,.98f));
            fy=clamp(app.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getFloat(Y,.48f));
            WindowManager.LayoutParams next=new WindowManager.LayoutParams(size,size,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            next.gravity=Gravity.TOP|Gravity.LEFT;
            next.setTitle("SkriNim: плавающая кнопка");
            params=next;
            position();
            Look look=new Look(window);
            ImageButton view=new ImageButton(window);
            view.setPadding(Look.dp(window,12),Look.dp(window,12),Look.dp(window,12),Look.dp(window,12));
            view.setBackground(look.shape(window,look.accent,24,true));
            view.setImageResource(android.R.drawable.ic_menu_camera);
            view.setColorFilter(look.onAccent);
            view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            view.setContentDescription("Сделать снимок экрана");
            view.setOnClickListener(v -> tap.run());
            int slop=ViewConfiguration.get(window).getScaledTouchSlop();
            view.setOnTouchListener((v,event) -> touch(view,event,slop));
            windows.addView(view,params);
            button=view;
        } catch (RuntimeException error) {
            // Permission can disappear while adding the window, or OEMs may disallow it.
            clear();
        }
    }

    /** Success means the WindowManager removal returned, or the window was already detached. */
    boolean hide() {
        if (!visible()) return true;
        try {
            windows.removeViewImmediate(button);
        } catch (IllegalArgumentException alreadyGone) {
            // Detached by the system (for example, permission was revoked).
        } catch (RuntimeException ignored) {
            return false;
        }
        clear();
        return true;
    }

    private void clear() {
        button=null;
        windows=null;
        params=null;
        pointer=-1;
    }

    void relayout() {
        if (!visible()) return;
        position();
        applyPosition();
    }

    private boolean touch(ImageButton view,MotionEvent e,int slop) {
        int action=e.getActionMasked();
        if (action==MotionEvent.ACTION_DOWN) {
            pointer=e.getPointerId(0);
            dragging=false;
            startX=e.getRawX();
            startY=e.getRawY();
            startLeft=params.x;
            startTop=params.y;
            view.setPressed(true);
            return true;
        }
        int index=e.findPointerIndex(pointer);
        if (action==MotionEvent.ACTION_CANCEL || index<0 ||
            (action==MotionEvent.ACTION_POINTER_UP && e.getActionIndex()==index)) {
            finishDrag(view,false);
            return true;
        }
        if (action==MotionEvent.ACTION_MOVE) {
            float dx=e.getRawX(index)-startX,dy=e.getRawY(index)-startY;
            if (!dragging && Math.hypot(dx,dy)>slop) {
                dragging=true;
                view.setPressed(false);
            }
            if (dragging) {
                int maxX=maxX(),maxY=maxY();
                params.x=Math.max(0,Math.min(maxX,startLeft+Math.round(dx)));
                params.y=Math.max(0,Math.min(maxY,startTop+Math.round(dy)));
                fx=maxX==0?.5f:params.x/(float)maxX;
                fy=maxY==0?.5f:params.y/(float)maxY;
                applyPosition();
            }
        } else if (action==MotionEvent.ACTION_UP) finishDrag(view,true);
        return true;
    }

    private void finishDrag(ImageButton view,boolean up) {
        pointer=-1;
        view.setPressed(false);
        if (dragging) app.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putFloat(X,fx).putFloat(Y,fy).apply();
        else if (up) view.performClick();
    }

    private void position() {
        params.x=Math.round(fx*maxX());
        params.y=Math.round(fy*maxY());
    }

    private void applyPosition() {
        try { windows.updateViewLayout(button,params); }
        catch (RuntimeException error) { hide(); }
    }

    private int extent(boolean horizontal) {
        WindowMetrics metrics=windows.getCurrentWindowMetrics();
        Rect bounds=metrics.getBounds();
        Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        return Math.max(0,(horizontal?bounds.width()-insets.left-insets.right
                :bounds.height()-insets.top-insets.bottom)-size);
    }
    private int maxX() { return extent(true); }
    private int maxY() { return extent(false); }
    private static float clamp(float n) { return Math.max(0,Math.min(1,n)); }
}
