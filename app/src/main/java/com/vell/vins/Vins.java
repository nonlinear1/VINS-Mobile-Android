package com.vell.vins;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.camera2.CameraCharacteristics;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Vins implements SensorEventListener, LocationListener {
    private static final String TAG = Vins.class.getSimpleName();
    private String configPath = "/sdcard/vell_vins/config/mix2_480p";
    private long cameraTimestampsShiftWrtSensors = 0;
    private double sensorTimestampDalta = -1;

    private boolean isRecording = false;
    private File recordRootDir = new File(Environment.getExternalStorageDirectory(), "0_vins_record");
    private File recordDir;
    private File imageSaveDir;
    private FileWriter frameTxtWriter;
    private FileWriter imuTxtWriter;
    private FileWriter gpsTxtWriter;
    private HandlerThread threadHandler;
    private Handler recordHandler;

    public void init(CameraCharacteristics cameraCharacteristics) {
        cameraTimestampsShiftWrtSensors = ImageUtils.getCameraTimestampsShiftWrtSensors(cameraCharacteristics);
        VinsUtils.init(configPath);
    }

    public void startRecord() {
        if (isRecording) {
            return;
        }
        recordDir = new File(recordRootDir, new Date().toLocaleString());
        if (!recordDir.exists() && !recordDir.mkdirs()) {
            return;
        }
        imageSaveDir = new File(recordDir, "image");
        if (!imageSaveDir.exists() && !imageSaveDir.mkdirs()) {
            return;
        }
        try {
            frameTxtWriter = new FileWriter(new File(recordDir, "frame.txt"));
            imuTxtWriter = new FileWriter(new File(recordDir, "imu.txt"));
            gpsTxtWriter = new FileWriter(new File(recordDir, "gps.txt"));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        threadHandler = new HandlerThread("VINSRecordThread");
        threadHandler.start();
        recordHandler = new Handler(threadHandler.getLooper());
        isRecording = true;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void stopRecord() {
        if (!isRecording) {
            return;
        }
        try {
            if (frameTxtWriter != null) {
                frameTxtWriter.flush();
                frameTxtWriter.close();
                frameTxtWriter = null;
            }
            if (imuTxtWriter != null) {
                imuTxtWriter.flush();
                imuTxtWriter.close();
                imuTxtWriter = null;
            }
            if (gpsTxtWriter != null) {
                gpsTxtWriter.flush();
                gpsTxtWriter.close();
                gpsTxtWriter = null;
            }
            recordDir = null;
            if (threadHandler != null) {
                threadHandler.quit();
                threadHandler = null;
            }
            recordHandler = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        isRecording = false;
    }

    public void recvImage(final long imageTimestamp, final Mat originMat) {
        final long timeUsec =
                TimeUnit.NANOSECONDS.toMicros(imageTimestamp + cameraTimestampsShiftWrtSensors);
        double timeSec = timeUsec / 1000000.0;
        timeSec += sensorTimestampDalta;

        Log.d(TAG, "imageTimestamp: " + timeSec);
        if (isRecording) {
            final double finalTimeSec = timeSec;
            recordHandler.post(new Runnable() {

                @Override
                public void run() {
                    String fileName = String.format(Locale.CHINA, "%.6f.jpg", finalTimeSec);
                    File imageFile = new File(imageSaveDir, fileName);
                    Mat bgrMat = new Mat();
                    Imgproc.cvtColor(originMat, bgrMat, Imgproc.COLOR_RGB2BGR);

                    if (Imgcodecs.imwrite(imageFile.getAbsolutePath(), bgrMat)) {
                        try {
                            if (frameTxtWriter == null) return;
                            frameTxtWriter.append(String.format(Locale.CHINA, "%.6f\n", finalTimeSec));
                            frameTxtWriter.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
            });
        }
        VinsUtils.recvImage(timeSec, originMat.nativeObj);
    }

    public void recvImu(double timeSec, final double ax, final double ay, final double az, final double gx, final double gy, final double gz) {
        timeSec += sensorTimestampDalta;

        if (isRecording) {
            final double finalTimeSec = timeSec;
            recordHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (imuTxtWriter == null) return;
                        imuTxtWriter.append(String.format(Locale.CHINA, "%.6f %.6f %.6f %.6f %.6f %.6f %.6f\n", finalTimeSec, ax, ay, az, gx, gy, gz));
                        if (finalTimeSec % 100 <= 10) {
                            imuTxtWriter.flush();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            });
        }
        Log.d(TAG, String.format("imuTimestamp: %f,%f,%f,%f,%f,%f,%f", timeSec, ax, ay, az, gx, gy, gz));
        VinsUtils.recvImu(timeSec, ax, ay, az, gx, gy, gz);
    }

    public void recvGPS(double timeSec, final double latitude, final double longitude, final double altitude,
                        final double posAccuracy) {
        timeSec += sensorTimestampDalta;
        if (isRecording) {
            final double finalTimeSec = timeSec;
            recordHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (gpsTxtWriter == null) return;
                        gpsTxtWriter.append(String.format(Locale.CHINA, "%.6f %.6f %.6f %.6f %.6f\n", finalTimeSec, latitude, longitude, altitude, posAccuracy));
                        gpsTxtWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            });
        }
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
