package ru.dot.clipshot;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One editor-initiated capture. UI and gestures use main; bitmap ownership belongs to the worker. */
final class ScrollCapture {
    private enum Phase { STARTING, SETTLING, READING, MATCHING, WAITING, SWIPING, FINAL_FRAME, ADJUSTING, FINISHED }

    private final ProjectionService projection;
    private final ScrollGestureService gestures;
    private final int width, height;
    final ScrollPage page;
    private final boolean upward, canReplaceSeed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ScrollStitcher stitcher;
    private long startDeadline = SystemClock.uptimeMillis() + 10000;
    private long startMotionDeadline = SystemClock.uptimeMillis() + 1200;
    private long hiddenReadSince = -1;
    private final Choreographer.FrameCallback finalFrame = this::afterRemovalFrame;
    private final Runnable timedOut = () -> finish("Длинный снимок остановлен: экран не ответил.");
    private Phase phase = Phase.STARTING;
    private boolean controls, stopRequested, swipingStarted, retriedUnchanged, alternateSwipe, shortSwipe;
    private int framesToWait, verifiedGeneration;
    private int mismatchGeneration = -1;
    private long mismatchSince;
    private ScrollStitcher.Result firstMiss;
    private boolean cleanProbe, checkingGap, checkingStart, stalled, reachedEnd;
    private int selectedGeneration = -1, sampleGeneration = -1;
    private Bitmap settlingSample;
    private ScrollMotion acceptedMotion;
    private long settleUntil;
    private String startupFailure = "Не удалось вернуться к исходной странице.";
    private String finishReason;

    ScrollCapture(ProjectionService projection, ScrollGestureService gestures, int width, int height,
                  ScrollPage page, boolean upward, boolean canReplaceSeed) {
        this.projection = projection;
        this.gestures = gestures;
        this.width = width;
        this.height = height;
        this.page = page;
        this.upward = upward;
        this.canReplaceSeed = canReplaceSeed && page.image == page.viewport;
        stitcher = new ScrollStitcher(upward, page);
    }

    void start() {
        main.postDelayed(this::waitForTarget, 250);
    }

    boolean watchSource() {
        return gestures.watchSource(() -> finish("Прокрутка остановлена: активное окно изменилось или служба прервана."));
    }

    private void waitForTarget() {
        if (phase != Phase.STARTING) return;
        long now = SystemClock.uptimeMillis();
        if (projection.targetVisible()) hiddenReadSince = -1;
        if (now >= startDeadline || (hiddenReadSince >= 0 && now - hiddenReadSince >= 2400)) {
            if (projection.targetVisible()) offerAdjustment();
            else finish(startupFailure);
        } else if (projection.canReadTarget() && !gestures.windowSettling() && readyToCompare()) {
            // Reattaching the reader may be what makes Android report that
            // the selected app is visible again. Pixels are gated on delivery.
            if (!projection.targetVisible() && hiddenReadSince < 0) hiddenReadSince = now;
            int generation = gestures.sourceGeneration();
            ScrollMotion before = gestures.scrollMotion();
            projection.captureFrame(bitmap -> checkFirstFrame(bitmap, generation, before));
        } else {
            main.postDelayed(this::waitForTarget, 75);
        }
    }

    // Nested carousels may report motion indefinitely on an otherwise stable page.
    // After the grace period, let the captured pixels establish its position.
    private boolean readyToCompare() {
        return !gestures.scrollSettling() || SystemClock.uptimeMillis() >= startMotionDeadline;
    }

    // Dismissing the editor can briefly reveal the launcher, the shade, or an
    // animation. No gestures are sent until a live frame matches the editor image.
    private void checkFirstFrame(Bitmap bitmap, int generation, ScrollMotion before) {
        if (phase != Phase.STARTING) {
            if (bitmap != null) bitmap.recycle();
            return;
        }
        if (bitmap == null) {
            retryStart(projection.targetVisible() ? "Не удалось получить кадр исходной страницы." : startupFailure);
            return;
        }
        if (!projection.targetVisible()) {
            bitmap.recycle();
            retryStart(startupFailure);
            return;
        }
        if (bitmap.getWidth() != width || bitmap.getHeight() != height
                || page.viewport.getWidth() != width || page.viewport.getHeight() != height) {
            bitmap.recycle();
            finish("Размер экрана изменился. Сделай новый снимок.");
            return;
        }
        ScrollMotion motion = motionAtFrame(before);
        boolean mayRebase = freshSource(generation);
        Bitmap prior = settlingSample;
        boolean compareSamples = prior != null && sampleGeneration == generation && mayRebase;
        settlingSample = null;
        worker.execute(() -> {
            try {
                ScrollPage.Start location = page.locate(bitmap);
                boolean settled = location == null && compareSamples
                        && ScreenshotViewport.settled(prior, bitmap);
                main.post(() -> acceptFirstFrame(bitmap, generation, location, settled, motion));
            } catch (RuntimeException | OutOfMemoryError error) {
                bitmap.recycle();
                main.post(() -> finish("Не удалось проверить исходную страницу."));
            } finally {
                if (prior != null) prior.recycle();
            }
        });
    }

