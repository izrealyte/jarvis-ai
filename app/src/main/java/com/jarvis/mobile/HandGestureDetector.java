package com.jarvis.mobile;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

/**
 * Native Touchless Hand Motion & Wave Detector for JARVIS.
 * Uses the proximity/motion sensor above the screen to detect hand waves.
 */
public class HandGestureDetector implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private long lastWaveTime = 0;
    private int waveCount = 0;
    private boolean isHandNear = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable resetRunnable;

    public interface OnHandGestureListener {
        void onHandWave();
        void onDoubleHandWave();
    }

    private final OnHandGestureListener listener;

    public HandGestureDetector(Context context, OnHandGestureListener listener) {
        this.listener = listener;
        if (context != null) {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            }
        }
    }

    public boolean start() {
        if (sensorManager != null && proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            return true;
        }
        return false;
    }

    public void stop() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null) return;
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            float distance = event.values[0];
            float maxRange = proximitySensor.getMaximumRange();
            boolean near = distance < maxRange && distance < 5.0f;

            long now = System.currentTimeMillis();

            if (near && !isHandNear) {
                isHandNear = true;
                if (now - lastWaveTime < 800) {
                    waveCount++;
                } else {
                    waveCount = 1;
                }
                lastWaveTime = now;

                if (resetRunnable != null) handler.removeCallbacks(resetRunnable);
                resetRunnable = () -> {
                    if (waveCount == 1 && listener != null) {
                        listener.onHandWave();
                    } else if (waveCount >= 2 && listener != null) {
                        listener.onDoubleHandWave();
                    }
                    waveCount = 0;
                };
                handler.postDelayed(resetRunnable, 400);

            } else if (!near) {
                isHandNear = false;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
