package com.panigalski.pilotstreetview;

import android.app.Application;
import android.util.Log;

import com.pi.pano.PilotSDK;

/**
 * Configures the native Labpano SDK before any preview surface is created.
 * The original system Camera application performs this setup from its
 * Application.onCreate(), not from the preview callback.
 */
public final class PilotRecorderApplication extends Application {
    private static final String TAG = "PilotRecorderApp";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PilotSDK.setDeviceModel(PilotSDK.DEVICE_MODEL_PILOT_ONE);
            PilotSDK.setFirmware("5.18.11");
            Log.i(TAG, "PilotSDK device model and firmware configured before preview init");
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to preconfigure PilotSDK", error);
        }
    }
}
