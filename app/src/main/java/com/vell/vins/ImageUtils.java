package com.vell.vins;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.media.Image;
import android.os.SystemClock;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ImageUtils {
    static {
        System.loadLibrary("opencv_java3");
    }

    public static Mat getMatFromImage(final Image image) {
        int w = image.getWidth();
        int h = image.getHeight();

        Mat originMat = new Mat();
        Image.Plane[] planes = image.getPlanes();
        assert (planes.length == 3);
        assert (image.getFormat() == ImageFormat.YUV_420_888);

        // see also https://developer.android.com/reference/android/graphics/ImageFormat.html#YUV_420_888
        // Y plane (0) non-interleaved => stride == 1; U/V plane interleaved => stride == 2
        assert (planes[0].getPixelStride() == 1);
        assert (planes[1].getPixelStride() == 2);
        assert (planes[2].getPixelStride() == 2);

        ByteBuffer y_plane = planes[0].getBuffer();
        ByteBuffer uv_plane = planes[1].getBuffer();
        Mat y_mat = new Mat(h, w, CvType.CV_8UC1, y_plane);
        Mat uv_mat = new Mat(h / 2, w / 2, CvType.CV_8UC2, uv_plane);
        Imgproc.cvtColorTwoPlane(y_mat, uv_mat, originMat, Imgproc.COLOR_YUV2BGR_NV21);

        return originMat;
    }

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
