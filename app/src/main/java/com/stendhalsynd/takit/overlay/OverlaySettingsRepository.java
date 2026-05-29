package com.stendhalsynd.takit.overlay;

import android.content.Context;
import android.content.SharedPreferences;

public final class OverlaySettingsRepository {
    private static final String PREFS = "takit_overlay";
    private static final String KEY_BUBBLE_ALPHA = "bubble_alpha";
    private static final String KEY_BUBBLE_ACTIVE = "bubble_active";

    private final SharedPreferences preferences;

    public OverlaySettingsRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public float getBubbleAlpha() {
        return OverlaySettings.clampAlpha(preferences.getFloat(KEY_BUBBLE_ALPHA, OverlaySettings.DEFAULT_ALPHA));
    }

    public void saveBubbleAlpha(float alpha) {
        preferences.edit().putFloat(KEY_BUBBLE_ALPHA, OverlaySettings.clampAlpha(alpha)).apply();
    }

    public boolean isBubbleActive() {
        return preferences.getBoolean(KEY_BUBBLE_ACTIVE, false);
    }

    public void saveBubbleActive(boolean active) {
        preferences.edit().putBoolean(KEY_BUBBLE_ACTIVE, active).apply();
    }
}
