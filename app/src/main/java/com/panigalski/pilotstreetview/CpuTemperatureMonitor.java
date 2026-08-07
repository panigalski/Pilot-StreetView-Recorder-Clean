package com.panigalski.pilotstreetview;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Pilot One CPU temperature monitor modelled on Labpano's original
 * CpuTemperature/CpuTemperatureWatcherMgr implementation.
 *
 * The thermal sensor is sampled on a dedicated worker thread every second.
 * A zero value means the thermal node could not be read or parsed.
 */
public final class CpuTemperatureMonitor {
    public interface Listener {
        void onTemperatureChanged(int temperatureCelsius);
    }

    private static final String THERMAL_NODE = "/sys/class/thermal/thermal_zone0/temp";
    private static final long POLL_INTERVAL_MS = 1000L;

    private final Object lock = new Object();
    private ScheduledExecutorService executor;
    private volatile Listener listener;
    private volatile int lastTemperature;

    public void start(Listener listener) {
        synchronized (lock) {
            this.listener = listener;
            if (executor != null) {
                return;
            }
            lastTemperature = 0;
            executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "CpuTemperatureWatch");
                    thread.setDaemon(true);
                    return thread;
                }
            });
            executor.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    int temperature = readCpuTemperatureCelsius();
                    if (temperature == lastTemperature) {
                        return;
                    }
                    lastTemperature = temperature;
                    Listener callback = CpuTemperatureMonitor.this.listener;
                    if (callback != null) {
                        try {
                            callback.onTemperatureChanged(temperature);
                        } catch (RuntimeException ignored) {
                            // Monitoring must never destabilize the recorder.
                        }
                    }
                }
            }, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        synchronized (lock) {
            listener = null;
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            lastTemperature = 0;
        }
    }

    public int getLastTemperature() {
        return lastTemperature;
    }

    static int readCpuTemperatureCelsius() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(THERMAL_NODE));
            String line = reader.readLine();
            if (line == null) {
                return 0;
            }
            long raw = Long.parseLong(line.trim());
            // Pilot One reports millidegrees C (e.g. 82000 = 82 C). Keep a
            // fallback for firmware variants that already report whole C.
            if (Math.abs(raw) >= 1000L) {
                raw /= 1000L;
            }
            if (raw < -40L || raw > 200L) {
                return 0;
            }
            return (int) raw;
        } catch (IOException | NumberFormatException ignored) {
            return 0;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // Nothing useful to do here.
                }
            }
        }
    }
}
