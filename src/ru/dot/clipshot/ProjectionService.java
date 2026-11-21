package ru.dot.clipshot;

import android.app.Activity;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Display;
import android.widget.Toast;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ProjectionService extends Service {
    private static final String START = "ru.dot.clipshot.projection.START";
    private static final String STOP = "ru.dot.clipshot.projection.STOP";
    private static final String RESULT = "result";
    private static final String CONSENT = "consent";
    private static final String GRANT_SEQUENCE = "grantSequence";
    private static final String ANNOUNCE_READY = "announceReady";
    private static final String CHANNEL = "screen_capture";
    private static final int NOTICE_ID = 91;

    static ProjectionService current;
    // The app picker can foreground the chosen task before it delivers RESULT_OK.
    // Keep its unused grant in this process until a visible user action starts the service.
    private static Intent pendingConsent;
    private static boolean consentDispatched;
    private static int grantSequence;
    private static Context pendingContext;
    private static final Handler consentHandler = new Handler(Looper.getMainLooper());
    private static final Runnable expireConsent = ProjectionService::clearConsent;
    private static final BroadcastReceiver cancelConsentOnLock = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { clearConsent(); }
    };
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Consumer<Bitmap> pending;
    private ScrollGestureService initialObserver;
    private Bitmap initialCandidate;
    private InitialViewport initialPattern;
    private ByteBuffer initialPacking;
    private boolean initialMoved;
    private long initialDeadline, nextMotionSample;
    private final Runnable finishInitial = () -> complete(initialCandidate);
    private int width, height, density;
    private boolean contentVisible = true;
    private boolean observedHiddenWindow, partialGeometry;
    private boolean stopping, receiverRegistered;
    private FloatingCapture floating;
    private boolean bubbleBusy;
    private ScrollCapture scroll;
    private Toast scrollNotice;
    private long floatingReadyAt;
    private int floatingFrames;
    private static final long OVERLAY_SETTLE_MS = 34;
    private final Choreographer.FrameCallback settleFrame = this::onOverlayFrame;
    private final Runnable captureFloating = this::captureAfterBubble;
    private final Runnable idleStop = this::onIdle;

    static void refreshFloatingButton() {
        ProjectionService service=current;
        if (service!=null) service.refreshFloating();
    }
    
    static void refreshIdleTimer() {
        ProjectionService service = current;
        if (service != null) service.scheduleIdle();
    }

    private void scheduleIdle() {
        main.removeCallbacks(idleStop);
        if (!ready() || scrolling()) return;
        IdleSettings settings = new IdleSettings(this);
        if (settings.enabled()) main.postDelayed(idleStop, settings.seconds() * 1000L);
    }

    private void onIdle() {
        if (!ready() || !new IdleSettings(this).enabled()) return;
        if (pending != null || bubbleBusy || scrolling()) {
            main.postDelayed(idleStop, 500L);
            return;
        }
        shutdown();
    }
    private final Runnable timeout = () -> complete(null);

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) shutdown();
        }
    };

    private final MediaProjection.Callback projectionEvents = new MediaProjection.Callback() {
        @Override public void onStop() { shutdown(); }

        @Override public void onCapturedContentResize(int w, int h) {
            if (stopping || w <= 0 || h <= 0) return;
            Point full = displaySize();
            partialGeometry = full.x != w || full.y != h;
            resize(w, h, getResources().getConfiguration().densityDpi);
        }

        @Override public void onCapturedContentVisibilityChanged(boolean visible) {
            if (stopping) return;
            contentVisible = visible;
            if (visible) return;
            if (!locked()) observedHiddenWindow = true;
            if (scroll != null) scroll.onSourceHidden();
            clearInitial(null);
            initialMoved = false;
        }
    };

    static boolean acceptConsent(Activity owner, int code, Intent grant) {
        if (code != Activity.RESULT_OK || grant == null) return false;
        if (current != null && current.ready()) return true;
        if (!consentDispatched) {
            clearConsent();
            Context context = owner.getApplicationContext();
            IntentFilter locks = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(cancelConsentOnLock, locks, Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(cancelConsentOnLock, locks);
            pendingContext = context;
            pendingConsent = new Intent(owner, ProjectionService.class).setAction(START)
                    .putExtra(RESULT, code).putExtra(CONSENT, grant).putExtra(GRANT_SEQUENCE, ++grantSequence);
            consentHandler.postDelayed(expireConsent, 5 * 60_000L);
        }
        return dispatchConsent(owner, owner instanceof CaptureActivity);
    }

    static boolean hasPendingConsent() { return pendingConsent != null; }

    static boolean resumeConsent(Activity owner) {
        return dispatchConsent(owner, owner instanceof SetupActivity);
    }

    private static boolean dispatchConsent(Activity owner, boolean announceReady) {
        if (pendingConsent == null || owner.isFinishing()) return false;
        if (consentDispatched) return true;
        try {
            owner.startForegroundService(new Intent(pendingConsent).putExtra(ANNOUNCE_READY, announceReady));
            consentDispatched = true;
            return true;
        } catch (ForegroundServiceStartNotAllowedException chosenTaskIsFront) {
            explainDeferredConsent(owner);
        } catch (RuntimeException invalid) {
            clearConsent();
            Toast.makeText(owner, "Не удалось начать сеанс. Запроси разрешение ещё раз.", Toast.LENGTH_LONG).show();
        }
        return false;
    }

    private static void explainDeferredConsent(Context context) {
        Toast.makeText(context, "Приложение выбрано. Для запуска нажми плитку SkriNim или открой SkriNim.",
                Toast.LENGTH_LONG).show();
    }

    private static void clearConsent() {
        consentHandler.removeCallbacks(expireConsent);
        if (pendingContext != null) pendingContext.unregisterReceiver(cancelConsentOnLock);
        pendingContext = null;
        pendingConsent = null;
        consentDispatched = false;
    }

    public static Intent stopIntent(Context context) {
        return new Intent(context, ProjectionService.class).setAction(STOP);
    }

    public boolean ready() { return !stopping && display != null; }

    boolean appWindowCapture() { return partialGeometry || observedHiddenWindow; }

    String captureFailureMessage() {
        return contentVisible ? "Снимок не сделан. Попробуй ещё раз."
                : "Для снимка открой приложение, выбранное в начале сеанса.";
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            clearConsent();
            shutdown();
            return START_NOT_STICKY;
        }
        if (ready()) return START_NOT_STICKY;
        if (stopping || intent == null || !START.equals(intent.getAction()) || pendingConsent == null) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (intent.getIntExtra(GRANT_SEQUENCE, -1) != grantSequence) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        try {
            try {
                startForeground(NOTICE_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } catch (ForegroundServiceStartNotAllowedException chosenTaskIsFront) {
                consentDispatched = false;
                explainDeferredConsent(this);
                shutdown();
                return START_NOT_STICKY;
            }
            clearConsent();
            int resultCode = intent.getIntExtra(RESULT, Activity.RESULT_CANCELED);
            Intent consent = Build.VERSION.SDK_INT >= 33 ? intent.getParcelableExtra(CONSENT, Intent.class)
                                                        : intent.getParcelableExtra(CONSENT);
            if (resultCode != Activity.RESULT_OK || consent == null || locked()) {
                shutdown();
                return START_NOT_STICKY;
            }
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screen, filter);
            receiverRegistered = true;

            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            projection = manager.getMediaProjection(resultCode, consent);
            if (projection == null) {
                shutdown();
                return START_NOT_STICKY;
            }
            projection.registerCallback(projectionEvents, main);
            Point size = displaySize();
            width = size.x;
            height = size.y;
            density = getResources().getConfiguration().densityDpi;
            display = projection.createVirtualDisplay("ClipShot", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, main);
            if (display == null || locked()) shutdown();
            else {
                current = this;
                floating = new FloatingCapture(this,this::onBubbleTap);
                refreshFloating();
                scheduleIdle();
                if (intent.getBooleanExtra(ANNOUNCE_READY, false)) {
                    scrollNotice = Toast.makeText(this, FloatingCapture.enabled(this)
                            ? "Сеанс запущен. Для снимка нажми плитку или плавающую кнопку."
                            : "Сеанс запущен. Для снимка нажми плитку.", Toast.LENGTH_SHORT);
                    scrollNotice.show();
                }
            }
        } catch (RuntimeException error) {
            clearConsent();
            Toast.makeText(this, "Не удалось начать сеанс. Запроси разрешение ещё раз.", Toast.LENGTH_LONG).show();
            shutdown();
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Захват экрана", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Показывает, когда разрешён захват экрана.");
        manager.createNotificationChannel(channel);
        int immutable = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent stop = PendingIntent.getService(this, 90, stopIntent(this), immutable);
        PendingIntent setup = PendingIntent.getActivity(this, 91,
                new Intent(this, SetupActivity.class), immutable);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Захват экрана включён")
                .setContentText("Снимай экран плиткой или плавающей кнопкой.")
                .setContentIntent(setup)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Остановить", stop).build())
                .build();
    }

    private boolean locked() {
        PowerManager power = getSystemService(PowerManager.class);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        return power == null || !power.isInteractive() || keyguard == null || keyguard.isKeyguardLocked();
    }

    private boolean floatingBlocked() {
        return app().blocksFloatingButton();
    }
    private SkriNimApp app() { return (SkriNimApp)getApplication(); }

    private void refreshFloating() {
        if (floating==null) return;
        if (ready() && !scrolling() && !bubbleBusy && !locked() && !floatingBlocked() && !app().editorOpening()
                && FloatingCapture.enabled(this) && FloatingCapture.allowed(this)) {
            floating.show();
        } else floating.hide();
    }

    void onOwnActivityChanged(Activity activity) {
        if (scroll != null && (activity instanceof SetupActivity || activity instanceof CropActivity))
            scroll.onAppOpened();
        refreshFloating();
    }

    private void onBubbleTap() {
        if (scrolling() || bubbleBusy || pending!=null || !ready() || locked() || floatingBlocked()
                || floating==null || !floating.visible()
                || !FloatingCapture.enabled(this) || !FloatingCapture.allowed(this)) return;
        if (app().returnToEditor(this)) return;
        bubbleBusy=true;
        // Remove the actual window before arming any frame reader. An unavailable
        // permissions/system operation must not turn into a capture of the bubble.
        if (!floating.hide()) {
            bubbleBusy=false;
            Toast.makeText(this,"Не удалось скрыть кнопку. Снимок не сделан.",Toast.LENGTH_SHORT).show();
            return;
        }
        floatingReadyAt=SystemClock.uptimeMillis()+OVERLAY_SETTLE_MS;
        floatingFrames=2;
        Choreographer.getInstance().postFrameCallback(settleFrame);
        // A stalled compositor should restore the control, not hang or take a stale shot.
        main.postDelayed(captureFloating,500);
    }

    private void onOverlayFrame(long frameTimeNanos) {
        if (!bubbleBusy || stopping) return;
        if (--floatingFrames>0) {
            Choreographer.getInstance().postFrameCallback(settleFrame);
            return;
        }
        main.removeCallbacks(captureFloating);
        main.postAtTime(captureFloating,floatingReadyAt);
    }

    private void captureAfterBubble() {
        Choreographer.getInstance().removeFrameCallback(settleFrame);
        if (!bubbleBusy) return;
        if (floatingFrames!=0 || !ready() || locked() || floatingBlocked()
                || !FloatingCapture.enabled(this) || !FloatingCapture.allowed(this)) {
            bubbleBusy=false;
            refreshFloating();
            return;
        }
        if (app().returnToEditor(this)) {
            bubbleBusy=false;
            refreshFloating();
            return;
        }
        // capture() creates a new ImageReader and attaches it only now; no
        // pre-hide frame ever entered this reader's queue.
        capture(this::onBubbleBitmap);
    }

    private void onBubbleBitmap(Bitmap bitmap, ScrollGestureService.Origin origin) {
        if (bitmap==null || !ready() || floatingBlocked() || locked()
                || !FloatingCapture.enabled(this) || !FloatingCapture.allowed(this)) {
            if (bitmap!=null) bitmap.recycle();
            bubbleBusy=false;
            refreshFloating();
            if (ready() && !locked() && !floatingBlocked())
                Toast.makeText(this,captureFailureMessage(),Toast.LENGTH_SHORT).show();
            return;
        }
        app().openOrReturn(this,bitmap,origin);
        bubbleBusy=false;
        refreshFloating();
    }

    boolean scrolling() { return scroll != null; }

    boolean startScrollingFromEditor(CropActivity editor, Bitmap original, boolean upward) {
        ScrollPage page = ShotMemory.page();
        if (page == null || page.image != original || scrolling()) return false;
        ScrollGestureService gestures = ScrollGestureService.current;
        if (gestures == null || !ready() || locked() || bubbleBusy || pending != null) {
            Toast.makeText(this, "Не удалось начать прокрутку.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (width != page.viewport.getWidth() || height != page.viewport.getHeight()) {
            Toast.makeText(editor, "Размер экрана изменился. Сделай новый снимок.", Toast.LENGTH_LONG).show();
            return false;
        }
        int limit = ScrollStitcher.maxOutputHeight(width);
        if (original.getHeight() >= limit) {
            Toast.makeText(editor, ScrollCapture.sizeLimitMessage(limit), Toast.LENGTH_LONG).show();
            return false;
        }
        if (floating != null && !floating.hide()) {
            Toast.makeText(editor, "Не удалось скрыть плавающую кнопку.", Toast.LENGTH_SHORT).show();
            return false;
        }
        clearScrollNotice();
        main.removeCallbacks(idleStop);
        boolean canReplaceSeed = page.image == page.viewport && !editor.hasScreenshotEdits();
        scroll = new ScrollCapture(this, gestures, width, height, page, upward, canReplaceSeed);
        if (!contentVisible) scroll.onSourceHidden();
        boolean sourceAlreadyExposed = !editor.hasWindowFocus() && canReadTarget();
        if (!scroll.watchSource() || (!sourceAlreadyExposed && !editor.moveTaskToBack(true))) {
            scroll.finish("Не удалось вернуться к исходной странице.");
            return false;
        }
        scroll.start();
        return true;
    }

    boolean targetVisible() {
        return contentVisible && canReadTarget();
    }

    boolean canReadTarget() {
        return ready() && !locked() && !floatingBlocked() && !app().editorOpening();
    }

    void onScrollResult(ScrollCapture source, ScrollPage result, String message) {
        if (scroll != source) {
            if (result != null) result.discardComparedTo(source.page);
            return;
        }
        // An interrupted capture may finish before its last reader returns.
        if (pending != null) complete(null);
        scroll = null;
        if (!ready() || locked()) {
            if (result != null) result.discardComparedTo(source.page);
            scheduleIdle();
            return;
        }
        app().finishEditorScroll(this, source.page, result);
        if (result != null && ShotMemory.page() == result) {
            int growth = result.image.getHeight() - source.page.image.getHeight();
            if (growth > 0) {
                String added = "Добавлено " + growth + " пикс. Высота снимка — "
                        + result.image.getHeight() + " пикс.";
                message = message == null ? added : message + "\n" + added;
            }
        }
        if (message != null) {
            clearScrollNotice();
            scrollNotice = Toast.makeText(this, message, Toast.LENGTH_LONG);
            scrollNotice.show();
        }
        refreshFloating();
        scheduleIdle();
    }

    void capture(BiConsumer<Bitmap, ScrollGestureService.Origin> callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> capture(callback));
            return;
        }
        if (scrolling() || pending != null || !ready() || locked()) {
            callback.accept(null, null);
            return;
        }
        clearScrollNotice();
        ScrollGestureService observer = ScrollGestureService.current;
        if (observer != null) observer.observeShot();
        initialObserver = observer;
        initialMoved = false;
        captureFrame(bitmap -> {
            ScrollGestureService.Origin origin = observer == null ? null : observer.finishShot(initialMoved);
            callback.accept(bitmap, origin);
        });
    }

    private void clearScrollNotice() {
        if (scrollNotice != null) scrollNotice.cancel();
        scrollNotice = null;
    }

    void captureFrame(Consumer<Bitmap> callback) {
        if (!ready() || pending != null || locked()) {
            callback.accept(null);
            return;
        }
        pending = callback;
        try {
            attachReader();
            main.postDelayed(timeout, 2400);
        } catch (RuntimeException | OutOfMemoryError error) {
            complete(null);
        }
    }

    private void attachReader() {
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(this::onImage, main);
        display.setSurface(reader.getSurface());
    }

    private void onImage(ImageReader source) {
        if (source != reader || pending == null) return;
        Bitmap result = null;
        try (Image frame = source.acquireLatestImage()) {
            if (frame == null) return;
            if (!locked()) {
                // Reattaching a surface can wake projection visibility callbacks.
                // Wait for the selected window to become visible before using pixels.
                if (!contentVisible) return;
                long now = SystemClock.uptimeMillis();
                if (initialCandidate != null && now < nextMotionSample) return;
                Image.Plane plane = frame.getPlanes()[0];
                int w = frame.getWidth(), h = frame.getHeight(), stride = plane.getRowStride();
                if (plane.getPixelStride() != 4 || stride < w * 4)
                    throw new IllegalStateException("Unexpected RGBA image layout");
                ByteBuffer pixels = plane.getBuffer();
                // A new reader can receive a transparent surface before composition.
                if (!hasVisiblePixels(pixels, w, h, stride)) return;
                if (initialObserver == null) {
                    result = toBitmap(pixels, w, h, stride);
                } else {
                    InitialViewport pattern = new InitialViewport(pixels, w, h, stride);
                    if (initialCandidate == null || pattern.scrolledFrom(initialPattern)) {
                        // Follow only a coherent scroll. Reuse the full-size bitmap and row
                        // packing buffer throughout the fling, including on high-DPI screens.
                        if (initialCandidate == null) initialDeadline = now + 1200;
                        else initialMoved = true;
                        initialCandidate = toBitmap(pixels, w, h, stride);
                        initialPattern = pattern;
                        nextMotionSample = now + 65;
                        // Static virtual displays need not deliver another frame.
                        main.removeCallbacks(finishInitial);
                        main.postAtTime(finishInitial, Math.min(initialDeadline, now + 145));
                        return;
                    }
                    // A still frame or unrelated animation keeps the last verified frame.
                    result = initialCandidate;
                }
            }
        } catch (RuntimeException | OutOfMemoryError error) {
            // The completion callback restores the editor/controls on failure.
        }
        complete(result);
    }

    private Bitmap toBitmap(ByteBuffer pixels, int w, int h, int rowStride) {
        if (rowStride != w * 4) {
            ByteBuffer packed = initialPacking;
            if (packed == null) {
                packed = ByteBuffer.allocateDirect(w * h * 4);
                if (initialObserver != null) initialPacking = packed;
            }
            packed.clear();
            ByteBuffer row = pixels.duplicate();
            int sourceOffset = row.position();
            for (int y = 0; y < h; ++y) {
                int start = sourceOffset + y * rowStride;
                row.limit(row.capacity()).position(start).limit(start + w * 4);
                packed.put(row);
            }
            packed.flip();
            pixels = packed;
        }
        Bitmap result = initialCandidate != null ? initialCandidate : Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        try {
            result.copyPixelsFromBuffer(pixels);
            return result;
        } catch (RuntimeException error) {
            if (result != initialCandidate) result.recycle();
            throw error;
        }
    }

    static boolean hasVisiblePixels(ByteBuffer rgba, int width, int height, int stride) {
        int start = rgba.position() + 3;
        int middle = start + (height / 2) * stride + (width / 2) * 4;
        if (rgba.get(middle) != 0) return true;
        for (int y = 0; y < height; y++) {
            int row = start + y * stride;
            for (int x = 0; x < width; x++)
                if (rgba.get(row + x * 4) != 0) return true;
        }
        return false;
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (floating!=null) floating.relayout();
        if (display == null || stopping) return;
        // API 34+ reports the captured content size directly, including app-window sharing.
        if (Build.VERSION.SDK_INT < 34) {
            Point size = displaySize();
            resize(size.x, size.y, config.densityDpi);
        }
    }

    @SuppressWarnings("deprecation")
    private Point displaySize() {
        // This is a foreground service, not an Activity/window context.
        Display source = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        if (source == null) throw new IllegalStateException("Default display is unavailable");
        Point real = new Point();
        source.getRealSize(real);
        return real;
    }

    private void resize(int w, int h, int dpi) {
        if (!ready() || w <= 0 || h <= 0 || (width == w && height == h && density == dpi)) return;
        if (scroll != null) scroll.finish("Размер экрана изменился.");
        try {
            if (initialCandidate != null) {
                // Android can report the selected task's real bounds just
                // after its first frame. Wait for a frame at the new size.
                clearInitial(null);
                initialMoved = false;
            }
            detachReader();
            display.resize(w, h, dpi);
            width = w;
            height = h;
            density = dpi;
            if (pending != null) attachReader();
        } catch (RuntimeException | OutOfMemoryError error) {
            shutdown();
        }
    }

    private void detachReader() {
        if (reader == null) return;
        ImageReader closing = reader;
        reader = null;
        try {
            if (display != null) display.setSurface(null);
        } finally {
            closing.close();
        }
    }

    private void clearInitial(Bitmap keep) {
        main.removeCallbacks(finishInitial);
        if (initialCandidate != null && keep != initialCandidate) initialCandidate.recycle();
        initialCandidate = null;
        initialPattern = null;
        initialPacking = null;
    }

    private void complete(Bitmap bitmap) {
        main.removeCallbacks(timeout);
        clearInitial(bitmap);
        initialObserver = null;
        Consumer<Bitmap> callback = pending;
        pending = null;
        try {
            detachReader();
        } catch (RuntimeException ignored) {}
        if (bitmap != null && ready()) scheduleIdle();
        if (callback != null) callback.accept(bitmap);
        else if (bitmap != null) bitmap.recycle();
    }

    private void shutdown() {
        release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void release() {
        if (stopping) return;
        stopping = true;
        clearScrollNotice();
        if (scroll != null) {
            scroll.cancel();
            scroll = null;
        }
        main.removeCallbacks(captureFloating);
        Choreographer.getInstance().removeFrameCallback(settleFrame);
        bubbleBusy=false;
        if (floating!=null) floating.hide();
        main.removeCallbacks(idleStop);
        if (current == this) current = null;
        complete(null);
        if (display != null) {
            display.release();
            display = null;
        }
        if (projection != null) {
            projection.unregisterCallback(projectionEvents);
            projection.stop();
            projection = null;
        }
        if (receiverRegistered) {
            unregisterReceiver(screen);
            receiverRegistered = false;
        }
    }

    @Override public void onDestroy() {
        release();
        super.onDestroy();
    }
}
