package com.vell.vins;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Rational;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import com.thkoeln.jmoeller.vins_mobile_androidport.R;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_OFF;

public class JavaCameraActivity extends Activity {
    private static final String TAG = JavaCameraActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_CODE = 12345;
    private final int imageWidth = 640;
    private final int imageHeight = 360;
    private HandlerThread threadHandler;
    private Handler cameraHandler;
    private String cameraID = "0";
    private CameraDevice camera;
    private CaptureRequest.Builder captureBuilder;
    private ImageReader imageReader;
    private Vins vins;
    private CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            try {
                updateCameraView(session);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };
    private boolean saveFrame = false;
    private File saveDir = new File(Environment.getExternalStorageDirectory(), "1_test");
    private boolean useLocalImage = true;
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        /*
         *  The following method will be called every time an image is ready
         *  be sure to use method acquireNextImage() and then close(), otherwise, the display may STOP
         */
        @Override
        public void onImageAvailable(ImageReader reader) {
            // get the newest frame
            Image image = reader.acquireNextImage();

            if (image == null) {
                return;
            }
//            Log.i(TAG,"get new image, height: " + image.getHeight() + " width: " + image.getWidth());
            Mat originMat = ImageUtils.getMatFromImage(image);

            if (vins != null) {
                vins.recvImage(image.getTimestamp(), originMat);
            }

            final Bitmap originBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.RGB_565);
            Utils.matToBitmap(originMat, originBitmap);
            if (saveFrame) {
                try {
                    saveFrame = false;
                    if (!saveDir.exists()) {
                        saveDir.mkdirs();
                    }
                    File frameFile = new File(saveDir, String.format("%s.jpg", new Date().toString()));

                    originBitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(frameFile));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }

            final StringBuilder infoBuilder = new StringBuilder();
            float[] pos = VinsUtils.getLatestPosition();
            float[] ang = VinsUtils.getLatestEulerAngles();
            infoBuilder.append(String.format(Locale.CHINA, "pos: %.2f %.2f %.2f\n", pos[0], pos[1], pos[2]));
            infoBuilder.append(String.format(Locale.CHINA, "ang: %.2f %.2f %.2f\n", ang[0], ang[1], ang[2]));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((ImageView) findViewById(R.id.java_camera_view)).setImageBitmap(originBitmap);

