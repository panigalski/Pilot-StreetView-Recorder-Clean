package com.panigalski.pilotstreetview;

import android.content.Context;
import android.content.SharedPreferences;

import com.pi.pano.annotation.PiProEt;
import com.pi.pano.annotation.PiProEv;
import com.pi.pano.annotation.PiProIso;
import com.pi.pano.annotation.PiProWb;
import com.pi.pano.annotation.PiStitchingDistance;

/** Central preference access shared by preview and native-style settings screens. */
public final class RecorderPreferences {
    private static final String PREFS = "pilot_street_view_recorder";
    private static final String KEY_DESTINATION_MODE = "destination_mode";
    private static final String KEY_EXPOSURE_TIME = "picture_exposure_time";
    private static final String KEY_ISO = "picture_iso";
    private static final String KEY_EXPOSURE_COMPENSATION = "picture_ev";
    private static final String KEY_WHITE_BALANCE = "picture_white_balance";
    private static final String KEY_AWB_LOCK = "picture_awb_lock";
    private static final String KEY_STITCHING_DISTANCE = "picture_stitching_distance";
    private static final String KEY_TEMPERATURE_SOUND_THRESHOLD = "temperature_sound_threshold_c";

    public static final int DEFAULT_TEMPERATURE_SOUND_THRESHOLD_C = 80;
    public static final int MIN_TEMPERATURE_SOUND_THRESHOLD_C = 60;
    public static final int MAX_TEMPERATURE_SOUND_THRESHOLD_C = 95;

    private RecorderPreferences() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getDestinationMode(Context context) {
        return preferences(context).getString(
                KEY_DESTINATION_MODE, StorageResolver.MODE_INTERNAL);
    }

    public static void setDestinationMode(Context context, String mode) {
        preferences(context).edit().putString(KEY_DESTINATION_MODE, mode).apply();
    }

    public static int getExposureTime(Context context) {
        return preferences(context).getInt(KEY_EXPOSURE_TIME, PiProEt.normal);
    }

    public static void setExposureTime(Context context, int value) {
        preferences(context).edit().putInt(KEY_EXPOSURE_TIME, value).apply();
    }

    public static int getIso(Context context) {
        return preferences(context).getInt(KEY_ISO, PiProIso.auto);
    }

    public static void setIso(Context context, int value) {
        preferences(context).edit().putInt(KEY_ISO, value).apply();
    }

    public static int getExposureCompensation(Context context) {
        return preferences(context).getInt(
                KEY_EXPOSURE_COMPENSATION, PiProEv.normal);
    }

    public static void setExposureCompensation(Context context, int value) {
        preferences(context).edit().putInt(KEY_EXPOSURE_COMPENSATION, value).apply();
    }

    public static String getWhiteBalance(Context context) {
        return preferences(context).getString(KEY_WHITE_BALANCE, PiProWb.auto);
    }

    public static void setWhiteBalance(Context context, String value) {
        preferences(context).edit().putString(KEY_WHITE_BALANCE, value).apply();
    }

    public static boolean getAutoWhiteBalanceLock(Context context) {
        return preferences(context).getBoolean(KEY_AWB_LOCK, false);
    }

    public static void setAutoWhiteBalanceLock(Context context, boolean value) {
        preferences(context).edit().putBoolean(KEY_AWB_LOCK, value).apply();
    }

    public static int getStitchingDistance(Context context) {
        return preferences(context).getInt(
                KEY_STITCHING_DISTANCE, PiStitchingDistance.auto);
    }

    public static void setStitchingDistance(Context context, int value) {
        preferences(context).edit().putInt(KEY_STITCHING_DISTANCE, value).apply();
    }

    public static int getTemperatureSoundThreshold(Context context) {
        int value = preferences(context).getInt(
                KEY_TEMPERATURE_SOUND_THRESHOLD, DEFAULT_TEMPERATURE_SOUND_THRESHOLD_C);
        return Math.max(MIN_TEMPERATURE_SOUND_THRESHOLD_C,
                Math.min(MAX_TEMPERATURE_SOUND_THRESHOLD_C, value));
    }

    public static void setTemperatureSoundThreshold(Context context, int value) {
        int clamped = Math.max(MIN_TEMPERATURE_SOUND_THRESHOLD_C,
                Math.min(MAX_TEMPERATURE_SOUND_THRESHOLD_C, value));
        preferences(context).edit().putInt(KEY_TEMPERATURE_SOUND_THRESHOLD, clamped).apply();
    }

    public static void resetPictureAdjustments(Context context) {
        preferences(context).edit()
                .putInt(KEY_EXPOSURE_TIME, PiProEt.normal)
                .putInt(KEY_ISO, PiProIso.auto)
                .putInt(KEY_EXPOSURE_COMPENSATION, PiProEv.normal)
                .putString(KEY_WHITE_BALANCE, PiProWb.auto)
                .putBoolean(KEY_AWB_LOCK, false)
                .putInt(KEY_STITCHING_DISTANCE, PiStitchingDistance.auto)
                .apply();
    }
}
