package ru.dot.clipshot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

final class StrokeSlider extends View {
    interface Change { void set(int width); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path track = new Path();
    private final Look look;
    private final int min, max, pivot;
    private final boolean opacity, pixelSize;
    private final int ink;
    private final Change change;
    private int width;

    StrokeSlider(Context context, Look look, int min, int max, int width, int ink,
            boolean opacity, boolean pixelSize, Change change) {
        this(context,look,min,max,width,ink,opacity,pixelSize,0,change);
    }

    StrokeSlider(Context context, Look look, int min, int max, int width,int ink,
            boolean opacity,boolean pixelSize,int pivot,Change change) {
        super(context);
        this.look = look;
        this.min = min;
        this.max = max;
        this.pivot = pivot;
        this.opacity = opacity;
        this.pixelSize = pixelSize;
        this.width = width;
        this.ink = ink;
        this.change = change;
        setFocusable(true);
        setClickable(true);
        setContentDescription(pixelSize ? "Размер блока мозаики" : opacity ? "Непрозрачность линии" : "Толщина линии");
    }

    private int dp(float value) { return Look.dp(getContext(), value); }
    private float top() { return dp(23); }
    private float bottom() { return Math.max(top() + 1, getHeight() - dp(23)); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float x = getWidth() / 2f, top = top(), bottom = bottom();
        float y = bottom - (bottom - top) * StrokeSize.sliderFraction(min,max,pivot,width);

        track.rewind();
        track.moveTo(x - dp(opacity ? 5 : 10), top);
        track.lineTo(x + dp(opacity ? 5 : 10), top);
        track.lineTo(x + dp(opacity ? 5 : 2), bottom);
        track.lineTo(x - dp(opacity ? 5 : 2), bottom);
        track.close();
        paint.setColor(look.border);
        canvas.drawPath(track, paint);
        canvas.save();
        canvas.clipRect(0, y, getWidth(), getHeight());
        paint.setColor(look.accent);
        canvas.drawPath(track, paint);
        canvas.restore();

        paint.setColor(look.panel);
        canvas.drawCircle(x, y, dp(19), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(isFocused() ? 3 : 2));
        paint.setColor(look.accent);
        canvas.drawCircle(x, y, dp(18), paint);
        paint.setStyle(Paint.Style.FILL);

        if (opacity) {
            float radius = dp(11);
            paint.setColor(look.border);
            canvas.drawCircle(x, y, radius, paint);
            canvas.save();
            canvas.clipRect(x - radius, y - radius, x, y + radius);
            paint.setColor(look.muted);
            canvas.drawCircle(x, y, radius, paint);
            canvas.restore();
            paint.setColor(InkOpacity.apply(ink, width));
            canvas.drawCircle(x, y, radius, paint);
        } else if (pixelSize) {
            float half = Math.max(dp(2), dp(11) * width / max);
            paint.setColor(look.muted);
            canvas.drawRect(x - half, y - half, x + half, y + half, paint);
        } else {
            float radius = Math.max(dp(1.5f), dp(12) * width / max);
            paint.setColor(look.muted);
            canvas.drawCircle(x, y, radius + (ink == 0xffffffff ? dp(1) : 0), paint);
            paint.setColor(ink);
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private boolean select(int value) {
        value = Math.max(min, Math.min(max, value));
        if (value == width) return false;
        width = value;
        change.set(width);
        invalidate();
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                setPressed(true);
                select(StrokeSize.sliderValue(min,max,pivot,(bottom()-event.getY())/(bottom()-top())));
                return true;
            case MotionEvent.ACTION_MOVE:
                select(StrokeSize.sliderValue(min,max,pivot,(bottom()-event.getY())/(bottom()-top())));
                return true;
            case MotionEvent.ACTION_UP:
                select(StrokeSize.sliderValue(min,max,pivot,(bottom()-event.getY())/(bottom()-top())));
                setPressed(false);
                performClick();
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (select(width + (keyCode == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1))) {
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(SeekBar.class.getName());
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, min, max, width));
        info.setStateDescription(width + (opacity ? "%" : " пикс."));
        info.setScrollable(true);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        if (width > min) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        if (width < max) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        boolean changed;
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && args != null) {
            changed = select(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));
        } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            changed = select(width + 1);
        } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            changed = select(width - 1);
        } else return super.performAccessibilityAction(action, args);
        if (changed) sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        return changed;
    }
}
