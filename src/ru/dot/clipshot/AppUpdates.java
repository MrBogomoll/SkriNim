package ru.dot.clipshot;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Shared across settings screens. Network and archive reads run off the UI thread. */
final class AppUpdates {
    enum Action { NONE, DOWNLOAD, PAGE, INSTALL }
    private static final String LATEST = "https://api.github.com/repos/MrBogomoll/SkriNim/releases/latest";
    private final Context app;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable listener;
    private ReleaseInfo release;
    boolean busy;
    Action action = Action.NONE;
    String message = "Проверить, есть ли новая версия на GitHub.";

    AppUpdates(Context app) { this.app = app; }
    void observe(Runnable observer) { listener = observer; observer.run(); }
    void remove(Runnable observer) { if (listener == observer) listener = null; }
    private void changed() { if (listener != null) listener.run(); }
    String page() { return release.page; }

    private void begin(String status) {
        busy = true;
        action = Action.NONE;
        message = status;
        changed();
    }
    private void finish(String status, Action next) {
        busy = false;
        action = next;
        message = status;
        changed();
    }

    void check(String installed) {
        if (busy) return;
        begin("Проверяем версию…");
        worker.execute(() -> {
            HttpsURLConnection connection = null;
            try {
                connection = connect(LATEST);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                int code = connection.getResponseCode();
                if (code == 404) {
                    main.post(() -> finish("Опубликованных релизов пока нет.", Action.NONE));
                    return;
                }
                if (code == 403 || code == 429) {
                    main.post(() -> finish("GitHub временно ограничил запросы. Попробуй позже.", Action.NONE));
                    return;
                }
                if (code != 200) throw new IOException();
                try (InputStream input = connection.getInputStream()) {
                    ByteArrayOutputStream json = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (json.size() + count > 1024 * 1024) throw new IOException();
                        json.write(buffer, 0, count);
                    }
                    JSONObject releaseJson = new JSONObject(json.toString("UTF-8"));
                    if (!ReleaseInfo.isStableRelease(releaseJson)) {
                        main.post(() -> finish("GitHub не вернул стабильный релиз.", Action.NONE));
                        return;
                    }
                    ReleaseInfo latest = ReleaseInfo.parse(releaseJson);
                    int comparison = ReleaseInfo.compare(installed, latest.version);
                    main.post(() -> showRelease(latest, installed, comparison));
                }
            } catch (IllegalArgumentException invalidVersion) {
                main.post(() -> finish("Не удалось сравнить номера версий.", Action.NONE));
            } catch (Exception error) {
                main.post(() -> finish("Не удалось проверить обновления. Проверь соединение и попробуй снова.", Action.NONE));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void showRelease(ReleaseInfo latest, String installed, int comparison) {
        release = latest;
        if (comparison == 0) finish("Установлена актуальная версия " + installed + ".", Action.NONE);
        else if (comparison > 0) finish("Твоя версия " + installed + " новее опубликованной " + latest.version + ".", Action.NONE);
        else {
            String status = "Доступна версия " + latest.version + ". Установлена " + installed + ".";
            if (latest.download != null) finish(status, Action.DOWNLOAD);
            else finish(status + " Для прямой загрузки нет подходящего APK.", Action.PAGE);
        }
    }

    private static HttpsURLConnection connect(String url) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("User-Agent", "SkriNim-Android");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        return connection;
    }

    void download() {
        if (busy || action != Action.DOWNLOAD) return;
        ReleaseInfo target = release;
        begin("Скачиваем SkriNim " + target.version + "…");
        worker.execute(() -> {
            File partial = new File(app.getCacheDir(), "SkriNim.partial.apk");
            HttpsURLConnection connection = null;
            try {
                connection = connect(target.download);
                if (connection.getResponseCode() != 200) throw new IOException();
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                long total = 0;
                int lastPercent = -1;
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[32768];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > target.size) throw new IOException();
                        sha.update(buffer, 0, count);
                        output.write(buffer, 0, count);
                        int percent = (int) (100.0 * total / target.size);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            main.post(() -> {
                                message = "Скачиваем SkriNim " + target.version + " · " + percent + "%";
                                changed();
                            });
                        }
                    }
                }
                if (total != target.size || !MessageDigest.isEqual(sha.digest(), decodeHex(target.digest))) {
                    main.post(() -> finish("Файл повреждён. Попробуй скачать обновление ещё раз.", Action.DOWNLOAD));
                    return;
                }
                PackageInfo apk = app.getPackageManager().getPackageArchiveInfo(partial.getPath(), 0);
                PackageInfo current = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
                if (apk == null || !app.getPackageName().equals(apk.packageName)
                        || ReleaseInfo.compare(target.version, apk.versionName) != 0
                        || apk.getLongVersionCode() <= current.getLongVersionCode()) {
                    main.post(() -> finish("APK не подходит для обновления этой сборки.", Action.PAGE));
                    return;
                }
                if (!partial.renameTo(UpdateProvider.apk(app))) throw new IOException();
                main.post(() -> finish("SkriNim " + target.version + " скачан и готов к установке.", Action.INSTALL));
            } catch (Exception error) {
                main.post(() -> finish("Не удалось скачать APK. Проверь соединение и свободное место.", Action.DOWNLOAD));
            } finally {
                partial.delete();
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static byte[] decodeHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
}
