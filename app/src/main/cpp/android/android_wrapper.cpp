//
// Created by vell on 19-2-23.
//
#include "android_wrapper.h"

extern "C"
JNIEXPORT void JNICALL
Java_com_vell_vins_VinsUtils_recvImu(JNIEnv *env, jclass type, jdouble timeSec, jdouble ax,
                                     jdouble ay, jdouble az, jdouble gx, jdouble gy, jdouble gz) {
    if (inited) {
        ImuPtr imu_msg(new IMU_MSG());
        imu_msg->header = timeSec;
        imu_msg->acc << ax, ay, az;
        imu_msg->gyr << gx, gy, gz;
        viewControllerGlobal->recv_imu(imu_msg);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vell_vins_VinsUtils_recvImage(JNIEnv *env, jclass type, jdouble timeS, jlong rgbaPtr) {
    if (inited) {
        cv::Mat &rgba = *(cv::Mat *) rgbaPtr;
        Mat rotatedRgba;
        cv::rotate(rgba, rotatedRgba, cv::ROTATE_90_CLOCKWISE);
        viewControllerGlobal->processImage(rotatedRgba, timeS, false);
        cv::rotate(rotatedRgba, rgba, cv::ROTATE_90_COUNTERCLOCKWISE);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vell_vins_VinsUtils_init(JNIEnv *env, jclass type, jstring configPath_) {
    const char *configPath = env->GetStringUTFChars(configPath_, 0);
    if (!inited) {
        viewControllerGlobal = std::unique_ptr<ViewController>(new ViewController);
        LOGI("Successfully created Viewcontroller Object");

        viewControllerGlobal->testMethod();

        // startup method of ViewController
        viewControllerGlobal->viewDidLoad();
        inited = true;
    }
    env->ReleaseStringUTFChars(configPath_, configPath);
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vell_vins_VinsUtils_getLatestPosition(JNIEnv *env, jclass type) {
    Vector3f pos = viewControllerGlobal->getLatestPosition();
    jfloat posJ[3] = {
            pos(0), pos(1), pos(2)
    };
    jfloatArray ret = env->NewFloatArray(3);
    env->SetFloatArrayRegion(ret, 0, 3, posJ);
    return ret;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vell_vins_VinsUtils_getLatestRotation(JNIEnv *env, jclass type) {

    Matrix3f rot = viewControllerGlobal->getLatestRotation();
    // 旋转90度
    Eigen::Matrix3f RIC = Utility::ypr2R(Vector3d(0.0f, 90.0f, 0.0f)).cast<float>();
    rot = rot * RIC;
    jfloat posJ[9] = {
            rot(0, 0), rot(0, 1), rot(0, 2),
            rot(1, 0), rot(1, 1), rot(1, 2),
            rot(2, 0), rot(2, 1), rot(2, 2)
    };
    jfloatArray ret = env->NewFloatArray(9);
    env->SetFloatArrayRegion(ret, 0, 9, posJ);
    return ret;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vell_vins_VinsUtils_getLatestEulerAngles(JNIEnv *env, jclass type) {

    Matrix3f rot = viewControllerGlobal->getLatestRotation();
    Eigen::Matrix3f RIC = Utility::ypr2R(Vector3d(0.0f, 90.0f, 0.0f)).cast<float>();
    rot = rot * RIC;
    Vector3d angles = Utility::R2ypr(rot.cast<double>());
    jfloat retJ[3] = {
            angles(0), angles(1), angles(2)
    };
    jfloatArray ret = env->NewFloatArray(3);
    env->SetFloatArrayRegion(ret, 0, 3, retJ);
    return ret;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_vell_vins_VinsUtils_initSucess(JNIEnv *env, jclass type) {
    if (inited) {
        return (jboolean) viewControllerGlobal->initSucess();
    }
    return (jboolean) false;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vell_vins_VinsUtils_enableAR(JNIEnv *env, jclass type, jboolean isAR) {
    if (inited) {
        viewControllerGlobal->enableAR(isAR);
    }
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vell_vins_VinsUtils_getLatestGroundCenter(JNIEnv *env, jclass type) {
    Vector3f pos = viewControllerGlobal->getLatestGroundCenter();
    jfloat posJ[3] = {
            pos(0), pos(1), pos(2)
    };
    jfloatArray ret = env->NewFloatArray(3);
    env->SetFloatArrayRegion(ret, 0, 3, posJ);
    return ret;
}