package ru.dot.clipshot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.function.IntConsumer;

final class ColorPickerDialog {
    private final Activity context;
    private final Look look;
    private final IntConsumer onApply;
    private final float[] hsv = new float[3];
    private final EditText[] rgb = new EditText[3];
    private EditText hex;
    private View preview;
    private TextView error;
    private SeekBar brightness;
    private Wheel wheel;
    private Button apply;
    private int color;
    private boolean syncing;
    private int invalidField = 0;

    static void show(Activity context, Look look, int initialColor, IntConsumer onApply) {
        new ColorPickerDialog(context, look, initialColor, onApply).show();
    }

    private ColorPickerDialog(Activity context, Look look, int initialColor, IntConsumer onApply) {
        this.context = context;
        this.look = look;
        this.onApply = onApply;
        color = 0xff000000 | (initialColor & 0xffffff);
        Color.colorToHSV(color, hsv);
    }

    private int dp(float value) { return Look.dp(context, value); }

    private void show() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(12));
        root.setFocusableInTouchMode(true);
        root.setBackground(look.shape(context, look.panel, 20, false));

        TextView title = text("Выбрать цвет", 19, look.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2, 1));

        wheel = new Wheel(context);
        int screenHeight = context.getResources().getConfiguration().screenHeightDp;
        int wheelDp = screenHeight <= 0 ? 196 : Math.max(112, Math.min(204, screenHeight * 38 / 100));
        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(-1, dp(wheelDp));
        wheelParams.topMargin = dp(8);
        body.addView(wheel, wheelParams);

        TextView label = text("Яркость", 13, look.muted);
        body.addView(label);
        brightness = new SeekBar(context);
        brightness.setMax(255);
        brightness.setProgressTintList(ColorStateList.valueOf(look.accent));
        brightness.setThumbTintList(ColorStateList.valueOf(look.accent));
        brightness.setContentDescription("Яркость");
        body.addView(brightness, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout hexRow = new LinearLayout(context);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(hexRow, new LinearLayout.LayoutParams(-1, -2));
        preview = new View(context);
        preview.setContentDescription("Предпросмотр цвета");
        LinearLayout.LayoutParams swatch = new LinearLayout.LayoutParams(dp(44), dp(42));
        swatch.rightMargin = dp(12);
        hexRow.addView(preview, swatch);
        hex = addField(hexRow, "HEX", InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, 7, 1);

        LinearLayout rgbRow = new LinearLayout(context);
        body.addView(rgbRow, new LinearLayout.LayoutParams(-1, -2));
        String[] labels = {"R", "G", "B"};
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = addField(rgbRow, labels[i], InputType.TYPE_CLASS_NUMBER, 3, i + 2);
        }
        error = text("", 12, look.danger);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(error, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
        row.topMargin = dp(8);
        root.addView(actions, row);
        Button cancel = look.button(context, "Отмена", look.tint, look.text);
        apply = look.button(context, "Применить", look.accent, look.onAccent);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(0, -2, 1);
        applyParams.leftMargin = dp(8);
        actions.addView(apply, applyParams);

        refreshFields(null);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) {
                    hsv[2] = value / 255f;
                    updateFromWheel();
                }
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(context,
                look.dark ? AlertDialog.THEME_DEVICE_DEFAULT_DARK : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setView(root)
                .create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        cancel.setOnClickListener(v -> dialog.dismiss());
        apply.setOnClickListener(v -> {
            if (invalidField != 0) return;
            dialog.dismiss();
            onApply.accept(color);
        });
        root.requestFocus();
        dialog.show();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private EditText addField(LinearLayout row, String label, int type, int limit, int field) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.rightMargin = field == 2 || field == 3 ? dp(8) : 0;
        row.addView(box, params);
        TextView caption = text(label, 13, look.muted);
        box.addView(caption);
        EditText edit = new EditText(context);
        edit.setId(View.generateViewId());
        caption.setLabelFor(edit.getId());
        edit.setSingleLine(true);
        edit.setTextColor(look.text);
        edit.setTextSize(15);
        edit.setInputType(type);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        edit.setSelectAllOnFocus(true);
        edit.setPadding(dp(9), dp(7), dp(9), dp(7));
        edit.setMinimumHeight(dp(42));
        edit.setBackground(look.shape(context, look.tint, 10, true));
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        box.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) {
                if (!syncing) fieldChanged(field, edit);
            }
        });
        return edit;
    }

    private void fieldChanged(int field, EditText edited) {
        Integer incoming;
        if (field == 1) {
            incoming = WheelColor.parseHex(hex.getText());
        } else {
            int r = WheelColor.parseChannel(rgb[0].getText());
            int g = WheelColor.parseChannel(rgb[1].getText());
            int b = WheelColor.parseChannel(rgb[2].getText());
            incoming = r < 0 || g < 0 || b < 0 ? null : Color.rgb(r, g, b);
        }
        if (incoming == null) {
            invalidField = field;
            showValidation();
            return;
        }
        color = incoming;
        float oldHue = hsv[0];
        Color.colorToHSV(color, hsv);
        if (hsv[1] == 0) hsv[0] = oldHue;
        refreshFields(edited);
    }

    private void updateFromWheel() {
        color = Color.HSVToColor(hsv);
        refreshFields(null);
    }

    private void refreshFields(EditText leaveEditing) {
        syncing = true;
        try {
            if (hex != leaveEditing) hex.setText(WheelColor.hex(color));
            if (rgb[0] != leaveEditing) rgb[0].setText(Integer.toString(Color.red(color)));
            if (rgb[1] != leaveEditing) rgb[1].setText(Integer.toString(Color.green(color)));
            if (rgb[2] != leaveEditing) rgb[2].setText(Integer.toString(Color.blue(color)));
            brightness.setProgress(Math.round(hsv[2] * brightness.getMax()));
        } finally {
            syncing = false;
        }
        GradientDrawable fill = look.shape(context, color, 10, true);
        preview.setBackground(fill);
        preview.setContentDescription("Выбранный цвет " + WheelColor.hex(color));
        wheel.invalidate();
        invalidField = 0;
        showValidation();
    }

    private void showValidation() {
        error.setText(invalidField == 1 ? "HEX: введите 6 символов от 0 до 9 и от A до F." :
                invalidField == 0 ? "" : "R, G и B: введите числа от 0 до 255.");
        error.setVisibility(invalidField == 0 ? View.GONE : View.VISIBLE);
        apply.setEnabled(invalidField == 0);
        apply.setAlpha(invalidField == 0 ? 1 : .48f);
    }

    private final class Wheel extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader sweep, fade;
        private float cx, cy, radius, lastValue = -1f;
        private int pointer = -1;

        Wheel(Context context) {
            super(context);
            setContentDescription("Круг цвета. Также можно ввести HEX или RGB.");
            setFocusable(true);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            cx = w / 2f;
            cy = h / 2f;
            radius = Math.max(1f, Math.min(cx, cy) - dp(10));
            lastValue = -1f;
        }

        @Override protected void onDraw(Canvas canvas) {
            if (radius <= 0) return;
            if (lastValue != hsv[2]) {
                lastValue = hsv[2];
                int[] hues = new int[7];
                float[] hsvShader = {0, 1, lastValue};
                for (int i = 0; i < hues.length; i++) {
                    hsvShader[0] = (i % 6) * 60;
                    hues[i] = Color.HSVToColor(hsvShader);
                }
                int center = Math.round(lastValue * 255f);
                int gray = Color.rgb(center, center, center);
                sweep = new SweepGradient(cx, cy, hues, null);
                fade = new RadialGradient(cx, cy, radius, gray, gray & 0xffffff, Shader.TileMode.CLAMP);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(sweep);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(fade);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(look.border);
            canvas.drawCircle(cx, cy, radius, paint);
            float x = cx + (float) Math.cos(Math.toRadians(hsv[0])) * hsv[1] * radius;
            float y = cy + (float) Math.sin(Math.toRadians(hsv[0])) * hsv[1] * radius;
            paint.setStrokeWidth(dp(4));
            paint.setColor(Color.BLACK);
            canvas.drawCircle(x, y, dp(6), paint);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(6), paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (Math.hypot(event.getX()-cx, event.getY()-cy) > radius + dp(10)) return false;
                pointer = event.getPointerId(0);
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (pointer < 0) return false;
            int i = event.findPointerIndex(pointer);
            if (i >= 0 && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE ||
                    action == MotionEvent.ACTION_UP)) {
                WheelColor.setFromPoint(event.getX(i) - cx, event.getY(i) - cy, radius, hsv);
                updateFromWheel();
            }
            if (i < 0 || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
                    (action == MotionEvent.ACTION_POINTER_UP && event.getActionIndex() == i)) {
                pointer = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (action == MotionEvent.ACTION_UP) performClick();
            }
            return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
