package com.thkoeln.jmoeller.vins_mobile_androidport;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.media.Image;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ImageUtils {
    public static long getCameraTimestampsShiftWrtSensors(CameraCharacteristics cameraCharacteristics) {
        long cameraTimestampsShiftWrtSensors = 0;
        final int cameraTimestampSource =
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        Log.i("SensorDataSaver", "cameraTimestampSource： " + cameraTimestampSource);
        switch (cameraTimestampSource) {
            case CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN:
                // Assume that the camera timer is based on System.nanoTime(). This is the case on Nexus 5
                // and Nexus 5X.

                // Allocate storage for multiple attempts at computing the difference between
                // SystemClock.elapsedRealtimeNanos() (which is not paused when sleeping) and
                // System.nanoTime() (which is paused in deep sleep).
                // We want to compute the difference repeatedly several times to warm up the caches and
                // achieve better accuracy.
                final long[] elapsedNanoTimeDiffCache = new long[5];
                for (int i = 0; i < elapsedNanoTimeDiffCache.length; ++i) {
                    elapsedNanoTimeDiffCache[i] = SystemClock.elapsedRealtimeNanos() - System.nanoTime();
                }
                // Log all the cached timer differences to prevent the compiler from optimizing away the
                // function calls.
                Log.i("SensorDataSaver", "elapsedRealtimeNanos - nanoTime difference values: " +
                        Arrays.toString(elapsedNanoTimeDiffCache));

                // Use the las estimate, which should be more accurate than the first.
                cameraTimestampsShiftWrtSensors =
                        elapsedNanoTimeDiffCache[elapsedNanoTimeDiffCache.length - 1];
                break;

            case CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME:
                cameraTimestampsShiftWrtSensors = 0;
                break;

            default:
                throw new AssertionError("Unknown camera timestamps source: " + cameraTimestampSource);
        }
        return cameraTimestampsShiftWrtSensors;
    }
}
