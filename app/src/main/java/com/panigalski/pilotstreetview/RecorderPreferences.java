package com.panigalski.pilotstreetview;

import android.content.Context;
import android.content.SharedPreferences;

/** Central preference access shared by preview and native-style settings screens. */
public final class RecorderPreferences {
    private static final String PREFS = "pilot_street_view_recorder";
    private static final String KEY_DESTINATION_MODE = "destination_mode";

    private RecorderPreferences() {
    }

    public static String getDestinationMode(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return preferences.getString(KEY_DESTINATION_MODE, StorageResolver.MODE_INTERNAL);
    }

    public static void setDestinationMode(Context context, String mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DESTINATION_MODE, mode)
                .apply();
    }
}
