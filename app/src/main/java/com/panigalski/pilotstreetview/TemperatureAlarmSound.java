package com.panigalski.pilotstreetview;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/** Short, dependency-free over-temperature warning tone. */
public final class TemperatureAlarmSound {
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static ToneGenerator toneGenerator;

    private TemperatureAlarmSound() { }

    public static void play() {
        MAIN.post(new Runnable() {
            @Override public void run() {
                synchronized (LOCK) {
                    stopLocked();
                    try {
                        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 1500);
                        MAIN.postDelayed(new Runnable() {
                            @Override public void run() {
                                synchronized (LOCK) {
                                    stopLocked();
                                }
                            }
                        }, 1700L);
                    } catch (RuntimeException ignored) {
                        stopLocked();
                    }
                }
            }
        });
    }

    public static void stop() {
        MAIN.post(new Runnable() {
            @Override public void run() {
                synchronized (LOCK) {
                    stopLocked();
                }
            }
        });
    }

    private static void stopLocked() {
        ToneGenerator generator = toneGenerator;
        toneGenerator = null;
        if (generator != null) {
            try { generator.stopTone(); } catch (RuntimeException ignored) { }
            try { generator.release(); } catch (RuntimeException ignored) { }
        }
    }
}