    private boolean freshSource(int generation) {
        return canReplaceSeed && generation >= 0 && (selectedGeneration == generation
                || gestures.matchesOrigin(ShotMemory.origin()));
    }

    // An event between the reader request and delivery could describe a later
    // scroll position than the bitmap. In that case use only its pixels.
    private ScrollMotion motionAtFrame(ScrollMotion before) {
        return gestures.scrollMotion() == before ? before : null;
    }

    private void acceptFirstFrame(Bitmap bitmap, int generation, ScrollPage.Start location,
                                  boolean settled, ScrollMotion motion) {
        if (phase != Phase.STARTING) {
            bitmap.recycle();
            return;
        }
        boolean visible = projection.targetVisible();
        if (!visible || generation != gestures.sourceGeneration()) {
            bitmap.recycle();
            retryStart(visible ? "Не удалось вернуться к исходной странице." : startupFailure);
            return;
        }
        if (location == null) {
            long now = SystemClock.uptimeMillis();
            if (mismatchGeneration != generation) {
                mismatchGeneration = generation;
                mismatchSince = now;
            }
            boolean allowFresh = freshSource(generation) && readyToCompare();
            if (allowFresh && settled) {
                // A fling can carry the page past every pixel of an ordinary
                // screenshot. Only rebase on a stable view of the original
                // window, or one chosen explicitly on screen.
                location = page.here();
            } else {
                if (allowFresh) {
                    settlingSample = bitmap;
                    sampleGeneration = generation;
                } else bitmap.recycle();
                if (now - mismatchSince >= 1800) offerAdjustment();
                else retryStart("Не удалось совместить страницу со снимком.");
                return;
            }
        }
        if (!gestures.confirmVerifiedSource(generation)) {
            bitmap.recycle();
            retryStart("Не удалось подтвердить активное окно.");
            return;
        }
        mismatchGeneration = -1;
        verifiedGeneration = generation;
        acceptedMotion = motion;
        phase = Phase.MATCHING;
        ScrollPage.Start verified = location;
        worker.execute(() -> {
            try {
                ScrollStitcher.Result result = stitcher.begin(bitmap, verified);
                boolean sparse = stitcher.needsShortSwipe();
                main.post(() -> {
                    shortSwipe = sparse;
                    onMatch(result);
                });
            } catch (RuntimeException | OutOfMemoryError error) {
                main.post(() -> finish("Не удалось добавить исходную страницу."));
            }
        });
    }

    private void retryStart(String reason) {
        startupFailure = reason;
        if (SystemClock.uptimeMillis() >= startDeadline) finish(reason);
        else main.postDelayed(this::waitForTarget, 200);
    }

    private void retryBeforeFirstSwipe() {
        if (!gestures.clearControlsForRetry()) {
            finish("Не удалось скрыть кнопку остановки.");
            return;
        }
        controls = false;
        phase = Phase.STARTING;
        worker.execute(stitcher::close);
        retryStart("Не удалось подтвердить исходную страницу. Сделай новый снимок.");
    }

    private boolean targetReady() {
        if (projection.targetVisible() && ScrollGestureService.current == gestures) return true;
        finish("Прокрутка остановлена: исходная страница больше недоступна.");
        return false;
    }

    private void readFrame() {
        if (phase == Phase.FINISHED || phase == Phase.FINAL_FRAME || !targetReady()) return;
        if (stopRequested) {
            requestFinalFrame(finishReason);
            return;
        }
        if (gestures.scrollSettling() && SystemClock.uptimeMillis() < settleUntil) {
            main.postDelayed(this::readFrame, 100);
            return;
        }
        boolean maskStop = controls && !projection.appWindowCapture();
        Rect mask = maskStop ? gestures.stopBounds() : null;
        if (maskStop && (mask == null || mask.isEmpty())) {
            requestFinalFrame("Не удалось определить положение кнопки остановки.");
            return;
        }
        phase = Phase.READING;
        ScrollMotion before = gestures.scrollMotion();
        projection.captureFrame(bitmap -> onBitmap(bitmap, mask, false, before));
    }

