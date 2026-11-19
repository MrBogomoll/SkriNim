package ru.dot.clipshot;

import android.content.Context;
import android.content.SharedPreferences;

final class IdleSettings {
    static final int[] PRESETS = {30, 60, 120, 300};
    static final String[] LABELS = {"30 секунд", "1 минута", "2 минуты", "5 минут", "Своё…"};
    private final SharedPreferences prefs;

    IdleSettings(Context context) {
        prefs = context.getSharedPreferences("capture_idle", Context.MODE_PRIVATE);
    }

    boolean enabled() { return prefs.getBoolean("enabled", false); }
    int seconds() { return prefs.getInt("seconds", 60); }
    boolean customSelected() { return prefs.getBoolean("custom_selected", false); }
    int customSeconds() { return prefs.getInt("custom_seconds", 60); }

    void setEnabled(boolean enabled) { prefs.edit().putBoolean("enabled", enabled).apply(); }

    void setDuration(int seconds, boolean custom) {
        SharedPreferences.Editor edit = prefs.edit().putInt("seconds", seconds)
                .putBoolean("custom_selected", custom);
        if (custom) edit.putInt("custom_seconds", seconds);
        edit.apply();
    }

    String display() {
        int total = seconds();
        int minutes = total / 60, seconds = total % 60;
        if (minutes == 0) return seconds + " сек";
        if (seconds == 0) return minutes + " мин";
        return minutes + " мин " + seconds + " сек";
    }

    static int parseDuration(String minutes, String seconds) {
        try {
            long m = minutes.trim().isEmpty() ? 0 : Long.parseLong(minutes.trim());
            long s = seconds.trim().isEmpty() ? 0 : Long.parseLong(seconds.trim());
            if (m < 0 || m > 1440 || s < 0 || s >= 60) return -1;
            long total = m * 60L + s;
            return total >= 1 && total <= 86400 ? (int) total : -1;
        } catch (NumberFormatException error) { return -1; }
    }
}
