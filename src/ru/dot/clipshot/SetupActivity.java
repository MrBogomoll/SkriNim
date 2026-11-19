package ru.dot.clipshot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class SetupActivity extends Activity {
    // Manual release checks open the project page in the browser.
    private static final String RELEASES_PAGE = "https://github.com/MrBogomoll/SkriNim/releases";
    private static final String ACTIVE_TAB = "setup.active_tab";
    private static final int CAPTURE_TAB = 0, SETTINGS_TAB = 1;
    private static final int OVERLAY_PERMISSION = 82;
    private static final String ASKING_OVERLAY = "setup.asking_overlay";
    private static final String[] TAB_NAMES = {"Снимок", "Настройки"};
    private TextView state;
    private Button session;
    private Look look;
    private IdleSettings idle;
    private Button interval;
    private CheckBox bubbleSwitch;
    private TextView bubbleStatus;
    private boolean renderingBubble, askingOverlay;
    private final Button[] tabs = new Button[TAB_NAMES.length];
    private final LinearLayout[] panels = new LinearLayout[TAB_NAMES.length];
    private ScrollView scroll;
    private int activeTab = CAPTURE_TAB;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        look = new Look(this);
        idle = new IdleSettings(this);
        askingOverlay = saved != null && saved.getBoolean(ASKING_OVERLAY);
        LinearLayout layout = column();
        layout.setBackgroundColor(look.canvas);
        LinearLayout header = column();
        header.setPadding(dp(18), dp(18), dp(18), 0);
        layout.addView(header);
        header.addView(label(getString(R.string.name), 30, look.text, true));
        TextView tagline = label("Снимки без сохранения в галерею", 15, look.muted, false);
        tagline.setPadding(0, dp(3), 0, 0);
        header.addView(tagline);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tabRowSize = new LinearLayout.LayoutParams(-1, -2);
        tabRowSize.topMargin = dp(12);
        header.addView(tabRow, tabRowSize);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            Button tab = look.button(this, TAB_NAMES[i], look.tint, look.text);
            tab.setOnClickListener(v -> selectTab(index));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) size.leftMargin = dp(8);
            tabRow.addView(tab, size);
            tabs[i] = tab;
        }

        scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout pages = column();
        pages.setPadding(dp(18), 0, dp(18), dp(14));
        scroll.addView(pages);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i = 0; i < panels.length; i++) {
            panels[i] = column();
            pages.addView(panels[i], new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout shots = panels[CAPTURE_TAB];
        LinearLayout settings = panels[SETTINGS_TAB];

        LinearLayout capture = card(shots);
        state = label("", 19, look.text, true);
        capture.addView(state);
        detail(capture, "Разреши захват всего экрана один раз, затем делай снимки через плитку.");
        session = look.button(this, "", look.accent, look.onAccent);
        addButton(capture, session);
        session.setOnClickListener(v -> {
            if (isRunning()) {
                stopService(new Intent(this, ProjectionService.class));
                setSession(false);
            } else startActivity(new Intent(this, CaptureActivity.class)
                    .putExtra(CaptureActivity.CONSENT_ONLY, true));
        });

        LinearLayout tile = card(shots);
        tile.addView(label("Плитка в шторке", 19, look.text, true));
        detail(tile, "Нажми плитку в нужном приложении, обрежь снимок и скопируй его.");
        Button add = look.button(this, "Добавить плитку", look.tint, look.text);
        addButton(tile, add);
        add.setOnClickListener(v -> addTile());

        LinearLayout bubble = card(shots);
        bubble.addView(label("Плавающая кнопка", 19, look.text, true));
        detail(bubble, "Нажми для снимка или перетащи кнопку. Работает только во время сеанса.");
        bubbleSwitch = new CheckBox(this);
        bubbleSwitch.setText("Показывать поверх приложений");
        bubbleSwitch.setTextColor(look.text);
        bubbleSwitch.setButtonTintList(ColorStateList.valueOf(look.accent));
        bubbleSwitch.setMinimumHeight(dp(48));
        bubble.addView(bubbleSwitch);
        bubbleStatus=label("",14,look.muted,false);
        bubble.addView(bubbleStatus);
        bubbleSwitch.setOnCheckedChangeListener((view,checked) -> onBubbleSwitch(checked));
        refreshBubble();

        LinearLayout appearance = card(settings);
        appearance.addView(label("Тема", 19, look.text, true));
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams opts = new LinearLayout.LayoutParams(-1, -2);
        opts.topMargin = dp(9);
        appearance.addView(options, opts);
        themeOption(options, "Светлая", false);
        themeOption(options, "Тёмная", true);

        LinearLayout timeout = card(settings);
        timeout.addView(label("Автоотключение", 19, look.text, true));
        detail(timeout, "Завершает захват после паузы. Текст уведомлений снова становится виден.");
        CheckBox enabled = new CheckBox(this);
        enabled.setText("Автоотключение захвата");
        enabled.setTextColor(look.text);
        enabled.setButtonTintList(ColorStateList.valueOf(look.accent));
        enabled.setMinimumHeight(dp(48));
        enabled.setChecked(idle.enabled());
        timeout.addView(enabled);
        interval = look.button(this, "", look.tint, look.text);
        interval.setOnClickListener(v -> pickInterval());
        addButton(timeout, interval);
        refreshInterval();
        enabled.setOnCheckedChangeListener((button, isChecked) -> {
            idle.setEnabled(isChecked);
            ProjectionService.refreshIdleTimer();
            refreshInterval();
        });

        LinearLayout about = card(settings);
        LinearLayout aboutRow = new LinearLayout(this);
        aboutRow.setGravity(Gravity.CENTER_VERTICAL);
        about.addView(aboutRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout versionColumn = column();
        aboutRow.addView(versionColumn, new LinearLayout.LayoutParams(0, -2, 1));
        versionColumn.addView(label("О приложении", 19, look.text, true));
        TextView version = label(installedVersion(), 16, look.text, false);
        version.setPadding(0, dp(6), 0, 0);
        versionColumn.addView(version);
        Button releases = look.button(this, "Обновить", look.tint,
                RELEASES_PAGE.isEmpty() ? look.muted : look.text);
        releases.setEnabled(!RELEASES_PAGE.isEmpty());
        releases.setOnClickListener(v -> openReleases());
        LinearLayout.LayoutParams releaseSize = new LinearLayout.LayoutParams(-2, -2);
        releaseSize.leftMargin = dp(8);
        aboutRow.addView(releases, releaseSize);
        if (RELEASES_PAGE.isEmpty()) {
            detail(about, "Проверка обновлений станет доступна после публикации релизов на GitHub.");
        } else {
            detail(about, "Последние версии открываются на GitHub в браузере.");
        }

        selectTab(saved == null ? CAPTURE_TAB : saved.getInt(ACTIVE_TAB, CAPTURE_TAB));
        setContentView(layout);
        look.bars(this, layout);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.length) index = CAPTURE_TAB;
        activeTab = index;
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == index;
            tabs[i].setSelected(selected);
            tabs[i].setTextColor(selected ? look.onAccent : look.text);
            tabs[i].setBackground(new RippleDrawable(
                    ColorStateList.valueOf(look.dark ? 0x34ffffff : 0x24001c40),
                    look.shape(this, selected ? look.accent : look.tint, 16, false),
                    look.shape(this, 0xffffffff, 16, false)));
            panels[i].setVisibility(selected ? View.VISIBLE : View.GONE);
        }
        scroll.scrollTo(0, 0);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(ACTIVE_TAB, activeTab);
        outState.putBoolean(ASKING_OVERLAY,askingOverlay);
        super.onSaveInstanceState(outState);
    }

    private void onBubbleSwitch(boolean checked) {
        if (renderingBubble) return;
        if (!checked || FloatingCapture.allowed(this)) {
            FloatingCapture.setEnabled(this,checked);
            ProjectionService.refreshFloatingButton();
            refreshBubble();
            return;
        }
        // Keep the app setting off until Settings grants access and the user returns.
        refreshBubble();
        if (askingOverlay) return;
        askingOverlay=true;
        try {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())),OVERLAY_PERMISSION);
        } catch (ActivityNotFoundException | SecurityException error) {
            askingOverlay=false;
            Toast.makeText(this,"Не удалось открыть разрешение поверх приложений.",Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=OVERLAY_PERMISSION || !askingOverlay) return;
        askingOverlay=false;
        if (FloatingCapture.allowed(this)) FloatingCapture.setEnabled(this,true);
        else Toast.makeText(this,"Разрешение не выдано. Кнопка выключена.",Toast.LENGTH_SHORT).show();
        ProjectionService.refreshFloatingButton();
        refreshBubble();
    }

    private void refreshBubble() {
        boolean enabled=FloatingCapture.enabled(this);
        boolean allowed=FloatingCapture.allowed(this);
        renderingBubble=true;
        bubbleSwitch.setChecked(enabled && allowed);
        renderingBubble=false;
        if (!enabled) bubbleStatus.setVisibility(View.GONE);
        else {
            bubbleStatus.setVisibility(View.VISIBLE);
            bubbleStatus.setText(!allowed?"Нужно разрешение «Поверх других приложений»."
                    :isRunning()?"Кнопка доступна на главном экране и поверх приложений."
                    :"Начни сеанс захвата, чтобы показать кнопку.");
        }
    }

    private String installedVersion() {
        PackageManager manager = getPackageManager();
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 33) {
                info = manager.getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                info = manager.getPackageInfo(getPackageName(), 0);
            }
            if (info.versionName != null && !info.versionName.isEmpty()) {
                return "Версия " + info.versionName;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Read the installed package instead of keeping a second version constant.
        }
        return "Версия неизвестна";
    }

    private void openReleases() {
        if (RELEASES_PAGE.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE))
                    .addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Не удалось открыть GitHub в браузере", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshInterval() {
        interval.setText("Через " + idle.display() + " без снимков");
        interval.setVisibility(idle.enabled() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, look.dark
                ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    private void pickInterval() {
        int selected = IdleSettings.PRESETS.length;
        if (!idle.customSelected()) {
            for (int i = 0; i < IdleSettings.PRESETS.length; i++)
                if (IdleSettings.PRESETS[i] == idle.seconds()) selected = i;
        }
        dialog().setTitle("Через сколько завершать захват")
            .setSingleChoiceItems(IdleSettings.LABELS, selected, (dialog, which) -> {
                dialog.dismiss();
                if (which == IdleSettings.PRESETS.length) customInterval();
                else saveInterval(IdleSettings.PRESETS[which], false);
            })
            .setNegativeButton("Отмена", null).show();
    }

    private void customInterval() {
        int previous = idle.customSeconds();
        AlertDialog.Builder builder = dialog();
        LinearLayout fields = new LinearLayout(builder.getContext());
        fields.setPadding(dp(20), dp(8), dp(20), 0);
        EditText minutes = durationField(builder, "Минуты", previous / 60);
        EditText seconds = durationField(builder, "Секунды", previous % 60);
        fields.addView(minutes, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1);
        right.leftMargin = dp(12);
        fields.addView(seconds, right);
        AlertDialog shown = builder.setTitle("Свой интервал")
                .setMessage("От 1 секунды до 24 часов. Секунды: от 0 до 59.")
                .setView(fields).setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null).show();
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int total = IdleSettings.parseDuration(minutes.getText().toString(), seconds.getText().toString());
            if (total < 0) seconds.setError("Проверь минуты и секунды");
            else {
                saveInterval(total, true);
                shown.dismiss();
            }
        });
    }

    private EditText durationField(AlertDialog.Builder builder, String hint, int value) {
        EditText field = new EditText(builder.getContext());
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setHint(hint);
        field.setContentDescription(hint);
        if (value > 0) field.setText(Integer.toString(value));
        return field;
    }

    private void saveInterval(int total, boolean custom) {
        idle.setDuration(total, custom);
        ProjectionService.refreshIdleTimer();
        refreshInterval();
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                getSystemService(StatusBarManager.class).requestAddTileService(
                    new ComponentName(this, CaptureTile.class), "Скрин в буфер", Icon.createWithResource(this,
                        getResources().getIdentifier("tile", "drawable", getPackageName())), getMainExecutor(), n -> {
                            if (n != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                && n != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) editHint();
                        });
            } catch (RuntimeException ex) { editHint(); }
        } else editHint();
    }

    private void themeOption(LinearLayout row, String title, boolean dark) {
        boolean selected = dark == look.dark;
        Button button = look.button(this, selected ? "✓ " + title : title,
                selected ? look.accent : look.tint, selected ? look.onAccent : look.text);
        button.setSelected(selected);
        button.setContentDescription(selected ? title + ", выбрана" : title);
        button.setOnClickListener(v -> {
            if (!selected) {
                Look.save(this, dark);
                recreate();
            }
        });
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1f);
        if (dark) size.leftMargin = dp(8);
        row.addView(button, size);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = column();
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackground(look.shape(this, look.panel, 22, true));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(12);
        parent.addView(card, size);
        return card;
    }

    private TextView label(String title, int size, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(size);
        label.setTextColor(color);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private void detail(LinearLayout card, String text) {
        TextView description = label(text, 15, look.muted, false);
        description.setPadding(0, dp(4), 0, 0);
        card.addView(description);
    }

    private void addButton(LinearLayout card, Button button) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(10);
        card.addView(button, size);
    }

    private int dp(float value) { return Look.dp(this, value); }

    private void editHint() {
        Toast.makeText(this, "Открой шторку → редактирование плиток → «Скрин в буфер»", Toast.LENGTH_LONG).show();
    }

    private boolean isRunning() {
        return ProjectionService.current != null && ProjectionService.current.ready();
    }

    private void setSession(boolean running) {
        state.setText(running ? "Сеанс активен" : "Сеанс не запущен");
        session.setText(running ? "Остановить сеанс" : "Начать сеанс");
        refreshBubble();
    }

    @Override public void onResume() {
        super.onResume();
        setSession(isRunning());
        state.postDelayed(refresh, 450);
    }

    private final Runnable refresh = () -> setSession(isRunning());

    @Override public void onPause() {
        state.removeCallbacks(refresh);
        super.onPause();
    }
}
