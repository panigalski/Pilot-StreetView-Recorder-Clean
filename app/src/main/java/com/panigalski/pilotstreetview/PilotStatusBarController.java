package com.panigalski.pilotstreetview;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Updates the custom white title/time/battery bar used throughout the native-style UI. */
public final class PilotStatusBarController {
    private final Activity activity;
    private final TextView timeView;
    private final TextView batteryView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean registered;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            timeView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            handler.postDelayed(this, 30_000L);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int percent = level >= 0 && scale > 0 ? Math.round(level * 100F / scale) : 0;
            batteryView.setText(percent + "%");
        }
    };

    public PilotStatusBarController(Activity activity, String title) {
        this.activity = activity;
        TextView titleView = activity.findViewById(R.id.status_title);
        timeView = activity.findViewById(R.id.status_time);
        batteryView = activity.findViewById(R.id.status_battery_text);
        titleView.setText(title);
    }

    public void start() {
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        if (!registered) {
            try {
                activity.registerReceiver(
                        batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                registered = true;
            } catch (RuntimeException ignored) {
                batteryView.setText("--%");
            }
        }
    }

    public void stop() {
        handler.removeCallbacks(clockTick);
        if (registered) {
            try {
                activity.unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered by the platform.
            }
            registered = false;
        }
    }
}
