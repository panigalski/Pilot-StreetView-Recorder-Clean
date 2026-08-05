package com.panigalski.pilotstreetview;

import android.content.Context;
import android.util.Log;

import com.pi.pano.PilotSDK;
import com.pi.pano.annotation.PiProEt;
import com.pi.pano.annotation.PiProIso;
import com.pi.pano.annotation.PiProWb;
import com.pi.pano.helper.PreviewHelper;

import java.io.File;
import java.util.Locale;

/**
 * Applies the image controls exposed by the public Pilot panorama SDK.
 *
 * These controls are deliberately applied only to the live camera pipeline;
 * unverified offline-stitching fields such as gamma and saturation are not
 * exposed because the supplied SDK does not connect them to 8K recording.
 */
public final class PictureAdjustments {
    private static final String TAG = "PilotPictureAdjust";
    private static final float PILOT_ONE_STITCHING_MAX = 0.9F;
    private static final File MANUAL_EXPOSURE_SWITCH = new File("/efs/.ex_en");
    private static final File MANUAL_EXPOSURE_VALUE = new File("/efs/.ex_val");

    private PictureAdjustments() {
    }

    public static boolean isManualExposureAvailable() {
        return MANUAL_EXPOSURE_SWITCH.exists()
                && MANUAL_EXPOSURE_VALUE.exists()
                && MANUAL_EXPOSURE_SWITCH.canWrite()
                && MANUAL_EXPOSURE_VALUE.canWrite();
    }

    /** Apply every saved setting after preview initialization or a user edit. */
    public static void applySaved(Context context) {
        int exposureTime = RecorderPreferences.getExposureTime(context);
        int iso = RecorderPreferences.getIso(context);

        if (exposureTime != PiProEt.normal) {
            if (!isManualExposureAvailable()) {
                exposureTime = PiProEt.normal;
                RecorderPreferences.setExposureTime(context, exposureTime);
            } else if (!isManualExposureIso(iso)) {
                iso = PiProIso._100;
                RecorderPreferences.setIso(context, iso);
            }
        }

        // Manual exposure uses the SDK's current ISO while writing /efs/.ex_val,
        // therefore ISO must be applied before exposure time. Each vendor call
        // is isolated because some Pilot OS builds omit individual parameters.
        try {
            PilotSDK.setISO(iso);
        } catch (RuntimeException error) {
            Log.w(TAG, "ISO adjustment is unavailable", error);
        }
        if (isManualExposureAvailable()) {
            try {
                PilotSDK.setExposeTime(exposureTime);
            } catch (RuntimeException error) {
                Log.w(TAG, "Exposure-time adjustment is unavailable", error);
            }
        }
        try {
            PilotSDK.setExposureCompensation(
                    RecorderPreferences.getExposureCompensation(context));
        } catch (RuntimeException error) {
            Log.w(TAG, "EV adjustment is unavailable", error);
        }

        String whiteBalance = RecorderPreferences.getWhiteBalance(context);
        boolean awbLock = PiProWb.auto.equals(whiteBalance)
                && RecorderPreferences.getAutoWhiteBalanceLock(context);
        try {
            PilotSDK.setWhiteBalance(whiteBalance);
        } catch (RuntimeException error) {
            Log.w(TAG, "White-balance adjustment is unavailable", error);
        }
        try {
            PilotSDK.setAutoWhiteBalanceLock(awbLock);
        } catch (RuntimeException error) {
            Log.w(TAG, "AWB lock is unavailable", error);
        }

        try {
            PreviewHelper.setStitchDistance(
                    RecorderPreferences.getStitchingDistance(context),
                    PILOT_ONE_STITCHING_MAX);
        } catch (RuntimeException error) {
            Log.w(TAG, "Stitching-distance adjustment is unavailable", error);
        }
    }

    public static boolean isManualExposureIso(int iso) {
        return iso == PiProIso._100
                || iso == PiProIso._200
                || iso == PiProIso._400
                || iso == PiProIso._600
                || iso == PiProIso._800
                || iso == PiProIso._1600
                || iso == PiProIso._3200;
    }

    public static String exposureTimeLabel(int value) {
        if (value == PiProEt.normal) {
            return "Auto";
        }
        return "1/" + value + " s";
    }

    public static String isoLabel(int value) {
        return value == PiProIso.auto ? "Auto" : "ISO " + value;
    }

    public static String evLabel(int value) {
        return value > 0 ? "EV +" + value : "EV " + value;
    }

    public static String whiteBalanceLabel(String value) {
        if (PiProWb.incandescent.equals(value)) {
            return "Incandescent";
        }
        if (PiProWb.fluorescent.equals(value)) {
            return "Fluorescent";
        }
        if (PiProWb.daylight.equals(value)) {
            return "Daylight";
        }
        if (PiProWb.cloudy_daylight.equals(value)) {
            return "Cloudy daylight";
        }
        return "Auto";
    }

    public static String stitchingDistanceLabel(int value) {
        if (value < 0) {
            return "Auto";
        }
        if (value >= 100) {
            return "Infinity (100)";
        }
        return String.format(Locale.US, "Manual %d", value);
    }

    public static String compactSummary(Context context) {
        return evLabel(RecorderPreferences.getExposureCompensation(context))
                + " • " + isoLabel(RecorderPreferences.getIso(context))
                + " • WB " + whiteBalanceLabel(RecorderPreferences.getWhiteBalance(context))
                + " • Stitch " + stitchingDistanceLabel(
                        RecorderPreferences.getStitchingDistance(context));
    }
}
