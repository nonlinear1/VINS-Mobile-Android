//
// Created by vell on 19-2-23.
//

#ifndef VINS_MOBILE_ANDROIDPORT_ANDROID_WRAPPER_H
#define VINS_MOBILE_ANDROIDPORT_ANDROID_WRAPPER_H
#include <jni.h>
#include <ViewController.hpp>
bool inited = false;
std::unique_ptr<ViewController> viewControllerGlobal;

vector<Vector3f> getCorrectPointCloud() {
    return viewControllerGlobal->getCorrectPointCloud();
}

Vector3f getLatestPosition() {
    return viewControllerGlobal->getLatestPosition();
}

Matrix3f getLatestRotation() {
    return viewControllerGlobal->getLatestRotation();
}

int getInitProcess() {
    return viewControllerGlobal->getInitProcess();
}

bool initSucess() {
    return viewControllerGlobal->initSucess();
}
#endif //VINS_MOBILE_ANDROIDPORT_ANDROID_WRAPPER_H
