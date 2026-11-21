package ru.dot.clipshot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Opens the settings for the service belonging to this installed build. */
final class ScrollAccess {
    private static final String DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS";
    private static final String HIGHLIGHT = ":settings:fragment_args_key";

    private ScrollAccess() {}

    static void explain(Activity activity) {
        String name = activity.getString(R.string.scroll_service_name);
        new AlertDialog.Builder(activity)
                .setTitle("Разрешить прокрутку")
                .setMessage("Для длинного снимка включи службу «" + name + "» в специальных возможностях Android. "
                        + "Она листает страницу по твоей команде. Для обычных снимков служба не нужна.\n\n"
                        + "Если службы нет в списке или она недоступна, может понадобиться пункт "
                        + "«Разрешить запрещённые настройки».")
                .setNeutralButton("Где найти?", (dialog, which) -> explainRestrictions(activity))
                .setNegativeButton("Не сейчас", null)
                .setPositiveButton("Открыть настройки", (dialog, which) -> open(activity))
                .show();
    }

    private static void explainRestrictions(Activity activity) {
        String path = "Настройки → Приложения → " + activity.getString(R.string.name);
        new AlertDialog.Builder(activity)
                .setTitle("Если служба недоступна")
                .setMessage("Открой «" + path + "» или нажми «О приложении» ниже.\n\n"
                        + "В разделе «Расширенные настройки» ищи «Разрешить запрещённые настройки». "
                        + "На других устройствах этот пункт находится в меню ⋮ и может называться «Разрешить доступ к настройкам».\n\n"
                        + "Давай доступ только приложению, которому доверяешь. Если Play Защита заблокировала APK, "
                        + "оставь ограничение включённым.\n\n"
                        + "После изменения вернись в «Специальные возможности» и включи службу «"
                        + activity.getString(R.string.scroll_service_name) + "».")
                .setNegativeButton("Назад", (dialog, which) -> explain(activity))
                .setPositiveButton("О приложении", (dialog, which) -> {
                    Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", activity.getPackageName(), null));
                    try {
                        activity.startActivity(info);
                    } catch (ActivityNotFoundException | SecurityException unavailable) {
                        Toast.makeText(activity, path, Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    static void open(Activity activity) {
        String component = new ComponentName(activity.getPackageName(),
                ScrollGestureService.class.getName()).flattenToString();
        Intent details = new Intent(DETAILS).putExtra(Intent.EXTRA_COMPONENT_NAME, component);
        // Stock Android reserves this screen for system callers; some vendors expose it.
        ResolveInfo handler = activity.getPackageManager().resolveActivity(details,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_SYSTEM_ONLY);
        if (handler != null && handler.activityInfo != null) {
            ActivityInfo info = handler.activityInfo;
            if (info.exported && (info.permission == null
                    || activity.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)) {
                try {
                    activity.startActivity(details.setClassName(info.packageName, info.name));
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // The system can change the preferred handler between resolving and opening it.
                }
            }
        }

        // AOSP Settings can scroll to this service's preference. Other vendors can ignore the hint.
        Bundle args = new Bundle();
        args.putString(HIGHLIGHT, component);
        Intent list = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:show_fragment_args", args)
                .putExtra(HIGHLIGHT, component);
        try {
            activity.startActivity(list);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(activity, "Открой раздел «Специальные возможности» в настройках телефона.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
