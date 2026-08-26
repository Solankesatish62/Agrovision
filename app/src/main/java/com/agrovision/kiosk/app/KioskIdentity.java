package com.agrovision.kiosk.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.Random;
import java.util.UUID;

public final class KioskIdentity {
    private static final String PREFS_NAME = "kiosk_settings";
    private static final String KEY_KIOSK_ID = "kiosk_id";
    private static final String KEY_PAIRING_CODE = "pairing_code";

    public static String getKioskId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_KIOSK_ID, null);
        if (id == null) {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            // Take last 8 characters of Android ID for a reasonably unique yet manageable Kiosk ID
            String suffix = (androidId != null && androidId.length() >= 8)
                    ? androidId.substring(androidId.length() - 8).toUpperCase()
                    : UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            id = "AV-KIOSK-" + suffix;
            prefs.edit().putString(KEY_KIOSK_ID, id).apply();
        }
        return id;
    }

    public static String getPairingCode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String code = prefs.getString(KEY_PAIRING_CODE, null);
        if (code == null) {
            code = String.format("%06d", new Random().nextInt(1000000));
            prefs.edit().putString(KEY_PAIRING_CODE, code).apply();
        }
        return code;
    }
}