    private void onBitmap(Bitmap bitmap, Rect mask, boolean last, ScrollMotion before) {
        if (phase != (last ? Phase.FINAL_FRAME : Phase.READING)) {
            if (bitmap != null) bitmap.recycle();
            return;
        }
        if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
            if (bitmap != null) bitmap.recycle();
            finish("Не удалось получить следующий кадр.");
            return;
        }
        phase = Phase.MATCHING;
        ScrollMotion motion = motionAtFrame(before);
        int distance = motion == null ? 0 : motion.distanceFrom(acceptedMotion, upward);
        worker.execute(() -> {
            try {
                ScrollStitcher.Result result = stitcher.add(bitmap, mask, distance);
                boolean sparse = stitcher.needsShortSwipe();
                main.post(() -> {
                    if (result == ScrollStitcher.Result.ADDED || (mask == null && result == ScrollStitcher.Result.UNCHANGED))
                        acceptedMotion = motion;
                    shortSwipe = sparse;
                    if (last) onCleanMatch(result);
                    else onMatch(result);
                });
            } catch (RuntimeException | OutOfMemoryError problem) {
                main.post(() -> finish("Не удалось добавить следующий кадр."));
            }
        });
    }

    private void onMatch(ScrollStitcher.Result result) {
        if (phase != Phase.MATCHING) return;
        if (!swipingStarted && result == ScrollStitcher.Result.NO_MATCH) {
            worker.execute(stitcher::close);
            offerAdjustment();
            return;
        }
        if (result == ScrollStitcher.Result.FIRST) {
            if (verifiedGeneration != gestures.sourceGeneration()) {
                retryBeforeFirstSwipe();
                return;
            }
            // This first frame is clean. Begin the swipe in the same main-loop
            // turn as showing Stop, while this verified foreground is still current.
            controls = gestures.begin(this::stopAtVisiblePage);
            if (!controls) {
                finish("Не удалось показать кнопку остановки прокрутки.");
                return;
            }
            phase = Phase.WAITING;
            swipe();
            return;
        }
        if (result == ScrollStitcher.Result.MEMORY) {
            finish("Не хватило памяти для следующего участка снимка.");
        } else if (result == ScrollStitcher.Result.LIMIT) {
            requestFinalFrame(sizeLimitMessage(ScrollStitcher.maxOutputHeight(width)));
        } else if (stopRequested) {
            requestFinalFrame(finishReason);
        } else if (result == ScrollStitcher.Result.NO_MATCH || result == ScrollStitcher.Result.UNCHANGED) {
            if (result == ScrollStitcher.Result.UNCHANGED && firstMiss == result && !retriedUnchanged) {
                // A pinned header or an embedded view may consume a central
                // gesture. Try the other path once, then keep it if it works.
                alternateSwipe = !alternateSwipe;
                scheduleSwipe(false);
            } else if (firstMiss != null) {
                // A fresh capture without the overlaid button can resolve ambiguity.
                cleanProbe = true;
                requestFinalFrame(null);
            } else {
                firstMiss = result;
                phase = Phase.SETTLING;
                main.postDelayed(this::readFrame, 400);
            }
        } else scheduleSwipe(true);
    }

    private void onCleanMatch(ScrollStitcher.Result result) {
        if (phase != Phase.MATCHING) return;
        if (result == ScrollStitcher.Result.MEMORY) {
            finish("Не хватило памяти для следующего участка снимка.");
            return;
        }
        if ((cleanProbe || checkingGap) && !stopRequested &&
                (result == ScrollStitcher.Result.ADDED || (checkingGap && result == ScrollStitcher.Result.UNCHANGED))) {
            controls = gestures.showStop();
            if (!controls) {
                finish("Не удалось вернуть кнопку остановки прокрутки.");
                return;
            }
            if (checkingGap) shortSwipe = true;
            checkingGap = false;
            scheduleSwipe(true);
            return;
        }
        if ((cleanProbe || checkingGap) && !stopRequested &&
                (result == ScrollStitcher.Result.NO_MATCH || (result == ScrollStitcher.Result.UNCHANGED
                    && (firstMiss == ScrollStitcher.Result.NO_MATCH || gestures.scrollMotion().hasMore(upward))))) {
            stalled = result == ScrollStitcher.Result.UNCHANGED && firstMiss == ScrollStitcher.Result.UNCHANGED;
            offerAdjustment();
            return;
        }
        reachedEnd = result == ScrollStitcher.Result.UNCHANGED && acceptedMotion != null
                && acceptedMotion == gestures.scrollMotion() && acceptedMotion.atEnd(upward);
        finish(finishReason, result);
    }

    private void scheduleSwipe(boolean moved) {
        firstMiss = null;
        cleanProbe = false;
        retriedUnchanged = !moved;
        phase = Phase.WAITING;
        main.postDelayed(this::swipe, 500);
    }

    private void swipe() {
        if (phase != Phase.WAITING || !targetReady()) return;
        if (!gestures.supportsCaptureViewport(width, height)) {
            finish("Для прокрутки разверни выбранное приложение на весь экран или разреши захват всего экрана.");
            return;
        }
        if (!swipingStarted) {
            if (!gestures.startSwiping(verifiedGeneration)) {
                retryBeforeFirstSwipe();
                return;
            }
            swipingStarted = true;
        }
        phase = Phase.SWIPING;
        main.postDelayed(timedOut, 2200);
        if (!gestures.swipe(width, height, upward, alternateSwipe, shortSwipe, this::onSwipe))
            requestFinalFrame("Не удалось прокрутить страницу.");
    }

    private void onSwipe(boolean complete) {
        if (phase != Phase.SWIPING) return;
        main.removeCallbacks(timedOut);
        if (!complete && !stopRequested) {
            finishReason = "Android прервал жест прокрутки.";
            stopRequested = true;
        }
        phase = Phase.SETTLING;
        settleUntil = SystemClock.uptimeMillis() + 1100;
        main.postDelayed(this::readFrame, 320);
    }

    private void stopAtVisiblePage() {
        stopRequested = true;
        // Let a gesture in progress settle; a pending read or match also runs to completion.
        if (phase == Phase.WAITING) requestFinalFrame(null);
    }

    private void offerAdjustment() {
        if (!targetReady()) return;
        main.removeCallbacksAndMessages(null);
        dropSettlingSample();
        if (!gestures.hideStop()) {
            finish("Не удалось скрыть кнопку остановки.");
            return;
        }
        controls = false;
        phase = Phase.ADJUSTING;
        boolean fresh = !swipingStarted && canReplaceSeed;
        String message;
        if (fresh) message = "Страница успела измениться. Начать длинный снимок с видимого экрана?";
        else if (!swipingStarted) message = "Вернись к уже снятому участку страницы и нажми «Проверить».";
        else if (checkingGap) message = "Совпадение ещё не найдено. Верни в кадр часть уже снятого текста или изображения.";
        else if (stalled) message = upward
                ? "Сдвинь страницу немного вверх вручную и нажми «Проверить»."
                : "Сдвинь страницу немного вниз вручную и нажми «Проверить».";
        else message = upward
                ? "Прокрути немного вниз, чтобы снова была видна часть предыдущего кадра."
                : "Прокрути немного вверх, чтобы снова была видна часть предыдущего кадра.";
        if (!gestures.showRecovery(message, fresh ? "Начать здесь" : "Проверить",
                () -> recheckAdjustment(fresh), () -> {
                    if (phase == Phase.ADJUSTING) finish("Прокрутка завершена. В редакторе открыт текущий снимок.");
                }))
            finish("Не удалось совместить кадры. В редакторе открыт собранный снимок.");
    }

    private void recheckAdjustment(boolean fresh) {
        if (phase != Phase.ADJUSTING || !targetReady()) return;
        // A real foreground switch dismisses this panel in the service. A scroll
        // on the page can also supply a previously missing window identity.
        int generation = gestures.sourceGeneration();
        if (!gestures.hideRecovery()) {
            finish("Не удалось скрыть подсказку прокрутки.");
            return;
        }
        finishReason = null;
        checkingStart = !swipingStarted;
        checkingGap = swipingStarted;
        cleanProbe = false;
        firstMiss = null;
        if (checkingStart) {
            selectedGeneration = fresh ? generation : -1;
            mismatchGeneration = -1;
            startDeadline = SystemClock.uptimeMillis() + 10000;
            startMotionDeadline = SystemClock.uptimeMillis() + 1200;
        }
        settleUntil = SystemClock.uptimeMillis() + 1100;
        phase = Phase.FINAL_FRAME;
        waitForCleanFrame();
    }

    private void requestFinalFrame(String reason) {
        if (phase == Phase.FINISHED || phase == Phase.FINAL_FRAME || !targetReady()) return;
        if (!swipingStarted) {
            finish(reason);
            return;
        }
        main.removeCallbacksAndMessages(null);
        finishReason = reason;
        phase = Phase.FINAL_FRAME;
        // Keep watching the source window until the clean frame is received.
        boolean detached = !controls || gestures.hideStop();
        controls = false;
        if (!detached) {
            finish("Не удалось снять последний кадр.");
            return;
        }
        waitForCleanFrame();
    }

    private void waitForCleanFrame() {
        // Wait until the compositor removes our controls before reading the page.
        framesToWait = 2;
        Choreographer.getInstance().postFrameCallback(finalFrame);
        main.postDelayed(timedOut, 1200);
    }

    private void afterRemovalFrame(long frameTime) {
        if (phase != Phase.FINAL_FRAME) return;
        if (--framesToWait > 0) {
            Choreographer.getInstance().postFrameCallback(finalFrame);
            return;
        }
        main.removeCallbacks(timedOut);
        if (!targetReady()) return;
        if (checkingStart) {
            checkingStart = false;
            phase = Phase.STARTING;
            waitForTarget();
        } else readCleanFrame();
    }

    private void readCleanFrame() {
        if (phase != Phase.FINAL_FRAME || !targetReady()) return;
        if (gestures.scrollSettling() && SystemClock.uptimeMillis() < settleUntil) {
            main.postDelayed(this::readCleanFrame, 100);
            return;
        }
        ScrollMotion before = gestures.scrollMotion();
        projection.captureFrame(bitmap -> onBitmap(bitmap, null, true, before));
    }

    void onAppOpened() {
        // SetupActivity can itself be the source returning behind the editor.
        // The first swipe is guarded by both window generation and visibility.
        if (swipingStarted || phase == Phase.ADJUSTING) finish("Прокрутка остановлена: открыто другое окно.");
    }

    void onSourceHidden() {
        String message = "Открой выбранное приложение на весь экран и повтори прокрутку.";
        if (swipingStarted || phase == Phase.ADJUSTING) finish(message);
        else startupFailure = message;  // The editor may still be sliding away.
    }

    void finish(String reason) { finish(reason, null); }

    private void finish(String reason, ScrollStitcher.Result closingResult) {
        if (!end()) return;
        boolean started = swipingStarted;
        worker.execute(() -> {
            ScrollPage output = null;
            try {
                // A worker can still be verifying a bridge when the foreground
                // changes. Only publish it after the first gesture passed its gate.
                if (started) output = stitcher.finishPage();
            } catch (RuntimeException | OutOfMemoryError problem) {
                // The editor still owns the original on failure.
            } finally {
                stitcher.close();
            }
            ScrollPage ready = output;
            String resultMessage = finishMessage(reason, closingResult, ready,
                    stitcher.shortenedOnFinish());
            main.post(() -> projection.onScrollResult(this, ready, resultMessage));
        });
        worker.shutdown();
    }

    static String sizeLimitMessage(int height) {
        return "Предел высоты снимка при этой ширине — " + height + " пикс.";
    }

    private String finishMessage(String reason, ScrollStitcher.Result closingResult,
                                 ScrollPage result, boolean shortened) {
        if (reason != null) return reason;
        if (result == null) return "Не удалось собрать снимок. В редакторе остался предыдущий кадр.";
        if (closingResult == ScrollStitcher.Result.LIMIT)
            return sizeLimitMessage(ScrollStitcher.maxOutputHeight(width));
        if (closingResult == ScrollStitcher.Result.MEMORY)
            return "Не хватило памяти для следующего кадра. В редакторе открыт собранный снимок.";
        if (closingResult == ScrollStitcher.Result.NO_MATCH)
            return "Не удалось совместить последний кадр. В редакторе открыт собранный снимок.";
        if (shortened) return "В редакторе открыта часть, которую удалось собрать.";
        if (reachedEnd && !stopRequested)
            return upward ? "Страница дальше вверх не прокручивается." : "Страница дальше вниз не прокручивается.";
        if (result.image == page.image && result.offset != page.offset)
            return "Прокручена уже снятая часть. Нажми стрелку ещё раз, чтобы продолжить.";
        if (!stopRequested && closingResult == ScrollStitcher.Result.UNCHANGED)
            return "Новых строк пока нет. Можно повторить прокрутку.";
        if (result.image == page.image) return "Новых строк в снимке нет.";
        return null;
    }

    void cancel() {
        if (!end()) return;
        worker.execute(stitcher::close);
        worker.shutdown();
    }

    private boolean end() {
        if (phase == Phase.FINISHED) return false;
        phase = Phase.FINISHED;
        dropSettlingSample();
        main.removeCallbacksAndMessages(null);
        Choreographer.getInstance().removeFrameCallback(finalFrame);
        gestures.end();
        controls = false;
        return true;
    }

    private void dropSettlingSample() {
        if (settlingSample != null) settlingSample.recycle();
        settlingSample = null;
    }
}
