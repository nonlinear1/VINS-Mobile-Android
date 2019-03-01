package com.vell.vins;

public class VinsUtils {
    public static native void recvImu(double timeS, double ax, double ay, double az, double gx, double gy, double gz);

    public static native void recvImage(double timeS, long rgbaPtr);

    public static native void init(String configPath);

    public static native float[] getLatestPosition();

    public static native float[] getLatestRotation();

    public static native float[] getLatestEulerAngles();

    public static native float[] getLatestGroundCenter();

    public static native void enableAR(boolean isAR);

    public static native boolean initSucess();

    static {
        System.loadLibrary("vins_android_wrapper");
    }
}
