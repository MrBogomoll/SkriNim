package ru.dot.clipshot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;

import java.util.function.Consumer;

/** Sends one bounded swipe at a time while a user-started long capture is active. */
public final class ScrollGestureService extends AccessibilityService {
    static ScrollGestureService current;
    private long watchingSince, lastWindowReport, lastScrollReport;
    private ScrollSourceWindow source;
    private ScrollMotion motion = ScrollMotion.empty();
    private String homePackage;
    private boolean observingShot, ambiguousShot, shotScrolled;
    private Origin shotOrigin, returnOrigin;
    private Runnable stopRequested, interrupted;
    private WindowManager windows;
    private Button stopButton;
    private ScrollRecoveryOverlay recovery;
    private boolean showing;
    private int session;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        current = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if ((!observingShot && interrupted == null) || event.getEventTime() < watchingSince) return;
        if (Build.VERSION.SDK_INT >= 33 && event.getDisplayId() >= 0
                && event.getDisplayId() != Display.DEFAULT_DISPLAY) return;
        int kind = event.getEventType();
        if (kind != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && kind != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;
        String owner = event.getPackageName() == null ? null : event.getPackageName().toString();
        String type = event.getClassName() == null ? "" : event.getClassName().toString();
        boolean scrolling = kind == AccessibilityEvent.TYPE_VIEW_SCROLLED;
        if (!scrolling) {
            if (getPackageName().equals(owner) && !SetupActivity.class.getName().equals(type)) return;
            // Pane titles, tooltips and toasts do not describe a new foreground task.
            if (event.getContentChangeTypes() != 0 || "android.widget.Toast".equals(type)
                    || "android.widget.TooltipPopup".equals(type)) return;
        }
        int id = event.getWindowId();
        boolean appWindow = owner != null && !owner.equals(homePackage) && !owner.equals("com.android.systemui");
        if (observingShot) {
            if (scrolling && appWindow && id >= 0) shotScrolled = true;
            if (!appWindow || id < 0 || (!scrolling && !event.isFullScreen())) {
                ambiguousShot = true;
            } else if (shotOrigin == null) {
                shotOrigin = new Origin(owner, id);
            } else if (!shotOrigin.matches(owner, id)) {
                ambiguousShot = true;
            }
            return;
        }
        if (scrolling) {
            if (!appWindow) return;
            source.noteScroll(owner, id);
            if (!source.acceptsScroll(owner, id)) {
                returnOrigin = null;
                return;
            }
            if (id >= 0) returnOrigin = new Origin(owner, id);
            lastScrollReport = SystemClock.uptimeMillis();
            motion = motion.update(type, id, event.getScrollX(), event.getScrollY(), event.getMaxScrollY(),
                    event.getScrollDeltaY(), event.getFromIndex(), event.getToIndex(), event.getItemCount());
            return;
        }
        lastWindowReport = SystemClock.uptimeMillis();
        int generation = source.generation();
        if (source.changed(owner, id, event.isFullScreen())
                || (recovery != null && generation != source.generation())) {
            interruptSession();
            return;
        }
        if (generation != source.generation()) {
            motion = ScrollMotion.empty();
            lastScrollReport = 0;
        }
        returnOrigin = appWindow && id >= 0 && event.isFullScreen() ? new Origin(owner, id) : null;
    }

    static final class Origin {
        private final String owner;
        private final int window;

        private Origin(String owner, int window) { this.owner = owner; this.window = window; }

        private boolean matches(String candidate, int id) { return window == id && owner.equals(candidate); }
    }

