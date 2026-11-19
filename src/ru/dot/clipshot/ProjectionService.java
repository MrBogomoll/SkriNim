package ru.dot.clipshot;

import android.app.Activity;
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
import java.util.function.Consumer;

public final class ProjectionService extends Service {
    private static final String START = "ru.dot.clipshot.projection.START";
    private static final String STOP = "ru.dot.clipshot.projection.STOP";
    private static final String RESULT = "result";
    private static final String CONSENT = "consent";
    private static final String CHANNEL = "screen_capture";
    private static final int NOTICE_ID = 91;

    static ProjectionService current;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Consumer<Bitmap> pending;
    private int width, height, density;
    private boolean stopping, receiverRegistered;
    private FloatingCapture floating;
    private boolean bubbleBusy;
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
        if (!ready()) return;
        IdleSettings settings = new IdleSettings(this);
        if (settings.enabled()) main.postDelayed(idleStop, settings.seconds() * 1000L);
    }

    private void onIdle() {
        if (!ready() || !new IdleSettings(this).enabled()) return;
        if (pending != null || bubbleBusy) {
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
            if (w > 0 && h > 0) resize(w, h, getResources().getConfiguration().densityDpi);
        }
    };

    public static Intent startIntent(Context context, int resultCode, Intent data) {
        return new Intent(context, ProjectionService.class).setAction(START)
                .putExtra(RESULT, resultCode).putExtra(CONSENT, data);
    }

    public static Intent stopIntent(Context context) {
        return new Intent(context, ProjectionService.class).setAction(STOP);
    }

    public boolean ready() { return !stopping && display != null; }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (ready()) return START_NOT_STICKY;
        if (stopping || intent == null || !START.equals(intent.getAction())) {
            shutdown();
            return START_NOT_STICKY;
        }

        try {
            startForeground(NOTICE_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
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
            }
        } catch (RuntimeException error) {
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
                .setContentText("Снимок делается только по нажатию на плитку или кнопку.")
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
        if (ready() && !bubbleBusy && !locked() && !floatingBlocked() && !app().editorOpening()
                && FloatingCapture.enabled(this) && FloatingCapture.allowed(this)) {
            floating.show();
        } else floating.hide();
    }

    void onOwnActivityChanged(Activity activity) {
        refreshFloating();
    }

    private void onBubbleTap() {
        if (bubbleBusy || pending!=null || !ready() || locked() || floatingBlocked()
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

    private void onBubbleBitmap(Bitmap bitmap) {
        if (bitmap==null || !ready() || floatingBlocked() || locked()
                || !FloatingCapture.enabled(this) || !FloatingCapture.allowed(this)) {
            if (bitmap!=null) bitmap.recycle();
            bubbleBusy=false;
            refreshFloating();
            if (ready() && !locked() && !floatingBlocked())
                Toast.makeText(this,"Снимок не сделан. Попробуй ещё раз.",Toast.LENGTH_SHORT).show();
            return;
        }
        app().openOrReturn(this,bitmap);
        bubbleBusy=false;
        refreshFloating();
    }

    public void capture(Consumer<Bitmap> callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> capture(callback));
            return;
        }
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
            if (!locked()) result = toBitmap(frame);
        } catch (RuntimeException | OutOfMemoryError error) {
            // Respond with null on capture failure.
        }
        complete(result);
    }

    private Bitmap toBitmap(Image frame) {
        Image.Plane plane = frame.getPlanes()[0];
        final int w = frame.getWidth(), h = frame.getHeight();
        final int pixelStride = plane.getPixelStride(), rowStride = plane.getRowStride();
        if (pixelStride != 4 || rowStride < w * 4)
            throw new IllegalStateException("Unexpected RGBA image layout");
        ByteBuffer pixels = plane.getBuffer();
        if (rowStride != w * 4) {
            ByteBuffer packed = ByteBuffer.allocateDirect(w * h * 4);
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
        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        try {
            result.copyPixelsFromBuffer(pixels);
            return result;
        } catch (RuntimeException error) {
            result.recycle();
            throw error;
        }
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
        try {
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

    private void complete(Bitmap bitmap) {
        main.removeCallbacks(timeout);
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
