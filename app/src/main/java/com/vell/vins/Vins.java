package com.vell.vins;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.camera2.CameraCharacteristics;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

import org.opencv.core.Mat;

import java.util.concurrent.TimeUnit;

public class Vins implements SensorEventListener, LocationListener {
    private static final String TAG = Vins.class.getSimpleName();
    private String configPath = "/sdcard/vell_vins/config/mix2_480p";
    private long cameraTimestampsShiftWrtSensors = 0;
    private double sensorTimestampDalta = -1;

    public void init(CameraCharacteristics cameraCharacteristics) {
        cameraTimestampsShiftWrtSensors = ImageUtils.getCameraTimestampsShiftWrtSensors(cameraCharacteristics);
        VinsUtils.init(configPath);
    }

    public void recvImage(long imageTimestamp, final Mat originMat) {
        final long timeUsec =
                TimeUnit.NANOSECONDS.toMicros(imageTimestamp + cameraTimestampsShiftWrtSensors);
        double timeSec = timeUsec / 1000000.0;
//        timeSec += sensorTimestampDalta;

        Log.d(TAG, "imageTimestamp: " + timeSec);
        VinsUtils.recvImage(timeSec, originMat.nativeObj);
    }

    public void recvImu(double timeSec, double ax, double ay, double az, double gx, double gy, double gz) {
//        timeSec += sensorTimestampDalta;

        Log.d(TAG, String.format("imuTimestamp: %f,%f,%f,%f,%f,%f,%f", timeSec, ax, ay, az, gx, gy, gz));
        VinsUtils.recvImu(timeSec, ax, ay, az, gx, gy, gz);
    }

    public void recvGPS(double timeSec, double latitude, double longitude, double altitude,
                        double posAccuracy) {
        Log.d(TAG, String.format("recvGPS: %f,%f,%f,%f,%f", timeSec, latitude, longitude, altitude, posAccuracy));
        VinsUtils.recvGPS(timeSec, latitude, longitude, altitude, posAccuracy);
    }

    private SensorEvent lastAccSensorEvent = null;
    private SensorEvent lastGyrSensorEvent = null;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (sensorTimestampDalta == -1) {
            sensorTimestampDalta = System.currentTimeMillis() / 1000.0 - event.timestamp / 1000000000.0;
        }
        switch (event.sensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                lastGyrSensorEvent = event;
                break;
            case Sensor.TYPE_ACCELEROMETER:
                lastAccSensorEvent = event;
                break;
            case Sensor.TYPE_PRESSURE:
                break;
            default:
                break;
        }
        if (lastAccSensorEvent != null && lastGyrSensorEvent != null && lastAccSensorEvent.timestamp == lastGyrSensorEvent.timestamp) {
            long timeUsec = lastAccSensorEvent.timestamp / 1000;
            double timeSec = timeUsec / 1000000.0;
            double ax = lastAccSensorEvent.values[0];
            double ay = lastAccSensorEvent.values[1];
            double az = lastAccSensorEvent.values[2];
            double gx = lastGyrSensorEvent.values[0];
            double gy = lastGyrSensorEvent.values[1];
            double gz = lastGyrSensorEvent.values[2];
            recvImu(timeSec, ax, ay, az, gx, gy, gz);
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onLocationChanged(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        double altitude = location.getAltitude();
        double accuracy = location.getAccuracy();
        double timeSec = TimeUnit.NANOSECONDS.toMicros(location.getElapsedRealtimeNanos());

        recvGPS(timeSec, latitude, longitude, altitude, accuracy);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
