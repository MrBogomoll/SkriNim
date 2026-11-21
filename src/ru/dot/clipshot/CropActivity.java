package ru.dot.clipshot;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ScrollView;
import java.io.IOException;

public final class CropActivity extends Activity {
    private CropView crop;
    private Bitmap source;
    private android.app.AlertDialog exportProgress;
    // Main-thread only. Keep a recreated editor from writing a second copy while
    // its predecessor is finishing the same explicit save.
    private static boolean saving;
    private Look look;
    private final Button[] tools = new Button[Markup.CALLOUT + 1];
    private static final String[] PENCIL_ICONS = {"✎","╱","↗"};
    private static final String[] SHAPE_ICONS = {"⬭","▭","◯","□","◇","♡","△","▣"};
    private static final String[] MARK_ICONS = {"✓","✕","①"};
    private final int[] markModes={Markup.CHECK,Markup.CROSS,Markup.STEP};
    private final String[] markLabels={"Галочка","Крестик","Номер шага"}; 
    private final int[] pencilModes = {Markup.PEN, Markup.LINE, Markup.ARROW};
    private final String[] pencilLabels = {"Карандаш", "Прямая линия", "Стрелка"};
    private final int[] shapeModes = {Markup.OVAL, Markup.RECT, Markup.CIRCLE, Markup.SQUARE, Markup.DIAMOND, Markup.HEART, Markup.TRIANGLE, Markup.CALLOUT};
    private final String[] shapeLabels = {"Овал", "Прямоугольник", "Круг", "Квадрат", "Ромб", "Сердечко", "Треугольник", "Выноска"};
    private static final String[] REGULAR_GESTURES = {"От края до края", "От центра наружу", "От края к центру"};
    private static final String[] GESTURE_SHORT={"Край ↔ край","От центра","К центру"};
    private final int[] shapeGestures=new int[Markup.CALLOUT+1];
    private final boolean[] shapeFills=new boolean[Markup.CALLOUT+1], shapeFree=new boolean[Markup.CALLOUT+1];
    private LinearLayout cropControls;
    private Button formatButton,tiltButton,scrollDownButton,scrollUpButton;
    private LinearLayout scrollActions;
    private static final int SCROLL_CONSENT = 46;
    private final Handler scrollHandler = new Handler(Looper.getMainLooper());
    private final Runnable continueScroll = this::startAfterScrollConsent;
    private boolean askingScrollConsent, scrollUpward, resumeGrantOnFocus;
    private android.app.AlertDialog scrollConfirmation;
    private long waitForProjectionUntil;
    private boolean tiltGuide;
    private Button undo;
    private LinearLayout styleControls,colorControls;
    private FrameLayout eyedropperButton,wheelButton;
    private PaletteIcon eyedropperIcon,wheelIcon;
    private boolean eyePicking;
    private android.window.OnBackInvokedCallback closeEyeOnBack;
    private Button thicknessButton, opacityButton, textMosaicButton, fillButton, gestureButton, directionButton;
    private boolean textMosaic, textDetecting;
    private int[] textWords;
    private Thread textDetectionThread;
    private TextView brushHint;
    private static final int[] COLORS = {0xffdf363f, 0xffffb400, 0xff16a34a, 0xff1476e7, 0xff171c24, 0xffffffff};
    private static final String[] COLOR_NAMES = {"Красный", "Жёлтый", "Зелёный", "Синий", "Чёрный", "Белый"};
    private final FrameLayout[] swatches = new FrameLayout[COLORS.length];
    private int inkColor, strokeWidth, opacity, eraserWidth, mosaicWidth, pixelSize, minPixelSize, maxPixelSize;
    private StrokeSize strokeSize, eraserSize, mosaicSize, captionSize;
    private int captionWidth, stepWeight, initialMarkWidth;
    private Button initialMarkSizeButton;
    private SlideMenu slideMenu;
    private FrameLayout root, styleOverlay;
    private LinearLayout editor, captureActions, captionActions;
    private View toolSelectors;
    private StableEditorToolbar editorToolbar;
    private TextView captionHint;
    private LinearLayout captionOptions;
    private Button draftFontButton,draftWrapButton,draftOpacityButton;
    private FrameLayout paletteSlot;
    private LinearLayout imeCaptionControls,imeCaptionButtons;
    private Button imeFontButton,imeWrapButton,imeOpacityButton;
    private int imeBottom, safeLeft, safeTop, safeRight, safeBottom;
    private boolean inlineKeyboardShown;
    private int imeControlsBottom,imeBaselineHeight;
    private final android.graphics.Rect imeVisibleFrame=new android.graphics.Rect();
    private final int[] imeRootOnScreen=new int[2];
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener imeMeasure=this::positionImeCaptionControls;
    private android.widget.EditText inlineCaption;
    private String draftValue="";
    private int draftTextStyle;
    private LinearLayout captionStyles;
    private final Button[] captionStyleButtons=new Button[4];
    private static final int[] CAPTION_STYLE_FLAGS={CaptionStyle.BOLD,CaptionStyle.ITALIC,CaptionStyle.UNDERLINE,CaptionStyle.STRIKE};
    private static final String[] CAPTION_STYLE_GLYPHS={"B","I","U","S"};
    private static final String[] CAPTION_STYLE_NAMES={"Жирный","Курсив","Подчёркивание","Зачёркивание"};
    private boolean captionWrap;
    private Button refineShapeButton;
    private boolean refineShape;
    private android.window.OnBackInvokedCallback closeCaptionOnBack;
    private android.window.OnBackInvokedCallback closeStyleOnBack;
    private Runnable saveStyle;
    private static int validIndex(int value, int limit, int fallback) {
        return value >= 0 && value < limit ? value : fallback;
    }
    private int editorTool = Markup.CROP;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        look = new Look(this);
        int legacyColor=validIndex(getPreferences(MODE_PRIVATE).getInt("stroke_color",0),COLORS.length,0);
        inkColor=getPreferences(MODE_PRIVATE).getInt("stroke_ink_color",COLORS[legacyColor]) | 0xff000000;
        opacity = InkOpacity.clamp(getPreferences(MODE_PRIVATE).getInt("stroke_opacity", 100));
        loadShapeOptions();
        refineShape=getPreferences(MODE_PRIVATE).getBoolean("shape_refine",false);
        Bitmap bitmap = ShotMemory.preview();
        source = bitmap;
        if (bitmap == null || bitmap.isRecycled()) {
            Toast.makeText(this, "Снимок недоступен", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (saved != null) {
            askingScrollConsent = saved.getBoolean("askingScrollConsent");
            resumeGrantOnFocus = saved.getBoolean("resumeScrollGrant");
            scrollUpward = saved.getBoolean("scrollUpward");
            waitForProjectionUntil = saved.getLong("waitForScrollProjection");
        }
        int shortSide = Math.min(bitmap.getWidth(), bitmap.getHeight());
        strokeSize = new StrokeSize(shortSide);
        captionSize = StrokeSize.caption(shortSide);
        initialMarkWidth=MarkPlacement.normalizeInitialWidth(getPreferences(MODE_PRIVATE)
                .getInt("mark_initial_width_px",0));
        stepWeight=InkOpacity.clamp(getPreferences(MODE_PRIVATE).getInt("step_weight",0));
        captionWidth = StrokeSize.captionWidth(shortSide,getPreferences(MODE_PRIVATE).getInt("caption_pixels_v2",-1),
                getPreferences(MODE_PRIVATE).getInt("caption_progress",240));
        eraserSize = StrokeSize.eraser(shortSide);
        mosaicSize = new StrokeSize(shortSide, 10f);
        eraserWidth = StrokeSize.eraserWidth(shortSide, getPreferences(MODE_PRIVATE).getInt("eraser_progress_v2", -1),
                getPreferences(MODE_PRIVATE).getInt("eraser_progress", 300));
        mosaicWidth = mosaicSize.fromProgress(getPreferences(MODE_PRIVATE).getInt("mosaic_progress", 350));
        minPixelSize = MosaicGrid.minCell(shortSide);
        maxPixelSize = MosaicGrid.maxCell(shortSide);
        pixelSize = Math.max(minPixelSize, Math.min(maxPixelSize, getPreferences(MODE_PRIVATE).getInt(
                "mosaic_block_size", Math.round(shortSide * .020f))));
        int progress = getPreferences(MODE_PRIVATE).getInt("stroke_progress", -1);
        strokeWidth = progress < 0
                ? strokeSize.fromLegacy(getPreferences(MODE_PRIVATE).getInt("stroke_thickness", 2))
                : strokeSize.fromProgress(progress);
        LinearLayout layout = new LinearLayout(this);
        editor = layout;
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(look.canvas);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(6), dp(12), dp(4));
        ImageButton save = new ImageButton(this);
        save.setImageResource(R.drawable.save_screenshot);
        save.setImageTintList(android.content.res.ColorStateList.valueOf(look.accent));
        save.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(look.dark ? 0x34ffffff : 0x24001c40),
                look.shape(this, look.tint, 14, false), null));
        save.setPadding(dp(12),dp(12),dp(12),dp(12));
        save.setContentDescription("Сохранить снимок");
        save.setTooltipText("Сохранить в папку скриншотов");
        save.setOnClickListener(v -> {
            if (crop.hasDraft()) {
                Toast.makeText(this, "Сначала нажми «Применить», чтобы сохранить правку.", Toast.LENGTH_SHORT).show();
            } else exportSelection(true);
        });
        LinearLayout.LayoutParams saveSize = new LinearLayout.LayoutParams(dp(48), dp(48));
        saveSize.rightMargin = dp(6);
        header.addView(save, saveSize);
        TextView title = new TextView(this);
        title.setText("Снимок");
        title.setSingleLine(true);
        title.setAutoSizeTextTypeUniformWithConfiguration(12,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(look.text);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button stop = look.button(this, "×  Завершить сеанс", look.dangerTint, look.danger);
        stop.setContentDescription("Завершить сеанс захвата экрана");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, ProjectionService.class));
            Toast.makeText(this, "Сеанс захвата завершён", Toast.LENGTH_SHORT).show();
            finish();
        });
        header.addView(stop, new LinearLayout.LayoutParams(-2, -2));
        layout.addView(header, new LinearLayout.LayoutParams(-1, -2));
        crop = new CropView(bitmap);
        layout.addView(crop, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout actions = new LinearLayout(this);
        captureActions=actions;
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setBaselineAligned(false);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        actions.setPadding(dp(8), dp(8), dp(8), dp(10));
        actions.setMinimumHeight(dp(72));
        editorToolbar=new StableEditorToolbar(this);
        editorToolbar.setBackground(look.shape(this,look.panel,22,true));
        LinearLayout toolbar=new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setClipChildren(false);
        editorToolbar.addView(toolbar,new FrameLayout.LayoutParams(-1,-2));
        LinearLayout markings = new LinearLayout(this);
        markings.setPadding(dp(7), dp(6), dp(7), 0);
        markings.setBaselineAligned(false);
        addTool(markings, Markup.CROP, "⌗", "Кадр");
        tools[Markup.CROP].setContentDescription("Кадрирование");
        addToolMenu(markings, Markup.PEN, "✎", "Карандаш", pencilModes, pencilLabels);
        addToolMenu(markings, Markup.OVAL, "◯", "Фигуры", shapeModes, shapeLabels);
        addToolMenu(markings, Markup.STEP, "✓", "Метки", markModes, markLabels);
        addTool(markings, Markup.ERASER, "▱", "Ластик");
        addTool(markings, Markup.MOSAIC, "▦", "Мозаика");
        addTool(markings, Markup.TEXT, "T", "Текст");
        undo = toolButton(markings, "↶", "Назад");
        undo.setContentDescription("Отменить последнюю пометку");
        undo.setOnClickListener(v -> { crop.markup.undo(); redrawTools(); crop.invalidate(); });
        FrameLayout toolSlot=new FrameLayout(this);
        toolSelectors=markings;
        toolSlot.addView(markings,new FrameLayout.LayoutParams(-1,-1));
        toolbar.addView(toolSlot,new LinearLayout.LayoutParams(-1,dp(EditorToolbarMetrics.TOOLS_DP)));
        LinearLayout details=new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        paletteSlot=new FrameLayout(this);
        paletteSlot.addView(makeCropControls(),new FrameLayout.LayoutParams(-1,-1));
        paletteSlot.addView(makeColorControls(),new FrameLayout.LayoutParams(-1,-1));
        details.addView(paletteSlot,new LinearLayout.LayoutParams(-1,dp(EditorToolbarMetrics.PALETTE_DP)));
        FrameLayout options = new FrameLayout(this);
        options.addView(makeStyleControls(), new FrameLayout.LayoutParams(-1, -1));
        scrollActions = new LinearLayout(this);
        scrollActions.setPadding(dp(8), 0, dp(8), 0);
        scrollActions.setBaselineAligned(false);
        scrollDownButton = scrollAction("Прокрутка вниз", "Добавить к снимку часть страницы ниже", () -> requestScroll(false));
        scrollUpButton = scrollAction("Прокрутка вверх", "Добавить к снимку часть страницы выше", () -> requestScroll(true));
        options.addView(scrollActions, new FrameLayout.LayoutParams(-1, -1));
        details.addView(options,new LinearLayout.LayoutParams(-1,dp(EditorToolbarMetrics.OPTIONS_DP)));
        FrameLayout detailsSlot=new FrameLayout(this);
        detailsSlot.addView(details,new FrameLayout.LayoutParams(-1,-1));
        toolbar.addView(detailsSlot,new LinearLayout.LayoutParams(-1,
                dp(EditorToolbarMetrics.PALETTE_DP)+dp(EditorToolbarMetrics.OPTIONS_DP)));
        captionHint=new TextView(this);
        captionHint.setText("Середина: сдвиг · Грани: размер рамки\nУглы: размер рамки и поворот");
        captionHint.setGravity(Gravity.CENTER);
        captionHint.setPadding(dp(12),dp(4),dp(12),dp(4));
        captionHint.setTextColor(look.text);
        captionHint.setMaxLines(3);
        captionHint.setAutoSizeTextTypeUniformWithConfiguration(10,13,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        captionHint.setVisibility(View.INVISIBLE);
        toolSlot.addView(captionHint,new FrameLayout.LayoutParams(-1,-1));
        toolSlot.addView(makeCaptionStyles(),new FrameLayout.LayoutParams(-1,-1));
        captionOptions=new LinearLayout(this);
        captionOptions.setGravity(Gravity.CENTER_VERTICAL);
        captionOptions.setPadding(dp(8),0,dp(8),dp(3));
        captionOptions.setVisibility(View.GONE);
        draftFontButton=look.button(this,"",look.tint,look.text);
        compactCaptionButton(draftFontButton);
        draftFontButton.setOnClickListener(v -> showDraftFont(draftFontButton));
        draftWrapButton=look.button(this,"",look.tint,look.text);
        compactCaptionButton(draftWrapButton);
        draftWrapButton.setOnClickListener(v -> toggleDraftWrap());
        draftOpacityButton=look.button(this,"",look.tint,look.text);
        compactCaptionButton(draftOpacityButton);
        draftOpacityButton.setOnClickListener(v -> showStyle(true,draftOpacityButton));
        addCaptionOption(draftFontButton,1.2f);
        addCaptionOption(draftWrapButton,1.15f);
        addCaptionOption(draftOpacityButton,.82f);
        detailsSlot.addView(captionOptions,new FrameLayout.LayoutParams(-1,dp(EditorToolbarMetrics.OPTIONS_DP),Gravity.BOTTOM));
        redrawTools();
        FrameLayout actionSlot=new FrameLayout(this);
        actionSlot.addView(actions,new FrameLayout.LayoutParams(-1,-1));
        captionActions=new LinearLayout(this);
        captionActions.setOrientation(LinearLayout.VERTICAL);
        captionActions.setVisibility(View.INVISIBLE);
        LinearLayout draftActions=new LinearLayout(this);
        draftActions.setGravity(Gravity.CENTER_VERTICAL);
        draftActions.setPadding(dp(8),dp(4),dp(8),dp(3));
        compactDraftAction(draftActions,"Отмена","Отменить размещение",look.tint,look.text,1f,() -> finishCaption(false));
        compactDraftAction(draftActions,"✓  Применить","Применить разметку",look.accent,look.onAccent,1.4f,() -> finishCaption(true));
        captionActions.addView(draftActions,new LinearLayout.LayoutParams(-1,-1));
        actionSlot.addView(captionActions,new FrameLayout.LayoutParams(-1,-1));
        toolbar.addView(actionSlot,new LinearLayout.LayoutParams(-1,dp(EditorToolbarMetrics.ACTIONS_DP)));
        action(actions, "Отмена", "Закрыть без копирования", look.tint, look.text, 0.88f, this::finish);
        action(actions, "↺", "Сбросить обрезку", look.tint, look.text, 0f, crop::reset);
        action(actions, "Копировать", "Скопировать обрезанный снимок", look.accent, look.onAccent, 1.4f,
                () -> exportSelection(false));
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, -2);
        bar.setMargins(dp(10), 0, dp(10), dp(5));
        layout.addView(editorToolbar,bar);
        layout.setMotionEventSplittingEnabled(false);
        root = new FrameLayout(this);
        root.setMotionEventSplittingEnabled(false);
        root.addView(layout, new FrameLayout.LayoutParams(-1, -1));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) dismissStyle();
        });
        // Same window setup as 0.33. A fresh capture must never enter the IME path.
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(root);
        look.bars(this, layout);
        if (saved != null && saved.getBoolean("confirmScroll"))
            confirmScroll();
    }

    private Button scrollAction(String label, String description, Runnable tap) {
        Button button = look.button(this, label, look.tint, look.accent);
        button.setSingleLine(true);
        button.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        button.setStateListAnimator(null);
        button.setContentDescription(description);
        button.setOnClickListener(v -> tap.run());
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1f);
        size.setMargins(dp(3), 0, dp(3), 0);
        scrollActions.addView(button, size);
        return button;
    }

    void refreshScrollButtons() {
        if (scrollUpButton == null) return;
        scrollActions.setVisibility(editorTool == Markup.CROP
                ? View.VISIBLE : View.GONE);
        ProjectionService service = ProjectionService.current;
        boolean enabled = (service == null || !service.scrolling()) && !askingScrollConsent && waitForProjectionUntil == 0;
        scrollDownButton.setEnabled(enabled);
        scrollUpButton.setEnabled(enabled);
        scrollActions.setAlpha(enabled ? 1f : .45f);
    }

    boolean hasScreenshotEdits() { return crop.hasEdits(); }

    private void requestScroll(boolean upward) {
        if (askingScrollConsent || waitForProjectionUntil != 0 || scrollConfirmation != null) return;
        if (ScrollGestureService.current == null) {
            ScrollAccess.explain(this);
            return;
        }
        scrollUpward = upward;
        if (crop.hasEdits()) confirmScroll();
        else beginScroll();
    }

    private void confirmScroll() {
        scrollConfirmation = new android.app.AlertDialog.Builder(this)
                .setMessage("Готовый длинный снимок заменит текущий кадр и его правки.")
                .setPositiveButton(scrollUpward ? "Прокрутка вверх" : "Прокрутка вниз", (dialog, which) -> beginScroll())
                .setNegativeButton("Отмена", null).create();
        scrollConfirmation.setOnDismissListener(dialog -> scrollConfirmation = null);
        scrollConfirmation.show();
    }

    private void beginScroll() {
        resumeGrantOnFocus = false;
        ProjectionService service = ProjectionService.current;
        if (service != null && service.ready()) {
            service.startScrollingFromEditor(this, source, scrollUpward);
            refreshScrollButtons();
            return;
        }
        if (ProjectionService.hasPendingConsent()) {
            if (ProjectionService.resumeConsent(this)) waitForScrollService();
            else resumeGrantOnFocus = ProjectionService.hasPendingConsent();
            return;
        }
        try {
            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            Intent consent = manager.createScreenCaptureIntent();
            askingScrollConsent = true;
            refreshScrollButtons();
            startActivityForResult(consent, SCROLL_CONSENT);
        } catch (RuntimeException failure) {
            clearScrollRequest();
            scrollUnavailable();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SCROLL_CONSENT || !askingScrollConsent) return;
        clearScrollRequest();
        if (result != RESULT_OK || data == null || isFinishing() || ShotMemory.preview() != source) return;
        if (ProjectionService.acceptConsent(this, result, data)) waitForScrollService();
        else resumeGrantOnFocus = ProjectionService.hasPendingConsent();
    }

    private void waitForScrollService() {
        waitForProjectionUntil = SystemClock.uptimeMillis() + 4000;
        refreshScrollButtons();
        scrollHandler.removeCallbacks(continueScroll);
        scrollHandler.post(continueScroll);
    }

    private void resumeScrollGrant() {
        if (!resumeGrantOnFocus || !hasWindowFocus() || isFinishing() || ShotMemory.preview() != source) return;
        ProjectionService service = ProjectionService.current;
        if ((service != null && service.ready()) || ProjectionService.resumeConsent(this)) {
            resumeGrantOnFocus = false;
            waitForScrollService();
        }
    }

    private void startAfterScrollConsent() {
        if (waitForProjectionUntil == 0 || isFinishing() || isDestroyed()) return;
        ProjectionService service = ProjectionService.current;
        if (ShotMemory.preview() != source) {
            clearScrollRequest();
        } else if (service != null && service.ready() && (hasWindowFocus() || service.canReadTarget())) {
            // On Android 14+, choosing one app can bring its task forward
            // before this editor receives the result.
            clearScrollRequest();
            service.startScrollingFromEditor(this, source, scrollUpward);
            refreshScrollButtons();
        } else if (SystemClock.uptimeMillis() >= waitForProjectionUntil) {
            clearScrollRequest();
            resumeGrantOnFocus = ProjectionService.hasPendingConsent();
            if (hasWindowFocus() && !resumeGrantOnFocus) scrollUnavailable();
        } else scrollHandler.postDelayed(continueScroll, 100);
    }

    private void scrollUnavailable() {
        Toast.makeText(this, "Не удалось начать захват экрана.", Toast.LENGTH_SHORT).show();
    }

    private void clearScrollRequest() {
        scrollHandler.removeCallbacks(continueScroll);
        askingScrollConsent = false;
        waitForProjectionUntil = 0;
        refreshScrollButtons();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshScrollButtons();
        resumeScrollGrant();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) {
            refreshScrollButtons();
            resumeScrollGrant();
            if (waitForProjectionUntil != 0) {
                if (ProjectionService.hasPendingConsent()) ProjectionService.resumeConsent(this);
                scrollHandler.removeCallbacks(continueScroll);
                scrollHandler.post(continueScroll);
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("askingScrollConsent", askingScrollConsent);
        out.putBoolean("resumeScrollGrant", resumeGrantOnFocus);
        out.putBoolean("scrollUpward", scrollUpward);
        out.putBoolean("confirmScroll", scrollConfirmation != null && scrollConfirmation.isShowing()
                && !askingScrollConsent && waitForProjectionUntil == 0);
        out.putLong("waitForScrollProjection", waitForProjectionUntil);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Bitmap preview = ShotMemory.preview();
        if (preview != null && preview != source) recreate();
    }

    private void exportSelection(boolean toDevice) {
        if (exportProgress != null) return;
        if (toDevice && saving) {
            Toast.makeText(this, "Сохранение уже идёт.", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap result;
        try {
            result = crop.selection();
        } catch (RuntimeException | OutOfMemoryError error) {
            exportFailed(toDevice);
            return;
        }
        exportProgress = new android.app.AlertDialog.Builder(this)
                .setMessage(toDevice ? "Сохранение снимка…" : "Копирование снимка…").setCancelable(false).show();
        if (toDevice) saving = true;
        try {
            new Thread(() -> writeSelection(result, toDevice), "SkriNim-PNG").start();
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            if (result != source) result.recycle();
            if (toDevice) saving = false;
            dismissExportProgress();
            exportFailed(toDevice);
        }
    }

    private void writeSelection(Bitmap result, boolean toDevice) {
        Context context = getApplicationContext();
        PngBuffer encoded = null;
        boolean written = false;
        try {
            if (toDevice) written = ScreenshotStorage.save(context, result) != null;
            else encoded = ShotMemory.encode(result);
        } catch (IOException | RuntimeException | OutOfMemoryError failure) {
            // The editor keeps its source and edits if rendering or writing fails.
        } finally {
            if (result != source) result.recycle();
        }
        final PngBuffer png = encoded;
        final boolean saved = written;
        runOnUiThread(() -> {
            if (toDevice) {
                saving = false;
                Toast.makeText(context, saved ? "Сохранено в альбом «Скриншоты»" : "Не удалось сохранить снимок.",
                        Toast.LENGTH_SHORT).show();
            }
            if (isFinishing() || isDestroyed()) return;
            dismissExportProgress();
            if (!toDevice) {
                if (png != null && ShotMemory.copy(this, png)) {
                    Toast.makeText(this, "Снимок в буфере", Toast.LENGTH_SHORT).show();
                    finish();
                } else exportFailed(false);
            }
        });
    }

    private void dismissExportProgress() {
        if (exportProgress != null) exportProgress.dismiss();
        exportProgress = null;
    }

    private void exportFailed(boolean toDevice) {
        Toast.makeText(this, toDevice ? "Не удалось сохранить снимок." : "Не удалось скопировать снимок.",
                Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        scrollHandler.removeCallbacks(continueScroll);
        if (scrollConfirmation != null) scrollConfirmation.dismiss();
        dismissExportProgress();
        closeInlineCaption();
        releaseCaptionBack();
        if (textDetectionThread!=null) textDetectionThread.interrupt();
        dismissStyle();
        setEyePicking(false);
        if (isFinishing()) ShotMemory.clearPreview(source);
        super.onDestroy();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Button toolButton(LinearLayout row, String glyph, String label) {
        Button button = look.button(this, "", android.graphics.Color.TRANSPARENT, look.text);
        toolCaption(button,glyph,label);
        button.setMaxLines(2);
        button.setTextSize(9);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setPadding(dp(1), dp(2), dp(1), dp(2));
        button.setMinimumHeight(0);
        button.setContentDescription(label);
        row.addView(button,new LinearLayout.LayoutParams(0,dp(50),1));
        return button;
    }

    private void toolCaption(Button button,String glyph,String label) {
        android.text.SpannableString caption = new android.text.SpannableString(glyph + "\n" + label);
        caption.setSpan(new android.text.style.AbsoluteSizeSpan(20,true),0,glyph.length(),0);
        button.setText(caption);
    }

    private void selectTool(int mode) {
        if (mode == editorTool) return;
        setEyePicking(false);
        crop.resetGesture();
        editorTool = mode;
        redrawTools();
        crop.invalidate();
    }

    private void addTool(LinearLayout row, int mode, String glyph, String label) {
        Button button = toolButton(row, glyph, label);
        tools[mode] = button;
        button.setOnClickListener(v -> selectTool(mode));
    }

    private void addToolMenu(LinearLayout row, int group, String glyph, String title, int[] modes, String[] labels) {
        Button button = toolButton(row, glyph, title);
        tools[group] = button;
        if (group == Markup.PEN) ((LinearLayout.LayoutParams) button.getLayoutParams()).weight=1.18f;
        bindSlideMenu(button, slide -> {
            int checked = -1;
            for (int i=0; i<modes.length; i++) if (modes[i] == editorTool) checked = i;
            choose(button,title,labels,group == Markup.PEN ? PENCIL_ICONS : group==Markup.STEP?MARK_ICONS:SHAPE_ICONS,
                    checked,group==Markup.OVAL?2:1,index -> selectTool(modes[index]),slide);
        });
    }

    private void bindSlideMenu(Button anchor,java.util.function.Consumer<SlideMenu> open) {
        anchor.setOnClickListener(v -> open.accept(null));
        anchor.setOnTouchListener(new View.OnTouchListener() {
            private int pointer=-1;
            private float startX,startY;
            private SlideMenu held;
            private final Runnable hold=() -> {
                if (pointer<0 || !anchor.isShown() || styleOverlay!=null) return;
                SlideMenu menu=new SlideMenu();
                open.accept(menu);
                if (menu!=slideMenu) return;
                held=menu;
                if (anchor.getParent()!=null) anchor.getParent().requestDisallowInterceptTouchEvent(true);
                anchor.setPressed(false);
            };

            @Override public boolean onTouch(View view,MotionEvent event) {
                int action=event.getActionMasked();
                if (action==MotionEvent.ACTION_DOWN) {
                    pointer=event.getPointerId(0);
                    startX=event.getX();
                    startY=event.getY();
                    anchor.setPressed(true);
                    anchor.postDelayed(hold,240);
                } else if (action==MotionEvent.ACTION_MOVE && pointer>=0) {
                    int index=event.findPointerIndex(pointer);
                    if (index<0) end(false,event);
                    else if (held!=null) {
                        if (held==slideMenu) held.move(event.getRawX(index),event.getRawY(index));
                    } else {
                        int slop=android.view.ViewConfiguration.get(CropActivity.this).getScaledTouchSlop();
                        if (Math.abs(event.getX(index)-startX)>slop || Math.abs(event.getY(index)-startY)>slop) end(false,event);
                    }
                } else if (action==MotionEvent.ACTION_UP) {
                    end(true,event);
                } else if (action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_POINTER_DOWN) {
                    end(false,event);
                }
                return true;
            }

            private void end(boolean up,MotionEvent event) {
                anchor.removeCallbacks(hold);
                anchor.setPressed(false);
                if (anchor.getParent()!=null) anchor.getParent().requestDisallowInterceptTouchEvent(false);
                if (held!=null && held==slideMenu) {
                    Button picked=up?held.hit(event.getRawX(),event.getRawY()):null;
                    if (picked!=null) picked.performClick();
                    else dismissStyle();
                } else if (held==null && up && pointer>=0 && event.getX()>=0 && event.getY()>=0
                        && event.getX()<anchor.getWidth() && event.getY()<anchor.getHeight()) {
                    anchor.performClick();
                }
                held=null;
                pointer=-1;
            }
        });
    }

    private final class SlideMenu {
        Button[] items;
        ScrollView scroll;
        Button highlighted;
        final android.graphics.Rect visible=new android.graphics.Rect();
        final int[] screen=new int[2];
        float x,y;
        final Runnable scrollWhileHeld=new Runnable() {
            @Override public void run() {
                if (slideMenu!=SlideMenu.this) return;
                int step=scrollStep();
                int old=scroll.getScrollY();
                scroll.scrollBy(0,step);
                highlight();
                if (step!=0 && old!=scroll.getScrollY()) scroll.postDelayed(this,32);
            }
        };

        Button hit(float x,float y) {
            for (Button item : items) {
                if (!item.getLocalVisibleRect(visible)) continue;
                item.getLocationOnScreen(screen);
                if (visible.contains((int)Math.floor(x-screen[0]),(int)Math.floor(y-screen[1]))) return item;
            }
            return null;
        }

        private int scrollStep() {
            scroll.getLocationOnScreen(screen);
            if (x<screen[0] || x>=screen[0]+scroll.getWidth()) return 0;
            float relative=y-screen[1];
            if (relative<0 || relative>=scroll.getHeight()) return 0;
            int band=Math.min(dp(18),scroll.getHeight()/4);
            return relative<band?-dp(11):relative>=scroll.getHeight()-band?dp(11):0;
        }

        private void highlight() {
            Button next=hit(x,y);
            if (next==highlighted) return;
            if (highlighted!=null) highlighted.setPressed(false);
            highlighted=next;
            if (next!=null) next.setPressed(true);
        }

        void move(float x,float y) {
            this.x=x;
            this.y=y;
            highlight();
            scroll.removeCallbacks(scrollWhileHeld);
            if (scrollStep()!=0) scroll.postDelayed(scrollWhileHeld,250);
        }

        void close() {
            scroll.removeCallbacks(scrollWhileHeld);
            if (highlighted!=null) highlighted.setPressed(false);
        }
    }

    private static boolean isMarkTool(int tool) {
        return tool==Markup.CHECK || tool==Markup.CROSS || tool==Markup.STEP;
    }

    private int toolGroup() {
        if (editorTool == Markup.LINE || editorTool == Markup.ARROW) return Markup.PEN;
        if (isMarkTool(editorTool)) return Markup.STEP;
        if (Markup.isShape(editorTool)||editorTool==Markup.CALLOUT) return Markup.OVAL;
        return editorTool;
    }

    private void choose(Button anchor,String title,String[] labels,String[] icons,int checked,
            int columns,java.util.function.IntConsumer pick) {
        choose(anchor,title,labels,icons,checked,columns,pick,null);
    }

    private void choose(Button anchor,String title,String[] labels,String[] icons,int checked,
            int columns,java.util.function.IntConsumer pick,SlideMenu slide) {
        if (styleOverlay != null) return;
        setEyePicking(false);
        int rowHeight=dp(46), header=dp(36), pad=dp(6);
        int rows=(labels.length+columns-1)/columns;
        int fixed=header+2*pad;
        int bodyHeight=rows*rowHeight;
        ThicknessPlacement placement=findPlacement(anchor,dp(columns>1?290:220),
                fixed+bodyHeight,fixed+rowHeight);
        if (placement.height<fixed+dp(30) || placement.width<dp(100)) return;
        LinearLayout panel=panel();
        panel.setPadding(pad,pad,pad,pad);
        TextView caption=new TextView(this);
        caption.setText(title);
        caption.setTextColor(look.muted);
        caption.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        caption.setTextSize(12);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setPadding(dp(9),0,dp(9),0);
        caption.setAccessibilityHeading(true);
        panel.addView(caption,new LinearLayout.LayoutParams(-1,header));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setClipToPadding(false);
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list,new ScrollView.LayoutParams(-1,-2));
        Button selected=null, first=null;
        if (slide!=null) {
            slide.items=new Button[labels.length];
            slide.scroll=scroll;
        }
        for (int row=0;row<rows;row++) {
            LinearLayout strip=new LinearLayout(this);
            strip.setBaselineAligned(false);
            list.addView(strip,new LinearLayout.LayoutParams(-1,rowHeight));
            for (int col=0;col<columns;col++) {
                int index=row*columns+col;
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-1,1);
                cell.setMargins(dp(2),dp(2),dp(2),dp(2));
                if (index>=labels.length) {
                    strip.addView(new View(this),cell);
                    continue;
                }
                boolean active=index==checked;
                String icon=icons==null?"":icons[index]+"  ";
                Button option=look.button(this,icon+labels[index]+(active && columns==1?"  ✓":""),
                        active?look.tint:look.canvas,active?look.accent:look.text);
                option.setTextSize(columns==1?14:12);
                option.setPadding(dp(columns==1?11:3),0,dp(columns==1?8:3),0);
                if (columns>1) {
                    option.setMaxLines(1);
                    option.setAutoSizeTextTypeUniformWithConfiguration(8,12,1,android.util.TypedValue.COMPLEX_UNIT_SP);
                }
                option.setGravity(columns==1?Gravity.CENTER_VERTICAL|Gravity.START:Gravity.CENTER);
                option.setStateListAnimator(null);
                option.setElevation(0);
                option.setSelected(active);
                option.setContentDescription(labels[index]);
                option.setOnClickListener(v -> { dismissStyle(); pick.accept(index); });
                strip.addView(option,cell);
                if (slide!=null) slide.items[index]=option;
                if (first==null) first=option;
                if (active) selected=option;
            }
        }
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,placement.height-fixed));
        slideMenu=slide;
        attachPanel(panel,placement,slide!=null?null:selected==null?first:selected);
        if (slide==null && selected!=null && bodyHeight>placement.height-fixed) {
            final int selectedRow=checked/columns;
            scroll.post(() -> scroll.scrollTo(0,selectedRow*rowHeight));
        }
    }

    private Button shapeStyleOption(LinearLayout strip,String label) {
        Button option=look.button(this,label,look.canvas,look.text);
        option.setTextSize(14);
        option.setMinHeight(0);
        option.setMinimumHeight(0);
        option.setGravity(Gravity.CENTER);
        option.setIncludeFontPadding(true);
        option.setMaxLines(1);
        option.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        option.setPadding(dp(3),dp(2),dp(3),dp(2));
        option.setStateListAnimator(null);
        option.setTranslationZ(0f);
        LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(44),1);
        cell.setMargins(dp(2),dp(2),dp(2),dp(2));
        strip.addView(option,cell);
        return option;
    }

    private void decorateShapeStyle(Button option,boolean active) {
        option.setSelected(active);
        option.setTextColor(active?look.accent:look.text);
        option.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x24005aba),
                look.shape(this,active?look.tint:look.canvas,12,active),
                look.shape(this,Color.WHITE,12,false)));
    }

    private static String shapeKey(int tool) {
        switch (tool) {
            case Markup.OVAL: return "oval";
            case Markup.RECT: return "rect";
            case Markup.CIRCLE: return "circle";
            case Markup.SQUARE: return "square";
            case Markup.DIAMOND: return "diamond";
            case Markup.HEART: return "heart";
            case Markup.TRIANGLE: return "triangle";
            case Markup.CHECK: return "check";
            case Markup.CROSS: return "cross";
            default: throw new IllegalArgumentException("Not a shape");
        }
    }

    private void loadShapeOptions() {
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        boolean oldFill=prefs.getBoolean("fill_shapes",false);
        for (int tool : shapeModes) {
            if (!Markup.isShape(tool)) continue;
            String key=shapeKey(tool);
            shapeFills[tool]=prefs.getBoolean(key+"_filled",oldFill);
            shapeFree[tool]=tool!=Markup.CIRCLE&&prefs.getBoolean(key+"_free_direction",false);
            int fallback=tool==Markup.CIRCLE?ShapeGeometry.CENTER_OUT:ShapeGeometry.EDGE_TO_EDGE;
            shapeGestures[tool]=validIndex(prefs.getInt(key+"_gesture",fallback),REGULAR_GESTURES.length,fallback);
        }
    }

    private void updateShapeOptions(boolean shape) {
        fillButton.setVisibility(shape&&!Markup.isOpenShape(editorTool)?View.VISIBLE:View.GONE);
        boolean placing=shape&&(crop==null||crop.shapeDraft==null);
        refineShapeButton.setVisibility(placing?View.VISIBLE:View.GONE);
        gestureButton.setVisibility(placing?View.VISIBLE:View.GONE);
        directionButton.setVisibility(placing&&editorTool!=Markup.CIRCLE?View.VISIBLE:View.GONE);
        if (!shape) return;
        refineShapeButton.setText(refineShape?"✓ Рамка":"Рамка");
        refineShapeButton.setContentDescription(refineShape?"Подправить после рисования: включено":"Подправить после рисования: выключено");
        decorateShapeStyle(refineShapeButton,refineShape);
        int tool=editorTool;
        boolean fill=shapeFills[tool]&&!Markup.isOpenShape(tool), free=shapeFree[tool];
        int gesture=shapeGestures[tool];
        fillButton.setText(fill?"Заливка":"Контур");
        fillButton.setContentDescription(fill?"Заливка. Нажми для контура":"Контур. Нажми для заливки");
        decorateShapeStyle(fillButton,fill);
        gestureButton.setText(GESTURE_SHORT[gesture]+" ▾");
        gestureButton.setContentDescription("Жест: "+REGULAR_GESTURES[gesture]);
        decorateShapeStyle(gestureButton,false);
        directionButton.setText(free?"Свободно":"По осям");
        directionButton.setContentDescription(free?"Свободный угол. Нажми для привязки к осям":
                "По осям. Нажми для свободного угла");
        decorateShapeStyle(directionButton,free);
    }

    private void chooseShapeGesture() {
        int tool=editorTool;
        if (!Markup.isShape(tool)) return;
        choose(gestureButton,"Жест фигуры",REGULAR_GESTURES,null,shapeGestures[tool],1,index -> {
            shapeGestures[tool]=index;
            getPreferences(MODE_PRIVATE).edit().putInt(shapeKey(tool)+"_gesture",index).apply();
            updateStyleControls();
        });
    }

    private LinearLayout makeCropControls() {
        cropControls = new LinearLayout(this);
        cropControls.setGravity(Gravity.CENTER_VERTICAL);
        cropControls.setPadding(dp(8), 0, dp(8), 0);
        formatButton = cropButton("Свободно", "Пропорции кадра", 1f, () -> {});
        bindSlideMenu(formatButton,slide -> choose(formatButton,"Пропорции кадра",
                CropFormats.NAMES,null,crop.format,3,index -> {
                    crop.setFormat(index);
                    updateFormat();
                },slide));
        cropButton("↻ 90°", "Повернуть по часовой стрелке на 90 градусов", 0f,
                () -> { crop.reorient(crop.orientation.rotate(), true); updateFormat(); });
        cropButton("↔", "Отразить по горизонтали", 0f,
                () -> { crop.reorient(crop.orientation.flipX(), false); updateFormat(); });
        cropButton("↕", "Отразить по вертикали", 0f,
                () -> { crop.reorient(crop.orientation.flipY(), false); updateFormat(); });
        tiltButton=cropButton("∠", "Выпрямить изображение", 0f,this::showTilt);
        return cropControls;
    }

    private Button cropButton(String text,String description,float weight,Runnable click) {
        Button button = look.button(this, text, look.tint, look.text);
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setTranslationZ(0f);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setSingleLine(true);
        button.setContentDescription(description);
        button.setTooltipText(description);
        button.setOnClickListener(v -> click.run());
        button.setAutoSizeTextTypeUniformWithConfiguration(9,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,dp(48),weight>0?1.8f:1f);
        lp.setMargins(dp(3),0,dp(3),0);
        cropControls.addView(button,lp);
        return button;
    }

    private void updateFormat() {
        String desc = CropFormats.NAMES[crop.format];
        formatButton.setText(desc + " ▾");
        formatButton.setContentDescription("Пропорции кадра: " + desc);
        int degrees=Math.round(crop.tilt());
        tiltButton.setText(degrees==0?"∠":degrees+"°");
        tiltButton.setContentDescription("Выпрямить изображение: "+degrees+" градусов");
    }

    private void showTilt() {
        if (styleOverlay!=null) return;
        ThicknessPlacement p=findPlacement(tiltButton,dp(310),dp(126),dp(108));
        if (p.width<dp(140)||p.height<dp(102)) return;
        LinearLayout card=panel();
        card.setPadding(dp(8),dp(5),dp(8),dp(5));
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setTextColor(look.text);
        title.setTextSize(14);
        title.setPadding(dp(8),0,0,0);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button reset=look.button(this,"0°",look.tint,look.text);
        reset.setContentDescription("Сбросить наклон");
        reset.setStateListAnimator(null);
        header.addView(reset,new LinearLayout.LayoutParams(dp(54),dp(48)));
        card.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
        android.widget.SeekBar dial=new android.widget.SeekBar(this);
        dial.setMax(180);
        dial.setProgress(Math.round(crop.tilt()*2)+90);
        dial.setProgressTintList(android.content.res.ColorStateList.valueOf(look.accent));
        dial.setThumbTintList(android.content.res.ColorStateList.valueOf(look.accent));
        dial.setContentDescription("Наклон, от минус 45 до плюс 45 градусов");
        card.addView(dial,new LinearLayout.LayoutParams(-1,dp(44)));
        Runnable refresh=() -> title.setText(String.format(java.util.Locale.getDefault(),
                "Выпрямить   %+.1f°",crop.tilt()));
        refresh.run();
        dial.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) {}
            @Override public void onProgressChanged(android.widget.SeekBar bar,int value,boolean user) {
                crop.setTilt((value-90)/2f);
                updateFormat();
                refresh.run();
            }
        });
        reset.setOnClickListener(v -> dial.setProgress(90));
        tiltGuide=true;
        crop.invalidate();
        saveStyle=() -> { tiltGuide=false; crop.invalidate(); };
        attachPanel(card,p,dial);
    }

    private LinearLayout makeColorControls() {
        colorControls=new LinearLayout(this);
        colorControls.setGravity(Gravity.CENTER_VERTICAL);
        colorControls.setPadding(dp(8),0,dp(8),0);
        for (int i=0;i<COLORS.length;i++) {
            final int index=i;
            View dot=new View(this);
            android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(COLORS[i]);
            circle.setStroke(dp(1),COLORS[i]==0xffffffff?0xff67758a:0x33000000);
            dot.setBackground(circle);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            swatches[i]=paletteItem(dot,COLOR_NAMES[i],20,() -> setInkColor(COLORS[index]));
        }
        eyedropperIcon=new PaletteIcon(this,look,false);
        eyedropperButton=paletteItem(eyedropperIcon,"Пипетка",26,() -> setEyePicking(!eyePicking));
        wheelIcon=new PaletteIcon(this,look,true);
        wheelButton=paletteItem(wheelIcon,"Цветовой круг",26,() -> {
            if (inlineCaption!=null) closeInlineCaption();
            setEyePicking(false);
            ColorPickerDialog.show(this,look,inkColor,this::setInkColor);
        });
        return colorControls;
    }

    private FrameLayout paletteItem(View icon,String label,int size,Runnable click) {
        FrameLayout hit=new FrameLayout(this);
        hit.setFocusable(true);
        hit.setTooltipText(label);
        hit.setContentDescription(label);
        hit.setOnClickListener(v -> click.run());
        hit.addView(icon,new FrameLayout.LayoutParams(dp(size),dp(size),Gravity.CENTER));
        colorControls.addView(hit,new LinearLayout.LayoutParams(0,dp(44),1));
        return hit;
    }

    private LinearLayout makeCaptionStyles() {
        captionStyles=new LinearLayout(this);
        captionStyles.setGravity(Gravity.CENTER_VERTICAL);
        captionStyles.setPadding(dp(8),dp(2),dp(8),0);
        captionStyles.setVisibility(View.INVISIBLE);
        for (int i=0;i<captionStyleButtons.length;i++) {
            final int flag=CAPTION_STYLE_FLAGS[i];
            Button button=look.button(this,CAPTION_STYLE_GLYPHS[i],look.tint,look.text);
            button.setTextSize(18);
            button.setTypeface(CaptionStamp.typeface(flag));
            button.setPaintFlags(captionPaintFlags(button.getPaintFlags(),flag));
            button.setStateListAnimator(null);
            button.setTooltipText(CAPTION_STYLE_NAMES[i]);
            button.setPadding(dp(4),0,dp(4),0);
            button.setOnClickListener(v -> {
                if (crop==null||crop.draft==null) return;
                draftTextStyle=CaptionStyle.toggle(draftTextStyle,flag);
                if (inlineCaption!=null) updateInlineStyle(inlineCaption);
                crop.rebuildCaption();
                refreshCaptionStyles();
            });
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(44),1);
            if (i>0) size.leftMargin=dp(6);
            captionStyles.addView(button,size);
            captionStyleButtons[i]=button;
        }
        return captionStyles;
    }

    private void refreshCaptionStyles() {
        for (int i=0;i<captionStyleButtons.length;i++) {
            Button button=captionStyleButtons[i];
            if (button==null) continue;
            boolean active=CaptionStyle.has(draftTextStyle,CAPTION_STYLE_FLAGS[i]);
            decorateShapeStyle(button,active);
            button.setContentDescription(CAPTION_STYLE_NAMES[i]+(active?", включено":", выключено"));
        }
    }

    private static int captionPaintFlags(int flags,int style) {
        flags&=~(Paint.UNDERLINE_TEXT_FLAG|Paint.STRIKE_THRU_TEXT_FLAG);
        if (CaptionStyle.has(style,CaptionStyle.UNDERLINE)) flags|=Paint.UNDERLINE_TEXT_FLAG;
        if (CaptionStyle.has(style,CaptionStyle.STRIKE)) flags|=Paint.STRIKE_THRU_TEXT_FLAG;
        return flags;
    }

    private void updateInlineStyle(android.widget.EditText field) {
        field.setTypeface(CaptionStamp.typeface(draftTextStyle));
        field.setPaintFlags(captionPaintFlags(field.getPaintFlags(),draftTextStyle));
    }

    private void addCaptionOption(Button button,float weight) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),weight);
        if (captionOptions.getChildCount()>0) p.leftMargin=dp(6);
        captionOptions.addView(button,p);
    }

    private void refreshDraftInk() {
        if (crop==null) return;
        if (crop.draftStepNumber>0) {
            crop.draftStepColor=InkOpacity.apply(inkColor,opacity);
            crop.draftMarkStroke=Math.max(1,strokeWidth);
            crop.previewStep=null;
            crop.invalidate();
        }
        if (crop.shapeDraft!=null) crop.invalidate();
        if (crop.draft==null) return;
        crop.draftCalloutColor=InkOpacity.apply(inkColor,opacity);
        if (inlineCaption!=null) inlineCaption.setTextColor(crop.draftCalloutColor);
        crop.rebuildCaption();
        refreshDraftControls();
    }

    private void setInkColor(int value) {
        inkColor=value | 0xff000000;
        getPreferences(MODE_PRIVATE).edit().putInt("stroke_ink_color",inkColor).apply();
        refreshDraftInk();
        if (eyePicking) setEyePicking(false);
        else updateStyleControls();
    }

    private void setEyePicking(boolean enabled) {
        if (eyePicking==enabled) return;
        if (enabled&&inlineCaption!=null) closeInlineCaption();
        eyePicking=enabled;
        if (crop!=null) {
            crop.resetGesture();
            crop.setContentDescription(enabled?"Веди по снимку и отпусти, чтобы взять цвет":"Область обрезки снимка");
            crop.invalidate();
        }
        if (android.os.Build.VERSION.SDK_INT>=33) {
            if (enabled) {
                closeEyeOnBack=() -> setEyePicking(false);
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,closeEyeOnBack);
            } else if (closeEyeOnBack!=null) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(closeEyeOnBack);
                closeEyeOnBack=null;
            }
        }
        if (styleControls!=null) updateStyleControls();
    }

    private LinearLayout makeStyleControls() {
        styleControls = new LinearLayout(this);
        styleControls.setGravity(Gravity.CENTER_VERTICAL);
        styleControls.setBaselineAligned(false);
        styleControls.setPadding(dp(8), 0, dp(8), 0);
        brushHint = new TextView(this);
        brushHint.setTextSize(12);
        brushHint.setTextColor(look.muted);
        brushHint.setGravity(Gravity.CENTER_VERTICAL);
        styleControls.addView(brushHint, new LinearLayout.LayoutParams(0, dp(48), 1f));

        initialMarkSizeButton=shapeStyleOption(styleControls,"Старт: авто");
        initialMarkSizeButton.setOnClickListener(v -> editInitialMarkSize());
        fillButton=shapeStyleOption(styleControls,"Контур");
        fillButton.setTooltipText("Контур или заливка");
        fillButton.setOnClickListener(v -> {
            int tool=editorTool;
            if (!Markup.isShape(tool)) return;
            shapeFills[tool]=!shapeFills[tool];
            getPreferences(MODE_PRIVATE).edit().putBoolean(shapeKey(tool)+"_filled",shapeFills[tool]).apply();
            refreshDraftInk();
            updateStyleControls();
        });
        gestureButton=shapeStyleOption(styleControls,"Жест");
        gestureButton.setTooltipText("Способ рисования фигуры");
        gestureButton.setOnClickListener(v -> chooseShapeGesture());
        directionButton=shapeStyleOption(styleControls,"По осям");
        directionButton.setTooltipText("По осям или под свободным углом");
        directionButton.setOnClickListener(v -> {
            int tool=editorTool;
            if (!Markup.isShape(tool)||tool==Markup.CIRCLE) return;
            shapeFree[tool]=!shapeFree[tool];
            getPreferences(MODE_PRIVATE).edit().putBoolean(shapeKey(tool)+"_free_direction",shapeFree[tool]).apply();
            updateStyleControls();
        });
        refineShapeButton=shapeStyleOption(styleControls,"Рамка");
        refineShapeButton.setTooltipText("Подправить после рисования");
        refineShapeButton.setOnClickListener(v -> {
            refineShape=!refineShape;
            getPreferences(MODE_PRIVATE).edit().putBoolean("shape_refine",refineShape).apply();
            updateStyleControls();
        });
        textMosaicButton = styleButton("Только текст", this::toggleTextMosaic);
        textMosaicButton.setText("Только текст");
        thicknessButton = styleButton("Толщина линии", () -> showStyle(false));
        opacityButton = styleButton("Непрозрачность линии", () -> showStyle(true));
        return styleControls;
    }

    private Button styleButton(String tooltip, Runnable open) {
        Button button = look.button(this, "", look.tint, look.text);
        button.setTextSize(12);
        button.setPadding(dp(6), dp(4), dp(6), dp(4));
        button.setMinimumWidth(dp(48));
        button.setSingleLine(true);
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setTranslationZ(0f);
        button.setTooltipText(tooltip);
        button.setOnClickListener(v -> open.run());
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
        size.leftMargin = dp(4);
        styleControls.addView(button, size);
        return button;
    }

    private void updateStyleControls() {
        boolean drawing=editorTool!=Markup.CROP && !isSpecialBrush();
        boolean mosaic=editorTool==Markup.MOSAIC;
        textMosaicButton.setVisibility(mosaic?View.VISIBLE:View.GONE);
        textMosaicButton.setText(textMosaic && textDetecting ? "Ищу текст…" :
                textMosaic?"✓ Только текст":"Только текст");
        textMosaicButton.setSelected(textMosaic);
        textMosaicButton.setTextColor(textMosaic?look.accent:look.text);
        textMosaicButton.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(look.dark?0x34ffffff:0x24001c40),
                look.shape(this,look.tint,16,textMosaic),
                look.shape(this,Color.WHITE,16,false)));
        textMosaicButton.setContentDescription(textMosaic?"Только текст, включено. "+textMosaicStatus():"Только текст, выключено");
        colorControls.setVisibility(drawing?View.VISIBLE:View.GONE);
        styleControls.setVisibility(editorTool==Markup.CROP?View.GONE:crop!=null&&crop.draft!=null?View.INVISIBLE:View.VISIBLE);
        opacityButton.setVisibility(drawing||editorTool==Markup.MOSAIC?View.VISIBLE:View.GONE);
        boolean shape=Markup.isShape(editorTool)&&!isMarkTool(editorTool);
        updateShapeOptions(shape);
        thicknessButton.setVisibility(shape&&shapeFills[editorTool]&&!Markup.isOpenShape(editorTool)?View.GONE:View.VISIBLE);
        boolean mark=isMarkTool(editorTool);
        boolean canSetMarkSize=mark&&(crop==null||crop.draftStepNumber==0);
        initialMarkSizeButton.setVisibility(canSetMarkSize?View.VISIBLE:View.GONE);
        initialMarkSizeButton.setText(initialMarkWidth>0?"Старт: "+initialMarkWidth+" px":"Старт: авто");
        initialMarkSizeButton.setContentDescription(initialMarkWidth>0?
                "Стартовый размер метки: "+initialMarkWidth+" пикселей":"Стартовый размер метки: автоматически");
        brushHint.setVisibility(shape||canSetMarkSize||mark&&!eyePicking?View.GONE:View.VISIBLE);
        brushHint.setText(eyePicking?"Веди пальцем и отпусти":editorTool==Markup.ERASER
                ?"Стирает разметку и мозаику":mosaic?textMosaicStatus():editorTool==Markup.TEXT?
                "Коснись снимка для подписи":editorTool==Markup.CALLOUT?"Коснись снимка для выноски":isMarkTool(editorTool)?"Коснись, чтобы поставить ровно":shape?"Цвет фигуры":"Цвет линии");
        boolean preset=false;
        for (int i=0;i<swatches.length;i++) {
            boolean picked=inkColor==COLORS[i];
            preset|=picked;
            swatches[i].setContentDescription(COLOR_NAMES[i]+(picked?", выбран":""));
            decoratePalette(swatches[i],picked);
        }
        eyedropperButton.setContentDescription(eyePicking?"Пипетка включена. Веди по снимку и отпусти":"Пипетка");
        eyedropperIcon.setPreview(0,eyePicking);
        decoratePalette(eyedropperButton,eyePicking);
        wheelIcon.setPreview(inkColor,!preset);
        decoratePalette(wheelButton,!preset);
        wheelButton.setContentDescription("Цветовой круг, "+String.format(java.util.Locale.ROOT,"#%06X",inkColor & 0xffffff));
        updateStyleButtons();
    }

    private void editInitialMarkSize() {
        setEyePicking(false);
        android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this,look.dark?
                android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert);
        LinearLayout box=new LinearLayout(builder.getContext());
        box.setPadding(dp(22),dp(4),dp(22),0);
        android.widget.EditText field=new android.widget.EditText(builder.getContext());
        field.setHint("Например, 20");
        field.setContentDescription("Стартовая ширина в пикселях");
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        if (initialMarkWidth>0) field.setText(Integer.toString(initialMarkWidth));
        field.setSelectAllOnFocus(true);
        box.addView(field,new LinearLayout.LayoutParams(-1,-2));
        android.app.AlertDialog dialog=builder.setTitle("Стартовый размер меток")
                .setMessage("Ширина новых меток в пикселях снимка, от 1 до "+MarkPlacement.MAX_INITIAL_WIDTH_PX+".")
                .setView(box)
                .setNegativeButton("Отмена",null)
                .setNeutralButton("Авто",(d,which) -> setInitialMarkSize(0))
                .setPositiveButton("Сохранить",null).create();
        if (dialog.getWindow()!=null) {
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        field.requestFocus();
        dialog.show();
        Runnable submit=() -> {
            int px;
            try { px=Integer.parseInt(field.getText().toString().trim()); }
            catch (NumberFormatException invalid) { px=0; }
            if (px<1 || px>MarkPlacement.MAX_INITIAL_WIDTH_PX) {
                field.setError("Введите число от 1 до "+MarkPlacement.MAX_INITIAL_WIDTH_PX);
                return;
            }
            setInitialMarkSize(px);
            dialog.dismiss();
        };
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit.run());
        field.setOnEditorActionListener((v,action,event) -> {
            if (action!=android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            submit.run();
            return true;
        });
    }

    private void setInitialMarkSize(int px) {
        initialMarkWidth=MarkPlacement.normalizeInitialWidth(px);
        getPreferences(MODE_PRIVATE).edit().putInt("mark_initial_width_px",initialMarkWidth).apply();
        updateStyleControls();
    }

    private String textMosaicStatus() {
        if (!textMosaic) return "Мозаика";
        if (textDetecting) return "Ищу текст…";
        if (textWords==null || textWords.length==0) return "Текст не найден";
        return "Проведи по словам";
    }

    private void toggleTextMosaic() {
        crop.resetGesture();
        crop.invalidate();
        textMosaic=!textMosaic;
        if (textMosaic && textWords==null) startTextDetection();
        updateStyleControls();
    }

    private void startTextDetection() {
        if (textDetecting) return;
        textDetecting=true;
        final android.content.Context context=getApplicationContext();
        final Bitmap image=source;
        textDetectionThread=new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            int[] found=null;
            try { found=TextOcr.detect(context,image); }
            catch (Exception | LinkageError | OutOfMemoryError ignored) {
                // Do not log screen contents or OCR output.
            }
            final int[] result=found;
            if (Thread.currentThread().isInterrupted()) return;
            runOnUiThread(() -> finishTextDetection(result));
        },"skrinim-text");
        textDetectionThread.start();
    }

    private void finishTextDetection(int[] result) {
        if (isFinishing() || isDestroyed()) return;
        textDetectionThread=null;
        textDetecting=false;
        textWords=result;
        if (result==null) {
            if (textMosaic && editorTool==Markup.MOSAIC)
                Toast.makeText(this,"Не удалось распознать текст",Toast.LENGTH_SHORT).show();
            textMosaic=false;
        } else if (result.length==0 && textMosaic && editorTool==Markup.MOSAIC) {
            Toast.makeText(this,"Текст не найден. Можно переключиться на обычную мозаику.",Toast.LENGTH_SHORT).show();
        }
        updateStyleControls();
    }

    private void decoratePalette(View item,boolean selected) {
        item.setSelected(selected);
        item.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x24005aba),
                look.shape(this,selected?look.tint:Color.TRANSPARENT,12,selected),
                look.shape(this,Color.WHITE,12,false)));
    }

    private void updateStyleButtons() {
        int width = activeWidth();
        boolean badge=editorTool==Markup.STEP;
        thicknessButton.setText(badge?"Жирность "+stepWeight+"%":width+" px");
        thicknessButton.setContentDescription(badge?"Жирность цифры: "+stepWeight+" процентов":
                (isSpecialBrush()?"Диаметр кисти: ":(editorTool==Markup.TEXT||editorTool==Markup.CALLOUT)?
                        "Размер текста: ":"Толщина линии: ")+width+" пикс.");
        thicknessButton.setTooltipText(badge?"Жирность цифры":"Толщина линии");
        boolean pixel = editorTool == Markup.MOSAIC;
        opacityButton.setText(pixel ? "Блок " + pixelSize + " px" : opacity + "%");
        String alphaLabel=isMarkTool(editorTool)?"Непрозрачность метки":"Непрозрачность линии";
        opacityButton.setContentDescription(pixel ? "Размер блока мозаики: " + pixelSize + " пикс."
                : alphaLabel + ": " + opacity + "%");
        opacityButton.setTooltipText(pixel ? "Пикселизация" : alphaLabel);
    }

    private boolean isSpecialBrush() { return editorTool == Markup.ERASER || editorTool == Markup.MOSAIC; }
    private int activeWidth() {
        if (editorTool == Markup.TEXT || editorTool==Markup.CALLOUT) return captionWidth;
        if (editorTool == Markup.ERASER) return eraserWidth;
        return editorTool == Markup.MOSAIC ? mosaicWidth : strokeWidth;
    }

    private void dismissStyle() {
        if (styleOverlay == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 33 && closeStyleOnBack != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(closeStyleOnBack);
            closeStyleOnBack = null;
        }
        if (slideMenu!=null) {
            slideMenu.close();
            slideMenu=null;
        }
        root.removeView(styleOverlay);
        styleOverlay = null;
        if (saveStyle != null) {
            saveStyle.run();
            saveStyle = null;
        }
        editor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (styleOverlay != null) dismissStyle();
        else if (eyePicking) setEyePicking(false);
        else if (inlineCaption!=null) closeInlineCaption();
        else if (crop!=null && crop.hasDraft()) finishCaption(false);
        else super.onBackPressed();
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        dismissStyle();
        setEyePicking(false);
    }

    private void showStyle(boolean editSecondary) {
        showStyle(editSecondary,editSecondary?opacityButton:thicknessButton);
    }

    private void showStyle(boolean editSecondary,Button anchor) {
        boolean editPixel = editSecondary && editorTool == Markup.MOSAIC;
        boolean editOpacity = editSecondary && !editPixel;
        if (styleOverlay != null) {
            dismissStyle();
            return;
        }
        setEyePicking(false);
        int labelsAndPadding=dp(24)*2+dp(5)+dp(6);
        ThicknessPlacement placement=findPlacement(anchor,dp(editSecondary?145:98),
                dp(178)+labelsAndPadding,dp(66)+labelsAndPadding);
        int sliderHeight = placement.height - labelsAndPadding;
        if (sliderHeight < dp(48) || placement.width < dp(48)) return;

        int mode = editorTool;
        boolean editBadge=!editSecondary && mode==Markup.STEP;
        StrokeSize size = mode == Markup.MOSAIC ? mosaicSize : mode == Markup.ERASER ? eraserSize : (mode==Markup.TEXT||mode==Markup.CALLOUT) ? captionSize : strokeSize;
        String widthKey = mode == Markup.MOSAIC ? "mosaic_progress" : mode == Markup.ERASER ? "eraser_progress_v2" : (mode==Markup.TEXT||mode==Markup.CALLOUT) ? "caption_progress" : "stroke_progress";
        String title = editBadge ? "Жирность" : editPixel ? "Пикселизация" : editOpacity ? "Непрозрачность" : isSpecialBrush() ? "Диаметр" : (mode==Markup.TEXT||mode==Markup.CALLOUT) ? "Размер текста" : "Толщина";
        String unit = editOpacity || editBadge ? "%" : " px";
        int initial = editBadge ? stepWeight : editPixel ? pixelSize : editOpacity ? opacity : activeWidth();
        LinearLayout panel=panel();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(5),dp(5),dp(5),dp(6));

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(12);
        label.setTextColor(look.text);
        label.setGravity(Gravity.CENTER);
        panel.addView(label, new LinearLayout.LayoutParams(-1, dp(24)));

        TextView valueLabel = new TextView(this);
        valueLabel.setGravity(Gravity.CENTER);
        valueLabel.setTextSize(12);
        valueLabel.setTextColor(look.muted);
        valueLabel.setText(initial + unit);
        StrokeSlider slider = new StrokeSlider(this, look, editPixel ? minPixelSize : editOpacity || editBadge ? 0 : size.min,
                editPixel ? maxPixelSize : editOpacity || editBadge ? 100 : size.max, initial,
                isSpecialBrush() ? look.muted : inkColor, editOpacity, editPixel,
                !editSecondary&&size==captionSize?captionSize.max/5:0, value -> {
            if (editBadge) {
                stepWeight=value;
                refreshDraftInk();
            } else if (editPixel) pixelSize = value;
            else if (editOpacity) {
                opacity=value;
                refreshDraftInk();
            }
            else if (mode == Markup.MOSAIC) mosaicWidth = value;
            else if (mode == Markup.ERASER) eraserWidth = value;
            else if ((mode==Markup.TEXT||mode==Markup.CALLOUT)) captionWidth = value;
            else {
                strokeWidth=value;
                refreshDraftInk();
            }
            valueLabel.setText(value + unit);
            updateStyleButtons();
        });
        panel.addView(slider, new LinearLayout.LayoutParams(-1, sliderHeight));
        panel.addView(valueLabel, new LinearLayout.LayoutParams(-1, dp(24)));

        saveStyle = () -> {
            if (editBadge) {
                if (stepWeight!=initial) getPreferences(MODE_PRIVATE).edit().putInt("step_weight",stepWeight).apply();
            } else if (editPixel && pixelSize != initial) {
                getPreferences(MODE_PRIVATE).edit().putInt("mosaic_block_size", pixelSize).apply();
            } else if (editOpacity && opacity != initial) {
                getPreferences(MODE_PRIVATE).edit().putInt("stroke_opacity", opacity).apply();
            } else if (!editSecondary && activeWidth() != initial) {
                if (size==captionSize) getPreferences(MODE_PRIVATE).edit().putInt("caption_pixels_v2",captionWidth).apply();
                else getPreferences(MODE_PRIVATE).edit().putInt(widthKey, size.progress(activeWidth())).apply();
            }
        };
        attachPanel(panel,placement,inlineCaption==null?slider:null);
    }

    private void openCaption(float touchX,float touchY) {
        if (isFinishing() || crop.selected.isEmpty() || crop.image.isEmpty() || opacity==0) return;
        startCaption(touchX,touchY);
    }

    private void startCaption(float x,float y) {
        float scale=crop.captionViewScale();
        android.graphics.RectF frame=crop.selected;
        float boundW=frame.width()/scale,boundH=frame.height()/scale;
        float minW=Math.min(boundW,dp(52)/scale),minH=Math.min(boundH,dp(38)/scale);
        float width=Math.min(boundW,Math.max(minW,dp(146)/scale));
        float height=Math.min(boundH,Math.max(minH,dp(52)/scale));
        float cx=Math.max(frame.left+width*scale/2,Math.min(frame.right-width*scale/2,x));
        float cy=Math.max(frame.top+height*scale/2,Math.min(frame.bottom-height*scale/2,y));
        draftValue="";
        crop.lastCaptionTap=0;
        captionWrap=getPreferences(MODE_PRIVATE).getBoolean("caption_wrap",true);
        crop.draftFontScale=1;
        crop.draftFontPixels=captionWidth;
        crop.draftCaptionMinWidth=minW;
        crop.draftIsCallout=editorTool==Markup.CALLOUT;
        crop.draftCalloutColor=InkOpacity.apply(inkColor,opacity);
        crop.draftCalloutStroke=Math.max(2,captionWidth*.055f);
        float tx=cx+(cx+width*scale/2+dp(40)<frame.right?1:-1)*(width*scale/2+dp(27));
        float ty=cy+(cy+height*scale/2+dp(32)<frame.bottom?1:-1)*(height*scale/2+dp(23));
        crop.draftTipX=crop.orientedX(Math.max(frame.left,Math.min(frame.right,tx)));
        crop.draftTipY=crop.orientedY(Math.max(frame.top,Math.min(frame.bottom,ty)));
        crop.draftPad=Math.min(6,Math.min(boundW,boundH)*.05f);
        crop.draftMinWidth=minW;
        crop.draftMinHeight=minH;
        crop.draftPose=new CaptionTransform(crop.orientedX(cx),crop.orientedY(cy));
        crop.draftBox=new CaptionFrame(crop.draftPose.cx,crop.draftPose.cy,width,height);
        crop.draftBox.heightFloor=height;
        crop.draft=new CaptionStamp("",crop.draftCalloutColor,captionWidth,
                Math.max(1,(int)(width-2*crop.draftPad)),captionWrap,draftTextStyle);
        refreshDraftControls();
        captionMode(true);
    }

    private void refreshDraftControls() {
        draftFontButton.setText("Размер: "+captionWidth+" px");
        draftWrapButton.setText(captionWrap?"С переносом":"Одна строка");
        draftOpacityButton.setText(opacity+"%");
        draftOpacityButton.setContentDescription("Непрозрачность текста: "+opacity+" процентов");
        draftOpacityButton.setTooltipText("Непрозрачность текста");
        draftWrapButton.setContentDescription(captionWrap?"Перенос строк включён":"Перенос строк выключен");
        if (imeFontButton!=null && imeWrapButton!=null) {
            imeFontButton.setText("Размер: "+captionWidth+" px");
            imeWrapButton.setText(captionWrap?"С переносом":"Одна строка");
            imeWrapButton.setContentDescription(draftWrapButton.getContentDescription());
            imeOpacityButton.setText(opacity+"%");
            imeOpacityButton.setContentDescription(draftOpacityButton.getContentDescription());
        }
    }

    private void toggleDraftWrap() {
        if (crop.draft==null) return;
        captionWrap=!captionWrap;
        getPreferences(MODE_PRIVATE).edit().putBoolean("caption_wrap",captionWrap).apply();
        if (inlineCaption!=null) {
            inlineCaption.setHorizontallyScrolling(!captionWrap);
            inlineCaption.setMaxLines(captionWrap?Integer.MAX_VALUE:1);
        }
        crop.rebuildCaption();
        refreshDraftControls();
    }

    private void showDraftFont(Button anchor) {
        if (crop.draft==null) return;
        if (styleOverlay!=null) { dismissStyle(); return; }
        int chrome=dp(60);
        ThicknessPlacement placement=findPlacement(anchor,dp(110),dp(178)+chrome,dp(66)+chrome);
        if (placement.height-chrome<dp(48)||placement.width<dp(48)) return;
        LinearLayout panel=panel();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(5),dp(5),dp(5),dp(5));
        TextView title=new TextView(this);
        title.setText("Размер текста");
        title.setTextColor(look.text);
        title.setGravity(Gravity.CENTER);
        panel.addView(title,new LinearLayout.LayoutParams(-1,dp(24)));
        TextView number=new TextView(this);
        number.setGravity(Gravity.CENTER);
        number.setTextColor(look.muted);
        number.setText(captionWidth+" px");
        StrokeSlider slider=new StrokeSlider(this,look,captionSize.min,captionSize.max,captionWidth,
                inkColor,false,false,captionSize.max/5,value -> {
            captionWidth=value;
            number.setText(value+" px");
            crop.rebuildCaption();
            refreshDraftControls();
        });
        panel.addView(slider,new LinearLayout.LayoutParams(-1,placement.height-chrome));
        panel.addView(number,new LinearLayout.LayoutParams(-1,dp(24)));
        saveStyle=() -> getPreferences(MODE_PRIVATE).edit().putInt("caption_pixels_v2",captionWidth).apply();
        attachPanel(panel,placement,inlineCaption==null?slider:null);
    }

    private int imeToolbarHeight() { return dp(44)+dp(48); }

    private void setupImeCaptionControls() {
        imeCaptionControls=new LinearLayout(this);
        imeCaptionControls.setOrientation(LinearLayout.VERTICAL);
        imeCaptionControls.setBackground(look.shape(this,look.panel,16,true));
        imeCaptionControls.setElevation(dp(3));
        imeCaptionControls.setVisibility(View.GONE);
        imeCaptionButtons=new LinearLayout(this);
        imeCaptionButtons.setOrientation(LinearLayout.HORIZONTAL);
        imeCaptionButtons.setPadding(dp(3),dp(2),dp(3),dp(2));
        imeCaptionButtons.setGravity(Gravity.CENTER_VERTICAL);
        imeCaptionControls.addView(imeCaptionButtons,new LinearLayout.LayoutParams(-1,dp(48)));
        imeFontButton=imeControl("",look.tint,look.text,1.2f,() -> showDraftFont(imeFontButton));
        imeWrapButton=imeControl("",look.tint,look.text,1.2f,this::toggleDraftWrap);
        imeOpacityButton=imeControl("",look.tint,look.text,.73f,() -> showStyle(true,imeOpacityButton));
        Button done=imeControl("Готово",look.accent,look.onAccent,.9f,this::closeInlineCaption);
        done.setContentDescription("Готово, закрыть клавиатуру");
        root.addView(imeCaptionControls,new FrameLayout.LayoutParams(-1,imeToolbarHeight(),Gravity.BOTTOM));
    }

    private void moveCaptionPalette(boolean intoIme) {
        android.view.ViewGroup want=intoIme?imeCaptionControls:paletteSlot;
        if (colorControls.getParent()==want) return;
        if (colorControls.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup)colorControls.getParent()).removeView(colorControls);
        if (intoIme) imeCaptionControls.addView(colorControls,0,new LinearLayout.LayoutParams(-1,dp(44)));
        else paletteSlot.addView(colorControls,new FrameLayout.LayoutParams(-1,-1));
    }

    private void compactDraftAction(LinearLayout row,String label,String description,int bg,int fg,float weight,Runnable run) {
        Button button=look.button(this,label,bg,fg);
        compactCaptionButton(button);
        button.setContentDescription(description);
        button.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(44),weight);
        if (row.getChildCount()>0) size.leftMargin=dp(8);
        row.addView(button,size);
    }

    private void compactCaptionButton(Button button) {
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(4),dp(2),dp(4),dp(2));
        button.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        button.setStateListAnimator(null);
    }

    private Button imeControl(String label,int background,int foreground,float weight,Runnable perform) {
        Button button=look.button(this,label,background,foreground);
        compactCaptionButton(button);
        button.setOnClickListener(v -> perform.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,weight);
        if (imeCaptionButtons.getChildCount()>0) params.leftMargin=dp(4);
        imeCaptionButtons.addView(button,params);
        return button;
    }

    private void onCaptionImeInsets(android.view.WindowInsets insets) {
        boolean shown=insets.isVisible(android.view.WindowInsets.Type.ime());
        imeBottom=insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
        if (inlineCaption!=null) {
            if (shown) inlineKeyboardShown=true;
            else if (inlineKeyboardShown) {
                android.widget.EditText field=inlineCaption;
                root.post(() -> {
                    android.view.WindowInsets current=root.getRootWindowInsets();
                    if (inlineCaption==field && current!=null
                            && !current.isVisible(android.view.WindowInsets.Type.ime())) {
                        closeInlineCaption();
                    }
                });
            }
            positionImeCaptionControls();
        }
    }

    private int imeDockBottom() { return imeControlsBottom; }

    private void positionImeCaptionControls() {
        if (inlineCaption==null) return;
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)imeCaptionControls.getLayoutParams();
        // Some OEM windows report no IME inset in ADJUST_NOTHING, but still update the visible frame.
        root.getWindowVisibleDisplayFrame(imeVisibleFrame);
        root.getLocationOnScreen(imeRootOnScreen);
        int bottom=InlineCaptionDock.bottom(root.getHeight(),imeBaselineHeight,imeBottom,safeBottom,imeRootOnScreen[1],
                imeVisibleFrame.bottom,imeToolbarHeight(),dp(2));
        imeControlsBottom=bottom;
        int left=safeLeft+dp(6),right=safeRight+dp(6);
        if (params.leftMargin!=left||params.rightMargin!=right||params.bottomMargin!=bottom) {
            params.leftMargin=left;params.rightMargin=right;params.bottomMargin=bottom;
            imeCaptionControls.setLayoutParams(params);
        }
        layoutInlineCaption();
    }

    private void activateCaptionIme() {
        if (imeCaptionControls==null) setupImeCaptionControls();
        refreshDraftControls();
        safeLeft=editor.getPaddingLeft();
        safeTop=editor.getPaddingTop();
        safeRight=editor.getPaddingRight();
        safeBottom=editor.getPaddingBottom();
        imeBottom=0;
        imeBaselineHeight=root.getHeight();
        try {
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        } catch (RuntimeException ignored) {
            // An OEM may keep its default keyboard layout policy; text editing still works.
        }
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            if (inlineCaption!=null) {
                try { onCaptionImeInsets(insets); }
                catch (RuntimeException ignored) { /* Keep the OEM keyboard usable. */ }
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private void editCaptionInline() {
        if (inlineCaption!=null || crop.draft==null || isFinishing()) return;
        activateCaptionIme();
        android.widget.EditText field=new android.widget.EditText(this);
        inlineCaption=field;
        inlineKeyboardShown=false;
        field.setGravity(Gravity.TOP|Gravity.LEFT);
        field.setPadding(0,0,0,0);
        field.setTextColor(crop.draftCalloutColor);
        field.setHintTextColor(look.muted);
        field.setHint("Введи текст");
        field.setContentDescription("Текст на снимке");
        field.setBackgroundColor(Color.TRANSPARENT);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                |android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                |android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                |android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                |android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                |android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(CaptionStamp.MAX_CHARS)});
        field.setHorizontallyScrolling(!captionWrap);
        field.setMaxLines(captionWrap?Integer.MAX_VALUE:1);
        updateInlineStyle(field);
        field.setText(draftValue);
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {
                draftValue=s.toString();
                if (crop.draft!=null) crop.rebuildCaption();
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
        root.addView(field,new FrameLayout.LayoutParams(1,1));
        root.getViewTreeObserver().addOnGlobalLayoutListener(imeMeasure);
        captionActions.setVisibility(View.INVISIBLE);
        captionOptions.setVisibility(View.INVISIBLE);
        moveCaptionPalette(true);
        imeCaptionControls.setVisibility(View.VISIBLE);
        imeCaptionControls.bringToFront();
        positionImeCaptionControls();
        field.requestFocus();
        field.setSelection(field.length());
        field.post(() -> {
            if (inlineCaption==field) getSystemService(android.view.inputmethod.InputMethodManager.class)
                    .showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        crop.invalidate();
    }

    private void layoutInlineCaption() {
        if (inlineCaption==null||crop.draft==null||crop.draftBox==null||crop.image.isEmpty()) return;
        float scale=crop.captionViewScale();
        float pad=crop.draftPad*scale;
        int width=Math.max(dp(18),Math.round(crop.draftBox.width*scale-2*pad));
        int height=Math.max(dp(22),Math.round(crop.draftBox.height*scale-2*pad));
        int[] rootPosition=new int[2],cropPosition=new int[2];
        root.getLocationInWindow(rootPosition);
        crop.getLocationInWindow(cropPosition);
        int left=Math.round(cropPosition[0]-rootPosition[0]+crop.image.left+crop.draftPose.cx*scale-width*.5f)
                -root.getPaddingLeft();
        int top=Math.round(cropPosition[1]-rootPosition[1]+crop.image.top+crop.draftPose.cy*scale-height*.5f)
                -root.getPaddingTop();
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)inlineCaption.getLayoutParams();
        if (params.width!=width||params.height!=height||params.leftMargin!=left||params.topMargin!=top) {
            params.width=width; params.height=height;
            params.leftMargin=left;params.topMargin=top;
            inlineCaption.setLayoutParams(params);
        }
        inlineCaption.setRotation(crop.draftPose.angle);
        float roomBottom=root.getHeight()-imeDockBottom()-imeToolbarHeight()-dp(2);
        float roomTop=safeTop+dp(4);
        inlineCaption.setTranslationY(InlineCaptionDock.shift(top+height*.5f,width,height,
                crop.draftPose.angle,roomTop,roomBottom));
        float pixels=Math.max(1f,captionWidth*scale);
        if (Math.abs(inlineCaption.getTextSize()-pixels)>.1f)
            inlineCaption.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,pixels);
    }

    private void closeInlineCaption() {
        android.widget.EditText field=inlineCaption;
        if (field==null) return;
        inlineCaption=null;
        inlineKeyboardShown=false;
        if (styleOverlay!=null) dismissStyle();
        getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(field.getWindowToken(),0);
        root.getViewTreeObserver().removeOnGlobalLayoutListener(imeMeasure);
        root.removeView(field);
        imeCaptionControls.setVisibility(View.GONE);
        moveCaptionPalette(false);
        root.setOnApplyWindowInsetsListener(null);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (crop!=null&&crop.hasDraft()) captionActions.setVisibility(View.VISIBLE);
        if (crop!=null&&crop.draft!=null) captionOptions.setVisibility(View.VISIBLE);
        if (crop!=null) { crop.requestFocus(); crop.invalidate(); }
    }

    private void captionMode(boolean active) {
        toolSelectors.setVisibility(active?View.INVISIBLE:View.VISIBLE);
        updateStyleControls();
        captureActions.setVisibility(active?View.INVISIBLE:View.VISIBLE);
        boolean styleDraft=active&&crop.draft!=null;
        captionHint.setVisibility(active&&!styleDraft?View.VISIBLE:View.INVISIBLE);
        captionStyles.setVisibility(styleDraft?View.VISIBLE:View.INVISIBLE);
        if (styleDraft) refreshCaptionStyles();
        captionOptions.setVisibility(active&&crop.draft!=null?View.VISIBLE:View.GONE);
        captionActions.setVisibility(active?View.VISIBLE:View.INVISIBLE);
        if (active) captionHint.setText(crop.symbolDraft()?
                "Углы: масштаб · ↻ Поворот · Середина: сдвиг":crop.shapeDraft!=null?
                "Середина: сдвиг · Грани и углы: размер · ↻ Поворот":
                crop.draftIsCallout?"Дважды: ввод · Грани: рамка · Углы: поворот · ● Наконечник":
                        "Дважды: ввод · Грани: рамка · Углы: поворот");
        crop.setContentDescription(active?"Рамка размещения":"Область обрезки снимка");
        if (active && android.os.Build.VERSION.SDK_INT>=33) {
            closeCaptionOnBack=() -> {
                if (styleOverlay!=null) dismissStyle();
                else if (eyePicking) setEyePicking(false);
                else if (inlineCaption!=null) closeInlineCaption();
                else finishCaption(false);
            };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,closeCaptionOnBack);
        } else if (!active) releaseCaptionBack();
        crop.invalidate();
    }

    private void releaseCaptionBack() {
        if (android.os.Build.VERSION.SDK_INT>=33 && closeCaptionOnBack!=null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(closeCaptionOnBack);
            closeCaptionOnBack=null;
        }
    }

    private void finishCaption(boolean apply) {
        if (!crop.hasDraft()) return;
        if (apply&&crop.draft!=null&&draftValue.trim().isEmpty()) {
            Toast.makeText(this,"Дважды коснись рамки и введи текст.",Toast.LENGTH_SHORT).show();
            return;
        }
        if (eyePicking) setEyePicking(false);
        closeInlineCaption();
        if (styleOverlay!=null) dismissStyle();
        if (apply) crop.applyCaption();
        crop.draft=null;
        draftValue="";
        crop.shapeDraft=null;
        crop.draftStepNumber=0;
        crop.calloutPreview=null;
        crop.draftPose=null;
        crop.draftBox=null;
        crop.captionDrag=0;
        crop.pointer=-1;
        captionMode(false);
        redrawTools();
    }

    private LinearLayout panel() {
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(look.shape(this,look.panel,18,true));
        box.setElevation(dp(6));
        box.setClickable(true);
        box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return box;
    }

    private ThicknessPlacement findPlacement(Button anchor,int width,int height,int minHeight) {
        int[] xy=new int[2],origin=new int[2];
        anchor.getLocationInWindow(xy);
        root.getLocationInWindow(origin);
        int bottom=inlineCaption!=null?imeDockBottom():editor.getPaddingBottom();
        return ThicknessPlacement.fit(editor.getPaddingLeft()+dp(6),editor.getPaddingTop()+dp(6),
                root.getWidth()-editor.getPaddingRight()-dp(6),root.getHeight()-bottom-dp(6),
                xy[0]-origin[0]+anchor.getWidth(),xy[1]-origin[1],width,height,minHeight,dp(6));
    }

    private void attachPanel(LinearLayout panel,ThicknessPlacement placement,View focus) {
        FrameLayout overlay = new FrameLayout(this);
        View close = new View(this);
        close.setContentDescription("Закрыть настройку");
        close.setOnClickListener(v -> dismissStyle());
        overlay.addView(close, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams card = new FrameLayout.LayoutParams(placement.width, placement.height,
                Gravity.TOP | Gravity.LEFT);
        card.leftMargin = placement.x;
        card.topMargin = placement.y;
        overlay.addView(panel, card);

        styleOverlay = overlay;
        editor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        if (focus != null) focus.requestFocus();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            closeStyleOnBack = this::dismissStyle;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, closeStyleOnBack);
        }
    }

    private void redrawTools() {
        if (toolGroup()==Markup.PEN) {
            int i=editorTool==Markup.LINE?1:editorTool==Markup.ARROW?2:0;
            toolCaption(tools[Markup.PEN],PENCIL_ICONS[i],"Карандаш");
            tools[Markup.PEN].setContentDescription("Карандаш: "+pencilLabels[i]);
        }
        if (toolGroup()==Markup.OVAL) {
            int i=0;
            while (shapeModes[i]!=editorTool) i++;
            toolCaption(tools[Markup.OVAL],SHAPE_ICONS[i],"Фигуры");
            tools[Markup.OVAL].setContentDescription("Фигуры: "+shapeLabels[i]);
        }
        if (toolGroup()==Markup.STEP) {
            int i=editorTool==Markup.CHECK?0:editorTool==Markup.CROSS?1:2;
            toolCaption(tools[Markup.STEP],MARK_ICONS[i],"Метки");
            tools[Markup.STEP].setContentDescription("Метки: "+markLabels[i]);
        }
        for (int i = 0; i < tools.length; i++) {
            Button button = tools[i];
            if (button == null) continue;
            boolean selected = i == toolGroup();
            button.setSelected(selected);
            button.setTextColor(selected ? look.accent : look.text);
            button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x24005aba),
                look.shape(this, selected ? look.tint : android.graphics.Color.TRANSPARENT, 14, false),
                look.shape(this, 0xffffffff, 14, false)));
        }
        cropControls.setVisibility(editorTool == Markup.CROP ? View.VISIBLE : View.GONE);
        refreshScrollButtons();
        updateFormat();
        undo.setEnabled(crop.markup.hasMarks());
        undo.setAlpha(undo.isEnabled() ? 1f : 0.46f);
        updateStyleControls();
    }

    private void action(LinearLayout row, String label, String accessibility,
                        int background, int foreground, float weight, Runnable run) {
        Button button = look.button(this, label, background, foreground);
        // Keep the full shape within the measured row; no system elevation animation.
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setTranslationZ(0f);
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(52));
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setContentDescription(accessibility);
        button.setSingleLine(true);
        if (weight>0f) button.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        button.setOnClickListener(v -> run.run());
        if (weight == 0f) {
            button.setTextSize(23);
            button.setTooltipText(accessibility);
        }
        LinearLayout.LayoutParams size = weight == 0f
                ? new LinearLayout.LayoutParams(dp(48), -2)
                : new LinearLayout.LayoutParams(0, -2, weight);
        size.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, size);
    }

    private final class CropView extends View {
        private final Markup markup;
        private CaptionStamp draft;
        private CaptionTransform draftPose;
        private CaptionFrame draftBox;
        private Markup.ShapeDraft shapeDraft;
        private int draftStepNumber,draftStepColor,draftMarkTool;
        private float draftMarkStroke,draftMarkInitialRadius;
        private float previewStepRadius=-1;
        private Markup.Stamp previewStep;
        private final float[] identityAxes={1,0,0,1};
        private float shapeBaseX,shapeBaseY,shapeBaseW,shapeBaseH;
        private final Matrix shapeEditMatrix=new Matrix();
        private final float[] shapeMatrixValues=new float[9];
        private boolean draftIsCallout;
        private CalloutStamp calloutPreview;
        private int draftCalloutColor;
        private static final int DRAFT_GRIP=5,DRAFT_ARROW=6;
        private float draftFontScale,draftFontPixels,draftCaptionMinWidth,
                draftPad,draftMinWidth,draftMinHeight,draftTipX,draftTipY,draftCalloutStroke;
        private float gripX,gripY,gripAnchorX,gripAnchorY,captionTipStartX,captionTipStartY;
        private long lastCaptionTap;
        private float lastCaptionTapX,lastCaptionTapY;
        private int captionDrag,captionSideX,captionSideY;
        private float captionStartX,captionStartY,captionStartW,captionStartH,captionStartFloor,
                captionStartAngle,captionVectorX,captionVectorY;
        private final CaptionTransform captionStartPose=new CaptionTransform(0,0);
        private final android.graphics.Path captionOutline=new android.graphics.Path();
        private static final int LEFT=CropAspect.LEFT, RIGHT=CropAspect.RIGHT,
                TOP=CropAspect.TOP, BOTTOM=CropAspect.BOTTOM, MOVE=16;
        private ImageTransform orientation;
        private final Matrix transform = new Matrix();
        private final Matrix inverseTransform = new Matrix();
        private final RectF tiltBasis = new RectF();
        private TiltGeometry tiltGeometry = new TiltGeometry(0);
        private final float[] aspectBounds = new float[4];
        private int format;
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint exportTiltPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF image = new RectF();
        private final RectF bitmapFrame;
        private final RectF selected = new RectF();
        private final RectF startRect = new RectF();
        private int drag, pointer = -1;
        private float startX, startY, brushX, brushY;
        private boolean moved;
        private final EditorZoom zoom = new EditorZoom();
        private final EditorZoom.Gesture pinch = new EditorZoom.Gesture();
        // Capture the crop in oriented image pixels once, not once per animation frame.
        private final RectF zoomCrop = new RectF(), startZoomCrop = new RectF();
        private float fitWidth, fitHeight, pinchSpan, pinchX, pinchY;
        private float sampleX, sampleY;
        private final EyedropperLoupe loupe;
        private final int[] samplePixels = new int[EyedropperLoupe.GRID * EyedropperLoupe.GRID];

        CropView(Bitmap bitmap) {
            super(CropActivity.this);
            this.bitmap = bitmap;
            bitmapFrame=new RectF(0,0,bitmap.getWidth(),bitmap.getHeight());
            orientation = new ImageTransform(bitmap.getWidth(), bitmap.getHeight());
            markup = new Markup(bitmap);
            loupe = new EyedropperLoupe(look, getResources().getDisplayMetrics().density);
            setContentDescription("Область обрезки снимка");
            setFocusable(true);
        }

        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            RectF fraction = new RectF(0, 0, 1, 1);
            if (!zoomCrop.isEmpty()) {
                fraction.set(zoomCrop.left/orientation.width,zoomCrop.top/orientation.height,
                        zoomCrop.right/orientation.width,zoomCrop.bottom/orientation.height);
            } else if (!image.isEmpty() && !selected.isEmpty()) {
                fraction.set((selected.left - image.left) / image.width(),
                    (selected.top - image.top) / image.height(),
                    (selected.right - image.left) / image.width(),
                    (selected.bottom - image.top) / image.height());
            }
            layoutFrame(w,h,fraction);
        }

        private void layoutFrame(int w,int h,RectF fraction) {
            int pad = dp(9);
            if (w <= 0 || h <= 0) return;
            float scale = Math.min((float) Math.max(1, w - 2 * pad) / orientation.width,
                                   (float) Math.max(1, h - 2 * pad) / orientation.height);
            zoom.reset();
            zoom.maxScale = Math.max(8f, 1f / scale);
            float width=fitWidth=scale*orientation.width,height=fitHeight=scale*orientation.height;
            image.set((w-width)/2,(h-height)/2,(w+width)/2,(h+height)/2);
            selected.set(image.left+fraction.left*width,image.top+fraction.top*height,
                    image.left+fraction.right*width,image.top+fraction.bottom*height);
        }

        private float ratio() {
            if (format == 0) return 0;
            if (format == 1) return orientation.width/(float)orientation.height;
            return CropFormats.W[format]/(float)CropFormats.H[format];
        }

        void setFormat(int index) {
            format=index;
            tiltBasis.setEmpty();
            // Return to unconstrained handles without losing an existing selection.
            if (index != 0) reset();
        }

        void reset() {
            resetGesture();
            zoomCrop.setEmpty();
            tiltBasis.setEmpty();
            float aspect=ratio();
            if (tilt() != 0 && !image.isEmpty()) {
                fitCentered(aspect, image.width(), image.height());
                invalidate();
                return;
            }
            if (aspect>0 && !image.isEmpty()) {
                CropAspect.fit(image.width(),image.height(),image.width()/2,image.height()/2,
                        image.width(),image.height(),aspect,aspectBounds);
                setAspectBounds();
            } else selected.set(image);
            invalidate();
        }

        float tilt() { return tiltGeometry.degrees; }

        void setTilt(float degrees) {
            if (Float.isNaN(degrees)) return;
            degrees=clamp(degrees,-45,45);
            if (degrees == tilt()) return;
            resetGesture();
            if (!image.isEmpty() && !selected.isEmpty() && tiltBasis.isEmpty()) {
                tiltBasis.set((selected.left-image.left)/image.width(),
                        (selected.top-image.top)/image.height(),
                        (selected.right-image.left)/image.width(),
                        (selected.bottom-image.top)/image.height());
            }
            tiltGeometry=new TiltGeometry(degrees);
            updateTransform();
            zoomCrop.setEmpty();
            if (!image.isEmpty()) {
                if (degrees == 0 && !tiltBasis.isEmpty()) {
                    selected.set(image.left+tiltBasis.left*image.width(),
                            image.top+tiltBasis.top*image.height(),
                            image.left+tiltBasis.right*image.width(),
                            image.top+tiltBasis.bottom*image.height());
                    tiltBasis.setEmpty();
                } else {
                    float w=tiltBasis.isEmpty()?image.width():tiltBasis.width()*image.width();
                    float h=tiltBasis.isEmpty()?image.height():tiltBasis.height()*image.height();
                    fitCentered(ratio()>0?ratio():w/h,w,h);
                }
            }
            invalidate();
        }

        private void fitCentered(float aspect,float maxW,float maxH) {
            tiltGeometry.fit(image.width(),image.height(),aspect,maxW,maxH,aspectBounds);
            setAspectBounds();
        }

        private void updateTransform() {
            transform.setValues(orientation.matrixValues());
            if (tilt() != 0) {
                transform.postRotate(tilt(),orientation.width*.5f,orientation.height*.5f);
            }
            if (!transform.invert(inverseTransform)) {
                throw new IllegalStateException("Image transform is not invertible");
            }
        }

        float[] textOrientation() {
            float[] axes={1,0,0,1};
            inverseTransform.mapVectors(axes);
            return new float[]{axes[0],axes[2],axes[1],axes[3]};
        }

        private void setAspectBounds() {
            selected.set(image.left+aspectBounds[0],image.top+aspectBounds[1],
                    image.left+aspectBounds[2],image.top+aspectBounds[3]);
        }

        void reorient(ImageTransform next,boolean rotate) {
            resetGesture();
            tiltBasis.setEmpty();
            RectF fraction = new RectF(0,0,1,1);
            if (!image.isEmpty() && !selected.isEmpty()) {
                float l = orientedX(selected.left), t = orientedY(selected.top);
                float r = orientedX(selected.right), b = orientedY(selected.bottom);
                float x1=orientation.sourceX(l,t),y1=orientation.sourceY(l,t);
                float x2=orientation.sourceX(r,b),y2=orientation.sourceY(r,b);
                float u1=next.x(x1,y1)/next.width,v1=next.y(x1,y1)/next.height;
                float u2=next.x(x2,y2)/next.width,v2=next.y(x2,y2)/next.height;
                fraction.set(clamp(Math.min(u1,u2),0,1),clamp(Math.min(v1,v2),0,1),
                        clamp(Math.max(u1,u2),0,1),clamp(Math.max(v1,v2),0,1));
            }
            zoomCrop.setEmpty();
            if (rotate) format=CropFormats.rotate(format);
            else if (tilt() != 0) tiltGeometry=new TiltGeometry(-tilt());
            orientation=next;
            updateTransform();
            layoutFrame(getWidth(),getHeight(),fraction);
            invalidate();
        }

        boolean hasEdits() {
            if (markup.hasMarks() || !orientation.isIdentity() || tilt() != 0) return true;
            if (image.isEmpty() || selected.isEmpty()) return false;
            int[] box = selectedPixels();
            return box[0] != 0 || box[1] != 0
                    || box[2] != bitmap.getWidth() || box[3] != bitmap.getHeight();
        }

        private int[] selectedPixels() {
            int[] box=new int[4];
            float l=zoomCrop.isEmpty()?orientedX(selected.left):zoomCrop.left;
            float t=zoomCrop.isEmpty()?orientedY(selected.top):zoomCrop.top;
            float r=zoomCrop.isEmpty()?orientedX(selected.right):zoomCrop.right;
            float b=zoomCrop.isEmpty()?orientedY(selected.bottom):zoomCrop.bottom;
            if (tilt() == 0) CropAspect.pixels(orientation.width,orientation.height,l,t,r,b,
                    CropFormats.W[format],CropFormats.H[format],box);
            else tiltGeometry.pixels(orientation.width,orientation.height,l,t,r,b,
                    CropFormats.W[format],CropFormats.H[format],box);
            return box;
        }

        Bitmap selection() {
            if (image.isEmpty() || selected.isEmpty()) {
                if (markup.hasMarks() || !orientation.isIdentity() || tilt() != 0)
                    throw new IllegalStateException("Cannot export transformed image without a crop");
                return bitmap;
            }
            int[] box = selectedPixels();
            int left=box[0],top=box[1],width=box[2]-left,height=box[3]-top;
            if (!markup.hasMarks() && orientation.isIdentity() && tilt() == 0) {
                if (left == 0 && top == 0 && width == bitmap.getWidth() && height == bitmap.getHeight())
                    return bitmap;
                return Bitmap.createBitmap(bitmap,left,top,width,height);
            }
            Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            try {
                Canvas output=new Canvas(result);
                output.translate(-left,-top);
                output.concat(transform);
                drawSource(output,tilt() == 0?null:exportTiltPaint);
                markup.draw(output,false);
                return result;
            } catch (RuntimeException | OutOfMemoryError error) {
                result.recycle();
                throw error;
            }
        }

        private void drawSource(Canvas canvas,Paint bitmapPaint) {
            // Explicit destination rect avoids automatic bitmap-density scaling.
            canvas.drawBitmap(bitmap,null,bitmapFrame,bitmapPaint);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (canvas.isHardwareAccelerated() && (bitmap.getWidth() > canvas.getMaximumBitmapWidth()
                    || bitmap.getHeight() > canvas.getMaximumBitmapHeight())) {
                // Only the screen-sized viewport becomes a texture. Software drawing can sample
                // the full image and its full-resolution markup with the current zoom and crop.
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                postInvalidateOnAnimation();
                return;
            }
            if (image.isEmpty() || selected.isEmpty()) return;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            int saved = canvas.save();
            canvas.translate(image.left, image.top);
            canvas.scale(image.width()/orientation.width,image.height()/orientation.height);
            canvas.concat(transform);
            drawSource(canvas,paint);
            canvas.restoreToCount(saved);
            saved=canvas.save();
            canvas.clipRect(selected);
            canvas.translate(image.left,image.top);
            canvas.scale(image.width()/orientation.width,image.height()/orientation.height);
            canvas.concat(transform);
            markup.draw(canvas,true);
            canvas.restoreToCount(saved);
            if (pointer != -1 && isSpecialBrush()) {
                int cursor = canvas.save();
                canvas.clipRect(selected);
                float radius = activeWidth() * image.width() / (2f * orientation.width);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(Color.WHITE);
                canvas.drawCircle(brushX, brushY, radius, paint);
                paint.setStrokeWidth(dp(1));
                paint.setColor(look.accent);
                canvas.drawCircle(brushX, brushY, radius, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.restoreToCount(cursor);
            }
            paint.setColor(look.dark ? 0x99000000 : 0x80121d2c);
            if (tilt() != 0) {
                int shade=canvas.save();
                canvas.clipOutRect(selected);
                canvas.drawColor(paint.getColor());
                canvas.restoreToCount(shade);
            } else {
                canvas.drawRect(image.left, image.top, image.right, selected.top, paint);
                canvas.drawRect(image.left, selected.bottom, image.right, image.bottom, paint);
                canvas.drawRect(image.left, selected.top, selected.left, selected.bottom, paint);
                canvas.drawRect(selected.right, selected.top, image.right, selected.bottom, paint);
            }

            final float l = selected.left, t = selected.top, r = selected.right, b = selected.bottom;
            final float cx = selected.centerX(), cy = selected.centerY();
            paint.setStrokeWidth(dp(1));
            paint.setColor(0x80ffffff);
            if (editorTool == Markup.CROP && (tiltGuide || pointer != -1 && moved && drag != MOVE))
                for (int i = 1; i <= 2; ++i) {
                float x = l + selected.width() * i / 3f, y = t + selected.height() * i / 3f;
                canvas.drawLine(x, t, x, b, paint);
                canvas.drawLine(l, y, r, y, paint);
            }
            paint.setColor(look.frame);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(selected, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(dp(4));
            float hx = Math.min(dp(19), selected.width() / 4f);
            float hy = Math.min(dp(19), selected.height() / 4f);
            canvas.drawLine(l, t, l + hx, t, paint);
            canvas.drawLine(l, t, l, t + hy, paint);
            canvas.drawLine(r, t, r - hx, t, paint);
            canvas.drawLine(r, t, r, t + hy, paint);
            canvas.drawLine(l, b, l + hx, b, paint);
            canvas.drawLine(l, b, l, b - hy, paint);
            canvas.drawLine(r, b, r - hx, b, paint);
            canvas.drawLine(r, b, r, b - hy, paint);
            canvas.drawLine(cx - hx / 2, t, cx + hx / 2, t, paint);
            canvas.drawLine(cx - hx / 2, b, cx + hx / 2, b, paint);
            canvas.drawLine(l, cy - hy / 2, l, cy + hy / 2, paint);
            canvas.drawLine(r, cy - hy / 2, r, cy + hy / 2, paint);
            if (hasDraft()) drawCaptionDraft(canvas);
            if (eyePicking && pointer != -1) loupe.draw(canvas,sampleX,sampleY,getWidth(),getHeight(),samplePixels);
        }

        private int hit(float x, float y) {
            final float zone = dp(24);
            if (x < selected.left - zone || x > selected.right + zone ||
                y < selected.top - zone || y > selected.bottom + zone) return 0;
            float dl = Math.abs(x - selected.left), dr = Math.abs(x - selected.right);
            float dt = Math.abs(y - selected.top), db = Math.abs(y - selected.bottom);
            int h = Math.min(dl, dr) <= zone ? (dl <= dr ? LEFT : RIGHT) : 0;
            int v = Math.min(dt, db) <= zone ? (dt <= db ? TOP : BOTTOM) : 0;
            return (h | v) != 0 ? (h | v) : selected.contains(x, y) ? MOVE : 0;
        }

        void resetGesture() {
            if (drag != 0 && pointer != -1) {
                selected.set(startRect);
                zoomCrop.set(startZoomCrop);
            }
            markup.cancel();
            captionDrag=0;
            pointer = -1;
            drag = 0;
        }

        private float orientedX(float x) {
            return clamp((x-image.left)/image.width(),0,1)*orientation.width;
        }
        private float orientedY(float y) {
            return clamp((y-image.top)/image.height(),0,1)*orientation.height;
        }
        private float sourceX(float x,float y) {
            if (tilt() != 0) {
                x=clamp(x,selected.left,selected.right);
                y=clamp(y,selected.top,selected.bottom);
            }
            return sourceAxis(orientedX(x),orientedY(y),true);
        }
        private float sourceY(float x,float y) {
            if (tilt() != 0) {
                x=clamp(x,selected.left,selected.right);
                y=clamp(y,selected.top,selected.bottom);
            }
            return sourceAxis(orientedX(x),orientedY(y),false);
        }
        private float sourceAxis(float x,float y,boolean horizontal) {
            float u=tiltGeometry.inverseX(x,y,orientation.width,orientation.height);
            float v=tiltGeometry.inverseY(x,y,orientation.width,orientation.height);
            if (horizontal) return clamp(orientation.sourceX(u,v),0,bitmap.getWidth());
            return clamp(orientation.sourceY(u,v),0,bitmap.getHeight());
        }

        private void extendInk(float x,float y) { markup.extend(sourceX(x,y),sourceY(x,y)); }

        private float captionViewScale() { return image.width()/orientation.width; }
        private float captionViewX(float x,float y) { return image.left+captionViewScale()*draftPose.x(x,y); }
        private float captionViewY(float x,float y) { return image.top+captionViewScale()*draftPose.y(x,y); }
        private float captionHalfW() { return draftBox.width/2; }
        private float captionHalfH() { return draftBox.height/2; }
        private float handleHalfW() {
            return draftStepNumber>0?MarkPlacement.handleHalfExtent(captionHalfW(),
                    dp(16)/captionViewScale()):captionHalfW();
        }
        private float handleHalfH() {
            return draftStepNumber>0?MarkPlacement.handleHalfExtent(captionHalfH(),
                    dp(16)/captionViewScale()):captionHalfH();
        }
        boolean symbolDraft() {
            return draftStepNumber>0 || shapeDraft!=null&&Markup.isOpenShape(editorTool);
        }

        boolean hasDraft() { return draft!=null || shapeDraft!=null || draftStepNumber>0; }

        private void placeStep(float x,float y) {
            if (opacity==0) return;
            float radius=MarkPlacement.radius(orientation.width,orientation.height,initialMarkWidth);
            // Positive sentinel also identifies the two non-numbered stamp drafts.
            draftStepNumber=editorTool==Markup.STEP?markup.countStamps(StepStamp.class)+1:1;
            draftMarkTool=editorTool;
            draftStepColor=InkOpacity.apply(inkColor,opacity);
            draftMarkInitialRadius=radius;
            draftMarkStroke=Math.max(1,strokeWidth);
            previewStepRadius=-1;
            previewStep=null;
            draftPose=MarkPlacement.place(orientedX(x),orientedY(y));
            float aspect=editorTool==Markup.CHECK?MarkSymbolStamp.CHECK_ASPECT:1;
            draftBox=new CaptionFrame(draftPose.cx,draftPose.cy,radius*2,radius*2/aspect);
            draftBox.heightFloor=draftBox.height;
            draftMinWidth=draftMinHeight=1;
            draftIsCallout=false;
            captionMode(true);
        }

        private Markup.Stamp createMarkStamp(float radius,float x,float y,float[] axes) {
            if (draftMarkTool==Markup.STEP) return new StepStamp(draftStepNumber,draftStepColor,stepWeight,radius,x,y,axes);
            return new MarkSymbolStamp(draftMarkTool,draftStepColor,radius,
                    draftMarkStroke*radius/draftMarkInitialRadius,x,y,axes,initialMarkWidth>0);
        }

        private float[] affine(Matrix m) {
            m.getValues(shapeMatrixValues);
            return shapeMatrixValues;
        }

        private void beginShapeDraft(Markup.ShapeDraft candidate) {
            float[] bounds=candidate.bounds(affine(transform));
            shapeBaseW=Math.max(1,bounds[2]-bounds[0]);
            shapeBaseH=Math.max(1,bounds[3]-bounds[1]);
            shapeBaseX=(bounds[0]+bounds[2])/2;
            shapeBaseY=(bounds[1]+bounds[3])/2;
            draftMinWidth=draftMinHeight=Math.min(dp(30)/captionViewScale(),Math.min(shapeBaseW,shapeBaseH));
            draftPose=new CaptionTransform(shapeBaseX,shapeBaseY);
            draftBox=new CaptionFrame(shapeBaseX,shapeBaseY,shapeBaseW,shapeBaseH);
            draftBox.heightFloor=shapeBaseH;
            draftIsCallout=false;
            shapeDraft=candidate;
            captionMode(true);
        }

        private Matrix shapeEdit() {
            float sx=draftBox.width/shapeBaseW, sy=draftBox.height/shapeBaseH;
            if (symbolDraft()||editorTool==Markup.CIRCLE||editorTool==Markup.SQUARE||editorTool==Markup.DIAMOND) {
                sx=sy=Math.min(sx,sy);
            }
            shapeEditMatrix.setScale(sx,sy);
            shapeEditMatrix.postRotate(draftPose.angle);
            shapeEditMatrix.postTranslate(draftPose.cx,draftPose.cy);
            shapeEditMatrix.preTranslate(-shapeBaseX,-shapeBaseY);
            shapeEditMatrix.preConcat(transform);
            shapeEditMatrix.postConcat(inverseTransform);
            return shapeEditMatrix;
        }

        private Markup.ShapeDraft styledShapeDraft() {
            return shapeDraft.transformed(affine(shapeEdit()),InkOpacity.apply(inkColor,opacity),
                    strokeWidth,shapeFills[editorTool]);
        }

        private CalloutStamp callout() {
            float tipLocalX=draftPose.boxLocalX(draftTipX,draftTipY,captionHalfW());
            float tipLocalY=draftPose.boxLocalY(draftTipX,draftTipY,captionHalfH());
            if (calloutPreview==null) calloutPreview=new CalloutStamp(draft,draftCalloutColor,draftCalloutStroke,
                    draftBox.width,draftBox.height,draftPad,tipLocalX,tipLocalY,draftFontScale);
            else calloutPreview.configure(draftCalloutColor,draftCalloutStroke,
                    draftBox.width,draftBox.height,draftPad,tipLocalX,tipLocalY,draftFontScale);
            return calloutPreview;
        }

        private float tipViewX() { return image.left+captionViewScale()*draftTipX; }
        private float tipViewY() { return image.top+captionViewScale()*draftTipY; }

        private void rebuildCaption() {
            if (draft==null||draftBox==null) return;
            draftFontPixels=captionWidth;
            draftFontScale=1f;
            draftCalloutStroke=Math.max(2,captionWidth*.055f);
            draft=new CaptionStamp(draftValue,draftCalloutColor,captionWidth,
                    Math.max(1,(int)(draftBox.width-2*draftPad)),captionWrap,draftTextStyle);
            calloutPreview=null;
            float nextW=Math.max(draftBox.width,captionWrap?Math.max(draftCaptionMinWidth,captionWidth*1.25f+2*draftPad):draft.naturalWidth+2*draftPad);
            if (captionWrap) draft.reflow(Math.max(1,(int)(nextW-2*draftPad)));
            float nextH=draftBox.heightFor(draft.height()+2*draftPad);
            draftBox.resize(draftPose.cx,draftPose.cy,draftBox.width,draftBox.height,
                    nextW,nextH,1,1,draftPose.angle);
            draftPose.cx=draftBox.cx; draftPose.cy=draftBox.cy;
            layoutInlineCaption();
            invalidate();
        }

        private void draftGrip() {
            float w=handleHalfW(),h=handleHalfH(),scale=captionViewScale(),gap=dp(29)/scale;
            float inset=dp(15), right=Math.max(inset,getWidth()-inset), bottom=Math.max(inset,getHeight()-inset);
            float best=Float.POSITIVE_INFINITY;
            float tipX=draftIsCallout?tipViewX():Float.NEGATIVE_INFINITY;
            float tipY=draftIsCallout?tipViewY():Float.NEGATIVE_INFINITY;
            for (int i=0;i<4;i++) {
                int sx=i%2==0?1:-1, sy=i<2?1:-1;
                float targetX=captionViewX(sx*(w+gap*.70f),sy*(h+gap*.70f));
                float targetY=captionViewY(sx*(w+gap*.70f),sy*(h+gap*.70f));
                float gx=clamp(targetX,inset,right),gy=clamp(targetY,inset,bottom);
                float ax=captionViewX(sx*w,sy*h),ay=captionViewY(sx*w,sy*h);
                float score=Math.abs(targetX-gx)+Math.abs(targetY-gy);
                if (Math.hypot(gx-ax,gy-ay)<dp(17)) score+=dp(120);
                if (draftIsCallout&&Math.hypot(gx-tipX,gy-tipY)<dp(29)) score+=dp(120);
                if (score<best) {
                    best=score;
                    gripX=gx; gripY=gy;
                    gripAnchorX=ax; gripAnchorY=ay;
                }
            }
        }

        private void drawCaptionDraft(Canvas canvas) {
            float scale=captionViewScale();
            int saved=canvas.save();
            canvas.clipRect(selected);
            if (shapeDraft!=null) {
                canvas.translate(image.left,image.top);
                canvas.scale(scale,scale);
                canvas.concat(transform);
                styledShapeDraft().draw(canvas);
            } else {
                canvas.translate(image.left+scale*draftPose.cx,image.top+scale*draftPose.cy);
                canvas.rotate(draftPose.angle);
                canvas.scale(scale,scale);
                if (draftStepNumber>0) {
                    if (previewStep==null || previewStepRadius!=draftBox.width/2) {
                        previewStepRadius=draftBox.width/2;
                        previewStep=createMarkStamp(previewStepRadius,0,0,identityAxes);
                    }
                    previewStep.draw(canvas);
                } else {
                    canvas.translate(-captionHalfW(),-captionHalfH());
                    if (draftIsCallout) callout().drawLocal(canvas,inlineCaption==null);
                    else if (inlineCaption==null) {
                        canvas.translate(draftPad,draftPad);
                        canvas.scale(draftFontScale,draftFontScale);
                        draft.drawLocal(canvas);
                    }
                }
            }
            canvas.restoreToCount(saved);
            float w=handleHalfW(),h=handleHalfH();
            captionOutline.reset();
            captionOutline.moveTo(captionViewX(-w,-h),captionViewY(-w,-h));
            captionOutline.lineTo(captionViewX(w,-h),captionViewY(w,-h));
            captionOutline.lineTo(captionViewX(w,h),captionViewY(w,h));
            captionOutline.lineTo(captionViewX(-w,h),captionViewY(-w,h));
            captionOutline.close();
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xff111b2a);
            paint.setStrokeWidth(dp(3));
            canvas.drawPath(captionOutline,paint);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(dp(1));
            canvas.drawPath(captionOutline,paint);
            paint.setStyle(Paint.Style.FILL);
            for (int j=-1;j<=1;j++) for (int i=-1;i<=1;i++) {
                if (i==0 && j==0 || symbolDraft()&&(i==0||j==0)) continue;
                float x=captionViewX(i*w,j*h),y=captionViewY(i*w,j*h);
                int radius=dp(i==0 || j==0?3:5);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x,y,radius+dp(1),paint);
                paint.setColor(look.accent);
                canvas.drawCircle(x,y,radius,paint);
            }
            if (draft==null) {
            draftGrip();
            paint.setColor(0xff202737);
            paint.setStrokeWidth(dp(3));
            canvas.drawLine(gripAnchorX,gripAnchorY,gripX,gripY,paint);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(gripAnchorX,gripAnchorY,gripX,gripY,paint);
            paint.setColor(look.accent);
            canvas.drawCircle(gripX,gripY,dp(12),paint);
            paint.setColor(look.onAccent);
            paint.setTextSize(dp(16));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("↻",gripX,gripY-(paint.ascent()+paint.descent())/2,paint);
            paint.setTextAlign(Paint.Align.LEFT);
            }
            if (draftIsCallout) {
                float x=tipViewX(),y=tipViewY();
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x,y,dp(7),paint);
                paint.setColor(look.accent);
                canvas.drawCircle(x,y,dp(5),paint);
            }
        }

        private void applyCaption() {
            if (shapeDraft!=null) {
                if (opacity>0) markup.addStamp(styledShapeDraft());
                return;
            }
            float[] basis=textOrientation(), axes=new float[4];
            draftPose.axes(basis[0],basis[1],basis[2],basis[3],axes);
            if (draftStepNumber>0) {
                if ((draftStepColor>>>24)==0) return;
                float[] center={draftPose.cx,draftPose.cy};
                inverseTransform.mapPoints(center);
                markup.addStamp(createMarkStamp(draftBox.width/2,center[0],center[1],axes));
                return;
            }
            float pad=draftIsCallout?0:draftPad;
            float[] start={draftPose.x(-captionHalfW()+pad,-captionHalfH()+pad),
                           draftPose.y(-captionHalfW()+pad,-captionHalfH()+pad)};
            inverseTransform.mapPoints(start);
            if (draftIsCallout) {
                CalloutStamp stamp=callout();
                stamp.place(start[0],start[1],axes);
                markup.addStamp(stamp);
            } else {
                for (int i=0;i<axes.length;i++) axes[i]*=draftFontScale;
                draft.place(start[0],start[1],axes);
                markup.addStamp(draft);
            }
        }

        private void updateDraftBox(float nextW,float nextFloor,int sideX,int sideY,float angle) {
            float maximum=Math.max(orientation.width,orientation.height)*2f;
            draftBox.heightFloor=clamp(nextFloor,draftMinHeight,maximum);
            if (shapeDraft==null) {
                draftMinWidth=Math.max(draftCaptionMinWidth,2*draftPad+draftFontScale*
                        (draft.wrap?1.6f*draftFontPixels:draft.naturalWidth));
            }
            nextW=clamp(nextW,draftMinWidth,Math.max(maximum,draftMinWidth));
            float nextH=draftBox.heightFloor;
            if (shapeDraft==null) {
                draft.reflow((int)((nextW-2*draftPad)/draftFontScale));
                nextH=draftBox.heightFor(draft.height()*draftFontScale+2*draftPad);
            } else if (editorTool==Markup.CIRCLE||editorTool==Markup.SQUARE||editorTool==Markup.DIAMOND) {
                float factor=sideX==0?nextH/shapeBaseH:nextW/shapeBaseW;
                nextW=shapeBaseW*factor;
                nextH=shapeBaseH*factor;
            }
            draftBox.resize(captionStartX,captionStartY,captionStartW,captionStartH,nextW,nextH,
                    sideX,sideY,angle);
            draftPose.cx=draftBox.cx;
            draftPose.cy=draftBox.cy;
        }

        private void captionDraftTouch(MotionEvent event) {
            int action=event.getActionMasked();
            float scale=captionViewScale();
            if (action==MotionEvent.ACTION_DOWN && scale>0) {
                captionDrag=0;
                startX=event.getX(); startY=event.getY();
                float w=handleHalfW(),h=handleHalfH();
                float x=(startX-image.left)/scale,y=(startY-image.top)/scale;
                if (inlineCaption!=null) closeInlineCaption();
                if (draft==null) draftGrip();
                double gripDist=draft==null?Math.hypot(startX-gripX,startY-gripY):Double.POSITIVE_INFINITY;
                double tipDist=draftIsCallout?Math.hypot(startX-tipViewX(),
                        startY-tipViewY()):Double.POSITIVE_INFINITY;
                if (Math.min(gripDist,tipDist)<=dp(19)) captionDrag=gripDist<=tipDist?DRAFT_GRIP:DRAFT_ARROW;
                else captionDrag=draftPose.hit(x,y,w,h,Math.min(dp(13)/scale,Math.min(w,h)*.65f));
                if (symbolDraft()&&(captionDrag==CaptionTransform.X_EDGE||captionDrag==CaptionTransform.Y_EDGE))
                    captionDrag=CaptionTransform.INSIDE;
                if (captionDrag==CaptionTransform.NONE) return;
                pointer=event.getPointerId(0);
                captionStartX=draftPose.cx; captionStartY=draftPose.cy;
                captionStartW=draftBox.width; captionStartH=draftBox.height; captionStartFloor=draftBox.heightFloor;
                captionStartPose.angle=captionStartAngle=draftPose.angle;
                captionVectorX=x-captionStartX;
                captionVectorY=y-captionStartY;
                moved=false;
                captionTipStartX=draftTipX; captionTipStartY=draftTipY;
                captionSideX=captionStartPose.localX(captionVectorX,captionVectorY)<0?-1:1;
                captionSideY=captionStartPose.localY(captionVectorX,captionVectorY)<0?-1:1;
                return;
            }
            int index=event.findPointerIndex(pointer);
            if (captionDrag==CaptionTransform.NONE || index<0 || action==MotionEvent.ACTION_CANCEL || scale<=0) {
                lastCaptionTap=0;
                captionDrag=0;
                pointer=-1;
                return;
            }
            if (action!=MotionEvent.ACTION_MOVE && action!=MotionEvent.ACTION_UP) return;
            float dx=(event.getX(index)-startX)/scale,dy=(event.getY(index)-startY)/scale;
            if (draft!=null&&!moved&&Math.hypot(dx,dy)*scale<=dp(6)) {
                if (action==MotionEvent.ACTION_UP) {
                    if (captionDrag==CaptionTransform.INSIDE) {
                        long now=event.getEventTime();
                        boolean doubleTap=lastCaptionTap!=0&&now-lastCaptionTap<=android.view.ViewConfiguration.getDoubleTapTimeout()
                                &&Math.hypot(startX-lastCaptionTapX,startY-lastCaptionTapY)<=dp(30);
                        if (doubleTap) { lastCaptionTap=0; editCaptionInline(); }
                        else { lastCaptionTap=now; lastCaptionTapX=startX; lastCaptionTapY=startY; }
                    } else lastCaptionTap=0;
                    captionDrag=0;
                    pointer=-1;
                }
                return;
            }
            lastCaptionTap=0;
            if (captionDrag==CaptionTransform.INSIDE) {
                draftPose.move(captionStartX+dx,captionStartY+dy,
                        orientedX(selected.left),orientedY(selected.top),orientedX(selected.right),orientedY(selected.bottom));
            }
            else if (action==MotionEvent.ACTION_UP && dx==0 && dy==0 && !moved) {
                // No change from tapping a handle.
            } else if (captionDrag==DRAFT_ARROW) {
                draftTipX=clamp(captionTipStartX+dx,orientedX(selected.left),orientedX(selected.right));
                draftTipY=clamp(captionTipStartY+dy,orientedY(selected.top),orientedY(selected.bottom));
            } else if (captionDrag==DRAFT_GRIP || draft!=null&&captionDrag==CaptionTransform.CORNER) {
                draftPose.angle=CaptionTransform.rotated(captionStartAngle,captionVectorX,captionVectorY,dx,dy);
            } else if (symbolDraft()&&captionDrag==CaptionTransform.CORNER) {
                // Project onto the original center-to-corner axis. Crossing the center only clamps;
                // it cannot mirror the stamp or squash one dimension.
                float norm=captionVectorX*captionVectorX+captionVectorY*captionVectorY;
                float scaleFactor=norm>.01f?1+(captionVectorX*dx+captionVectorY*dy)/norm:1;
                if (draftStepNumber>0) {
                    float actual=captionStartW/2;
                    scaleFactor=MarkPlacement.cornerScale(scaleFactor,actual,
                            MarkPlacement.handleHalfExtent(actual,dp(16)/scale));
                }
                float factor=CaptionTransform.symbolBoxScale(scaleFactor,captionStartW,captionStartH,
                        draftMinWidth,draftMinHeight,Math.max(orientation.width,orientation.height)*2f);
                draftBox.width=captionStartW*factor;
                draftBox.height=captionStartH*factor;
                draftBox.heightFloor=draftBox.height;
            } else {
                int sideX=captionDrag==CaptionTransform.Y_EDGE?0:captionSideX;
                int sideY=captionDrag==CaptionTransform.X_EDGE?0:captionSideY;
                float width=captionStartW+sideX*captionStartPose.localX(dx,dy);
                float floor=sideY==0?captionStartFloor:captionStartH+sideY*captionStartPose.localY(dx,dy);
                updateDraftBox(width,floor,sideX,sideY,captionStartAngle);
            }
            if (dx!=0 || dy!=0) moved=true;
            if (action==MotionEvent.ACTION_UP) { captionDrag=0; pointer=-1; }
            invalidate();
        }

        private void captionTouch(MotionEvent event) {
            int action=event.getActionMasked();
            if (action==MotionEvent.ACTION_DOWN) {
                if (selected.contains(event.getX(),event.getY())) {
                    pointer=event.getPointerId(0);
                    startX=event.getX();
                    startY=event.getY();
                    moved=false;
                }
                return;
            }
            if (pointer==-1) return;
            int index=event.findPointerIndex(pointer);
            if (action==MotionEvent.ACTION_CANCEL||index<0) {
                pointer=-1;
                return;
            }
            float x=event.getX(index),y=event.getY(index);
            float slop=dp(8);
            if (Math.abs(x-startX)>slop||Math.abs(y-startY)>slop) moved=true;
            if (action==MotionEvent.ACTION_UP) {
                pointer=-1;
                if (!moved&&selected.contains(x,y)) {
                    performClick();
                    if (isMarkTool(editorTool)) placeStep(startX,startY);
                    else openCaption(startX,startY);
                }
            }
        }

        private boolean drawTouch(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (!selected.contains(event.getX(), event.getY())) return false;
                boolean textOnly=editorTool==Markup.MOSAIC && textMosaic;
                // Never silently apply regular mosaic while recognition is pending or found nothing.
                if (textOnly && (textWords==null || textWords.length==0)) return false;
                pointer = event.getPointerId(0);
                brushX = event.getX();
                brushY = event.getY();
                markup.begin(editorTool, sourceX(brushX,brushY), sourceY(brushX,brushY),
                        isSpecialBrush() ? Color.BLACK : InkOpacity.apply(inkColor, opacity), activeWidth(), pixelSize, orientation, shapeFills[editorTool], shapeGestures[editorTool],
                        textOnly?textWords:null,shapeFree[editorTool],tilt());
                invalidate();
                return true;
            }
            if (pointer == -1) return false;
            int at = event.findPointerIndex(pointer);
            if (action == MotionEvent.ACTION_CANCEL || at < 0) {
                resetGesture();
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                brushX = event.getX(at);
                brushY = event.getY(at);
                for (int i = 0; i < event.getHistorySize(); i++)
                    extendInk(event.getHistoricalX(at, i),event.getHistoricalY(at, i));
                extendInk(event.getX(at),event.getY(at));
            } else if (action == MotionEvent.ACTION_UP
                    || (action == MotionEvent.ACTION_POINTER_UP && event.getActionIndex() == at)) {
                extendInk(event.getX(at),event.getY(at));
                if (refineShape&&Markup.isShape(editorTool)) {
                    Markup.ShapeDraft candidate=markup.takeShapeDraft();
                    if (candidate!=null) beginShapeDraft(candidate);
                } else markup.finish();
                pointer = -1;
                redrawTools();
                performClick();
            }
            invalidate();
            return true;
        }

        private void previewSample(float x,float y) {
            sampleX=clamp(x,Math.max(0,selected.left),Math.min(getWidth(),selected.right));
            sampleY=clamp(y,Math.max(0,selected.top),Math.min(getHeight(),selected.bottom));
            int u=Math.min(orientation.width-1,(int)orientedX(sampleX));
            int v=Math.min(orientation.height-1,(int)orientedY(sampleY));
            if (tilt() == 0) markup.colorGrid(orientation,u,v,EyedropperLoupe.GRID,samplePixels);
            else {
                int grid=EyedropperLoupe.GRID, half=grid/2;
                for (int row=0;row<grid;row++) for (int col=0;col<grid;col++) {
                    float px=u+col-half+.5f,py=v+row-half+.5f;
                    int sx=Math.min(bitmap.getWidth()-1,(int)sourceAxis(px,py,true));
                    int sy=Math.min(bitmap.getHeight()-1,(int)sourceAxis(px,py,false));
                    samplePixels[row*grid+col]=markup.colorAt(sx,sy);
                }
            }
            invalidate();
        }

        private void sampleTouch(MotionEvent event) {
            int action=event.getActionMasked();
            if (action==MotionEvent.ACTION_DOWN) {
                if (!selected.contains(event.getX(),event.getY())) return;
                pointer=event.getPointerId(0);
                previewSample(event.getX(),event.getY());
            } else if (pointer!=-1) {
                int index=event.findPointerIndex(pointer);
                if (action==MotionEvent.ACTION_CANCEL || index<0) {
                    pointer=-1;
                    invalidate();
                } else if (action==MotionEvent.ACTION_MOVE || action==MotionEvent.ACTION_UP) {
                    previewSample(event.getX(index),event.getY(index));
                    if (action==MotionEvent.ACTION_UP) {
                        pointer=-1;
                        setInkColor(samplePixels[samplePixels.length/2]);
                        performClick();
                    }
                }
            }
        }

        private void transformTouch(MotionEvent event) {
            final int action=event.getActionMasked();
            if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) {
                pinchSpan=0;
                return;
            }
            int skip=action==MotionEvent.ACTION_POINTER_UP?event.getActionIndex():-1;
            int a=-1,b=-1;
            for (int i=0;i<event.getPointerCount();i++) {
                if (i==skip) continue;
                if (a<0) a=i;
                else { b=i; break; }
            }
            if (b<0 || image.isEmpty()) {
                pinchSpan=0;
                return;
            }
            final float ax=event.getX(a), ay=event.getY(a), bx=event.getX(b), by=event.getY(b);
            final float span=(float)Math.hypot(ax-bx,ay-by);
            final float x=(ax+bx)/2, y=(ay+by)/2;
            if (action==MotionEvent.ACTION_MOVE && pinchSpan>=1f && span>=1f) {
                if (zoomCrop.isEmpty()) zoomCrop.set(orientedX(selected.left),orientedY(selected.top),
                        orientedX(selected.right),orientedY(selected.bottom));
                zoom.update(fitWidth,fitHeight,image.left,image.top,getWidth(),getHeight(),
                        pinchX,pinchY,x,y,span/pinchSpan);
                float w=fitWidth*zoom.scale, h=fitHeight*zoom.scale;
                image.set(zoom.left,zoom.top,zoom.left+w,zoom.top+h);
                selected.set(image.left+w*(zoomCrop.left/orientation.width),
                        image.top+h*(zoomCrop.top/orientation.height),
                        image.left+w*(zoomCrop.right/orientation.width),
                        image.top+h*(zoomCrop.bottom/orientation.height));
                invalidate();
            }
            pinchSpan=span;
            pinchX=x;
            pinchY=y;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action=event.getActionMasked();
            boolean down=action==MotionEvent.ACTION_DOWN;
            boolean end=action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL;
            boolean wasPinching=pinch.isCaptured();
            if (pinch.capture(down,end,event.getPointerCount())) {
                if (!wasPinching) {
                    resetGesture();
                    pinchSpan=0;
                    invalidate();
                }
                transformTouch(event);
                return true;
            }
            // Consume DOWN even outside the crop so a pinch can start there.
            if (eyePicking) { sampleTouch(event); return true; }
            if (hasDraft()) { captionDraftTouch(event); return true; }
            if (editorTool == Markup.TEXT||editorTool==Markup.CALLOUT||isMarkTool(editorTool)) { captionTouch(event); return true; }
            if (editorTool != Markup.CROP) { drawTouch(event); return true; }
            if (action == MotionEvent.ACTION_DOWN) {
                if (selected.isEmpty()) return true;
                startX = event.getX();
                startY = event.getY();
                drag = hit(startX, startY);
                if (drag == 0) return true;
                startRect.set(selected);
                startZoomCrop.set(zoomCrop);
                pointer = event.getPointerId(0);
                moved = false;
                return true;
            }
            if (pointer == -1) return false;
            if (action == MotionEvent.ACTION_CANCEL) {
                resetGesture();
                invalidate();
                return true;
            }
            int at = event.findPointerIndex(pointer);
            if (at < 0) {
                pointer = -1;
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = event.getX(at) - startX, dy = event.getY(at) - startY;
                if (!moved && Math.abs(dx) < dp(2) && Math.abs(dy) < dp(2)) return true;
                moved = true;
                tiltBasis.setEmpty();
                zoomCrop.setEmpty();
                selected.set(startRect);
                float minW = Math.min(dp(44), startRect.width());
                float minH = Math.min(dp(44), startRect.height());
                if (drag == MOVE) {
                    selected.offset(clamp(dx, image.left - startRect.left, image.right - startRect.right),
                                    clamp(dy, image.top - startRect.top, image.bottom - startRect.bottom));
                } else if (format != 0) {
                    CropAspect.resize(image.width(),image.height(),startRect.left-image.left,startRect.top-image.top,
                            startRect.right-image.left,startRect.bottom-image.top,drag,dx,dy,
                            Math.min(minW,minH),ratio(),aspectBounds);
                    setAspectBounds();
                } else {
                    if ((drag & LEFT) != 0) selected.left = clamp(startRect.left + dx, image.left, startRect.right - minW);
                    if ((drag & RIGHT) != 0) selected.right = clamp(startRect.right + dx, startRect.left + minW, image.right);
                    if ((drag & TOP) != 0) selected.top = clamp(startRect.top + dy, image.top, startRect.bottom - minH);
                    if ((drag & BOTTOM) != 0) selected.bottom = clamp(startRect.bottom + dy, startRect.top + minH, image.bottom);
                }
                if (tilt() != 0) {
                    aspectBounds[0]=selected.left-image.left; aspectBounds[1]=selected.top-image.top;
                    aspectBounds[2]=selected.right-image.left; aspectBounds[3]=selected.bottom-image.top;
                    tiltGeometry.constrain(image.width(),image.height(),startRect.left-image.left,
                            startRect.top-image.top,startRect.right-image.left,startRect.bottom-image.top,
                            aspectBounds);
                    setAspectBounds();
                }
                invalidate();
            } else if (action == MotionEvent.ACTION_UP ||
                     (action == MotionEvent.ACTION_POINTER_UP && event.getActionIndex() == at)) {
                pointer = -1;
                drag = 0;
                if (!moved) performClick();
                invalidate();
            }
            return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }
        private float clamp(float number, float min, float max) { return Math.max(min, Math.min(number, max)); }
    }
}
