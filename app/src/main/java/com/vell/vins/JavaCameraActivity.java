package com.vell.vins;

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
import android.hardware.camera2.CameraDevice;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.thkoeln.jmoeller.vins_mobile_androidport.R;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class JavaCameraActivity extends Activity {
    private static final String TAG = JavaCameraActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_CODE = 12345;
    private final int imageWidth = 640;
    private final int imageHeight = 360;
    private ImageReader imageReader;
    private JavaCamera javaCamera;
    private Vins vins;
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_java_camera);
        // first make sure the necessary permissions are given
        checkPermissionsIfNeccessary();

        // to set the format of captured images and the maximum number of images that can be accessed in mImageReader
        imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.YUV_420_888, 1);

        imageReader.setOnImageAvailableListener(onImageAvailableListener, null);

        javaCamera = new JavaCamera();
        javaCamera.addImageReader(imageReader);

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
        findViewById(R.id.record).setOnClickListener(new View.OnClickListener() {
            boolean isRecording = false;

            @Override
            public void onClick(View v) {
                if (isRecording) {
                    vins.slamRecorder.stopRecord();
                } else {
                    vins.slamRecorder.startRecord();
                }
                isRecording = vins.slamRecorder.isRecording();
                ((TextView) v).setText(isRecording ? "停止" : "录像");
            }
        });
        findViewById(R.id.java_camera_view).setOnClickListener(new View.OnClickListener() {
            boolean enable = false;

            @Override
            public void onClick(View v) {
                enable = !enable;
                VinsUtils.enableAR(enable);
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

    @Override
    protected void onResume() {
        super.onResume();
        javaCamera.open(this, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                try {
                    vins.init(javaCamera.getCameraCharacteristics());
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
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        javaCamera.close();
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
