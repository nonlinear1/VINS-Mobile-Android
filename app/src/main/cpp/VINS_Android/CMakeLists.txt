set(TARGET_NAME VINS_Android)
set(${TARGET_NAME}_SRCS
    ViewController.cpp
)

SET(OpenCV_DIR /home/vell/workspace/opencv/OpenCV-android-sdk-3.4.5/sdk/native/jni)
find_package(OpenCV REQUIRED)

add_library(
    ${TARGET_NAME}
    STATIC
    ${${TARGET_NAME}_SRCS}
)

target_link_libraries( 
    ${TARGET_NAME}
    VINSMobile
    android
    log
    ${OpenCV_LIBS}
)

target_include_directories(${TARGET_NAME} INTERFACE ${CMAKE_CURRENT_SOURCE_DIR})