                    ((TextView) findViewById(R.id.tv_info)).setText(infoBuilder.toString());
                }
            });
            image.close();

            Log.i(TAG, "pos: " + VinsUtils.getLatestPosition()[0]);
            Log.i(TAG, "rot: " + VinsUtils.getLatestRotation()[0]);
        }
    };

    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            try {
                camera = cameraDevice;

                startCameraView(camera);

                if (vins != null) {
                    vins.init(getCameraCharacteristics(cameraDevice));
                    VinsUtils.enableAR(true);
                }

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
        }

        @Override
        public void onError(CameraDevice camera, int error) {
        }
    };

    private CameraCharacteristics getCameraCharacteristics(CameraDevice cameraDevice) throws CameraAccessException {
        final String cameraId = cameraDevice.getId();
        final CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        return cameraManager.getCameraCharacteristics(cameraId);
    }

    static {
        System.loadLibrary("opencv_java3");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_java_camera);
        // first make sure the necessary permissions are given
        checkPermissionsIfNeccessary();

        initLooper();

        try {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            // check permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                checkPermissionsIfNeccessary();
                return;
            }

            // start up Camera (not the recording)
            cameraManager.openCamera(cameraID, cameraDeviceStateCallback, cameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        findViewById(R.id.tv_info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (useLocalImage) {
                    useLocalImage = false;
                    Toast.makeText(JavaCameraActivity.this, "使用摄像头数据", Toast.LENGTH_SHORT).show();
                } else {
                    useLocalImage = true;
                    Toast.makeText(JavaCameraActivity.this, "使用本地数据", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.save_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveFrame = true;
            }
        });

        vins = new Vins();

        subscribeToImuUpdates(vins, SensorManager.SENSOR_DELAY_FASTEST);
        subscribeToLocationUpdates(vins, 20);

        // 增加gps信息展示
        final LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationManager.addGpsStatusListener(new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                GpsStatus status = locationManager.getGpsStatus(null); //取当前状态
                StringBuilder gpsInfo = new StringBuilder();
                gpsInfo.append("gps num: ");
                if (status == null) {
                    gpsInfo.append(0);
                } else if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                    int maxSatellites = status.getMaxSatellites();
                    Iterator<GpsSatellite> it = status.getSatellites().iterator();
                    int count = 0;
                    while (it.hasNext() && count <= maxSatellites) {
                        GpsSatellite s = it.next();
                        count++;
                    }
                    gpsInfo.append(count);
                }
                ((TextView) findViewById(R.id.tv_gps_info)).setText(gpsInfo.toString());
            }
        });
    }

    /**
     * Starting separate thread to handle camera input
     */
    private void initLooper() {
        threadHandler = new HandlerThread("Camera2Thread");
        threadHandler.start();
        cameraHandler = new Handler(threadHandler.getLooper());
    }


    /**
     * starts CameraView
     */
    private void startCameraView(CameraDevice camera) throws CameraAccessException {
        try {
            // to set request for CameraView
            captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // to set the format of captured images and the maximum number of images that can be accessed in mImageReader
        imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.YUV_420_888, 1);

        imageReader.setOnImageAvailableListener(onImageAvailableListener, cameraHandler);

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraID);
        // get the StepSize of the auto exposure compensation
        Rational aeCompStepSize = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        if (aeCompStepSize == null) {
            Log.e(TAG, "Camera doesn't support setting Auto-Exposure Compensation");
            finish();
        }
        Float yourMinFocus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        Float yourMaxFocus = characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);
        Log.i(TAG, "focus min: " + yourMinFocus + " max: " + yourMaxFocus);

        // 不自动对焦
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);
//        captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 2.0f);
        // 固定iso
//        captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,);

        // 不自动白平衡
//        captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
        captureBuilder.addTarget(imageReader.getSurface());

        //output Surface
        List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(imageReader.getSurface());

        camera.createCaptureSession(outputSurfaces, sessionStateCallback, cameraHandler);
    }

    /**
     * Starts the RepeatingRequest for
     */
    private void updateCameraView(CameraCaptureSession session)
            throws CameraAccessException {

        session.setRepeatingRequest(captureBuilder.build(), null, cameraHandler);
    }

    /**
     * @return true if permissions where given
     */
    private boolean checkPermissionsIfNeccessary() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions != null) {
                List<String> permissionsNotGrantedYet = new ArrayList<>(info.requestedPermissions.length);
                for (String p : info.requestedPermissions) {
                    if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNotGrantedYet.add(p);
                    }
                }
                if (permissionsNotGrantedYet.size() > 0) {
                    ActivityCompat.requestPermissions(this, permissionsNotGrantedYet.toArray(new String[permissionsNotGrantedYet.size()]),
                            PERMISSIONS_REQUEST_CODE);
                    return false;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean hasAllPermissions = true;
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length == 0)
                hasAllPermissions = false;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED)
                    hasAllPermissions = false;
            }

            if (!hasAllPermissions) {
                finish();
            }
        }
    }

    private void subscribeToImuUpdates(SensorEventListener listener, int delay) {
        final SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE), delay);
        sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), delay);
    }

    private void subscribeToLocationUpdates(LocationListener listener, long minTimeMsec) {
        final LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        final String bestProvider = locationManager.getBestProvider(new Criteria(), false);
        locationManager.requestLocationUpdates(bestProvider, minTimeMsec, 0.01f, listener);
    }

    static {
        System.loadLibrary("opencv_java3");
    }
}
