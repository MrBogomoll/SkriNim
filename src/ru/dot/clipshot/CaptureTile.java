package ru.dot.clipshot;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.TileService;
import android.service.quicksettings.Tile;
import android.widget.Toast;

public final class CaptureTile extends TileService {
    private static final String ADDED = "added";

    static SharedPreferences presence(Context context) {
        return context.getSharedPreferences("capture_tile", Context.MODE_PRIVATE);
    }

    static boolean isAdded(SharedPreferences state) {
        return state.getBoolean(ADDED, false);
    }

    static void rememberPresence(Context context, boolean added) {
        SharedPreferences state = presence(context);
        if (isAdded(state) != added) state.edit().putBoolean(ADDED, added).apply();
    }

    @Override public void onTileAdded() { rememberPresence(this, true); }

    @Override public void onTileRemoved() { rememberPresence(this, false); }

    @Override public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        // Also discover tiles that were already added before this setting existed.
        rememberPresence(this, true);
        ProjectionService service = ProjectionService.current;
        boolean active = service != null && service.ready();
        tile.setLabel(getString(R.string.tile_name));
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(active ? "Нажми, чтобы сделать снимок" : "Начать сеанс захвата");
        tile.updateTile();
    }
    @Override public void onClick() {
        if (isLocked()) {
            Toast.makeText(this, "Сначала разблокируй телефон", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, CaptureActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this, 11, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        else startActivityAndCollapse(intent);
    }
}
