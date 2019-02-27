package com.vell.vins;

import java.nio.ByteBuffer;

public class VinsUtils {
    public static native void recvImu(double timeS, double ax, double ay, double az, double gx, double gy, double gz);
    public static native void recvImage(double timeS, long rgbaPtr);
    public static native void init(String configPath);
    public static native float[] getLatestPosition();
    public static native float[] getLatestRotation();
    public static native boolean initSucess();
    static {
        System.loadLibrary("vins_android_wrapper");
    }
}