    private String resolveHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ComponentName launcher = home.resolveActivity(getPackageManager());
        return launcher == null ? null : launcher.getPackageName();
    }

    void observeShot() {
        if (current != this || interrupted != null) return;
        homePackage = resolveHome();
        watchingSince = SystemClock.uptimeMillis();
        shotOrigin = null;
        ambiguousShot = shotScrolled = false;
        observingShot = watchWindows(true);
    }

    Origin finishShot(boolean visualMotion) {
        if (!observingShot) return null;
        Origin result = !ambiguousShot && (shotScrolled || visualMotion) ? shotOrigin : null;
        observingShot = false;
        shotOrigin = null;
        watchWindows(false);
        return result;
    }

    boolean matchesOrigin(Origin origin) {
        return origin != null && returnOrigin != null && source != null
                && returnOrigin.matches(origin.owner, origin.window) && source.matchesIdentity(origin.owner, origin.window);
    }

    ScrollMotion scrollMotion() { return motion; }

    boolean scrollSettling() {
        return lastScrollReport != 0 && SystemClock.uptimeMillis() - lastScrollReport < 160;
    }

    boolean watchSource(Runnable onInterrupted) {
        if (current != this || interrupted != null || !hideStop()) return false;
        finishShot(false);
        homePackage = resolveHome();
        source = new ScrollSourceWindow(homePackage);
        motion = ScrollMotion.empty();
        lastScrollReport = 0;
        returnOrigin = null;
        interrupted = onInterrupted;
        lastWindowReport = watchingSince = SystemClock.uptimeMillis();
        if (watchWindows(true)) return true;
        end();
        return false;
    }

    int sourceGeneration() { return source == null ? -1 : source.generation(); }

    boolean windowSettling() {
        long now = SystemClock.uptimeMillis();
        // Some apps report their unchanged window continuously. In that case the
        // visual check and generation guard still apply after this brief grace period.
        return now - lastWindowReport < 220 && now - watchingSince < 1400;
    }

    boolean confirmVerifiedSource(int frameGeneration) {
        return current == this && source != null && source.confirm(frameGeneration);
    }

    boolean startSwiping(int frameGeneration) {
        return current == this && source != null && source.startSwiping(frameGeneration);
    }

    boolean clearControlsForRetry() {
        stopRequested = null;
        return hideStop();
    }

    boolean showRecovery(String message, String action, Runnable check, Runnable cancel) {
        if (current != this || source == null || !hideStop()) return false;
        if (recovery == null) recovery = new ScrollRecoveryOverlay(this);
        return recovery.show(message, action, check, cancel);
    }

    boolean hideRecovery() {
        if (recovery == null) return true;
        if (!recovery.hide()) return false;
        recovery = null;
        return true;
    }

    boolean begin(Runnable stop) {
        if (current != this || source == null || !source.confirmed() || stopRequested != null) return false;
        stopRequested = stop;
        if (showStop()) return true;
        end();
        return false;
    }

    private boolean watchWindows(boolean enabled) {
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) return false;
            info.eventTypes = enabled
                    ? AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_SCROLLED : 0;
            setServiceInfo(info);
            return true;
        } catch (RuntimeException disconnected) {
            return false;
        }
    }

    boolean showStop() {
        if (current != this || stopRequested == null) return false;
        if (showing) return true;
        try {
            // AccessibilityService supplies a WindowManager with its overlay token.
            windows = getSystemService(WindowManager.class);
            if (stopButton == null) {
                Look look = new Look(this);
                stopButton = look.button(this, "Стоп", look.danger, look.panel);
                // Keep every painted pixel inside the reported window rectangle,
                // including during a pressed-state transition.
                stopButton.setStateListAnimator(null);
                stopButton.setElevation(0);
                stopButton.setMinimumWidth(Look.dp(this, 64));
                stopButton.setContentDescription("Остановить прокрутку и открыть снимок");
                stopButton.setOnClickListener(v -> {
                    if (stopRequested != null) stopRequested.run();
                });
                // dispatchGesture cancels any touch already in progress. Honor
                // the first contact even if it lands just before a scheduled swipe.
                stopButton.setOnTouchListener((view, touch) -> {
                    if (touch.getActionMasked() == MotionEvent.ACTION_DOWN) view.performClick();
                    return true;
                });
            }
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            // An accessibility overlay may cover the navigation bar even when
            // the platform normally fits application windows to system bars.
            params.setFitInsetsTypes(0);
            WindowMetrics metrics = windows.getMaximumWindowMetrics();
            WindowInsets insets = metrics.getWindowInsets();
            Insets bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars());
            Insets safe = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int right = safe.right, bottom = safe.bottom;
            if (bars.left == 0 && bars.right == 0 && bars.bottom == 0) {
                // Some ROMs omit navigation insets for this window type.
                int navSpace = Look.dp(this, 56);
                bottom = Math.max(bottom, navSpace);
                if (metrics.getBounds().width() > metrics.getBounds().height())
                    right = Math.max(right, navSpace);
            }
            params.x = right + Look.dp(this, 12);
            params.y = bottom + Look.dp(this, 12);
            params.setTitle("SkriNim — остановить прокрутку");
            stopButton.setVisibility(View.VISIBLE);
            windows.addView(stopButton, params);
            showing = true;
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Occupied pixels in full-display screenshot coordinates. Only valid after layout. */
    Rect stopBounds() {
        if (!showing || stopButton == null || !stopButton.isLaidOut()
                || !stopButton.isAttachedToWindow()) return null;
        int[] location = new int[2];
        stopButton.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + stopButton.getWidth(),
                location[1] + stopButton.getHeight());
    }

    /** Called once when scrolling stops, before obtaining the clean final viewport. */
    boolean hideStop() {
        if (!showing) return true;
        try {
            windows.removeViewImmediate(stopButton);
        } catch (IllegalArgumentException detached) {
            // The system already removed this service's window.
        } catch (RuntimeException unavailable) {
            stopButton.setVisibility(View.GONE);
            return false;
        }
        if (stopButton.isAttachedToWindow()) return false;
        showing = false;
        return true;
    }

    boolean supportsCaptureViewport(int width, int height) {
        return captureGeometry(width, height) != null;
    }

    @SuppressWarnings("deprecation")
    private CaptureGeometry captureGeometry(int width, int height) {
        try {
            Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) return null;
            Point size = new Point();
            display.getRealSize(size);
            if (width == size.x && height == size.y)
                return CaptureGeometry.resolve(size.x,size.y,width,height,0,0,0,0,false);
            if (Build.VERSION.SDK_INT < 34 || source == null || !source.hasFullscreenSource()) return null;
            WindowMetrics metrics = getSystemService(WindowManager.class).getMaximumWindowMetrics();
            Rect bounds = metrics.getBounds();
            if (bounds.left != 0 || bounds.top != 0 || bounds.width() != size.x || bounds.height() != size.y)
                return null;
            Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return CaptureGeometry.resolve(size.x,size.y,width,height,bars.left,bars.top,bars.right,bars.bottom,true);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    boolean swipe(int captureWidth, int captureHeight, boolean upward, boolean alternate, boolean shortSwipe,
                  Consumer<Boolean> completed) {
        if (current != this || stopRequested == null || !showing) return false;
        try {
            CaptureGeometry area = captureGeometry(captureWidth, captureHeight);
            if (area == null) return false;
            Path path = new Path();
            float x = area.displayX(alternate ? .35f : .50f);
            float top = area.displayY(alternate ? .50f : .36f);
            float bottom = area.displayY(alternate ? .78f : .70f);
            if (shortSwipe) {
                // Keep a sparse page's few landmarks visible in consecutive frames.
                top = area.displayY(alternate ? .55f : .46f);
                bottom = top + area.height * .16f;
            }
            // To scroll toward earlier content, move the finger downward.
            path.moveTo(x, upward ? top : bottom);
            path.lineTo(x, upward ? bottom : top);
            GestureDescription gesture = new GestureDescription.Builder()
                    .setDisplayId(Display.DEFAULT_DISPLAY)
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 750)).build();
            int activeSession = session;
            return dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gesture) {
                    if (session == activeSession) completed.accept(true);
                }
                @Override public void onCancelled(GestureDescription gesture) {
                    if (session == activeSession) completed.accept(false);
                }
            }, null);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** True only when there is no Stop window left to contaminate the final capture. */
    boolean end() {
        session++;
        boolean active = observingShot || interrupted != null;
        observingShot = false;
        shotOrigin = returnOrigin = null;
        motion = ScrollMotion.empty();
        lastScrollReport = 0;
        stopRequested = null;
        interrupted = null;
        source = null;
        if (active) watchWindows(false);
        boolean removedRecovery = hideRecovery();
        if (!hideStop() || !removedRecovery) return false;
        stopButton = null;
        windows = null;
        return true;
    }

    private void interruptSession() {
        Runnable callback = interrupted;
        end();
        if (callback != null) callback.run();
    }

    @Override public void onInterrupt() { interruptSession(); }

    private void disconnect() {
        if (current == this) current = null;
        interruptSession();
    }

    @Override public boolean onUnbind(Intent intent) {
        disconnect();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        disconnect();
        super.onDestroy();
    }
}
