// app/src/main/cpp/hardcore-engine.cpp
#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#define LOG_TAG "HardcoreEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AMediaCodec* nativeCodec = nullptr;

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_nothingrecorder_NativeEncoder_createHardwareSurface(JNIEnv *env, jobject thiz) {
    
    // 1. Create the Hardware Codec directly in C++
    nativeCodec = AMediaCodec_createEncoderByType("video/avc");
    
    // 2. Configure for iOS-level constraints (1080p, 60FPS, 20Mbps)
    AMediaFormat* format = AMediaFormat_new();
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "video/avc");
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, 1080);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, 2400);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, 20000000);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, 60);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, 2130708361); // COLOR_FormatSurface

    // 3. Real-Time Priority Hack (Bypass Android throttling)
    AMediaFormat_setInt32(format, "priority", 0);
    AMediaFormat_setInt32(format, "low-latency", 1);
    AMediaFormat_setFloat(format, "operating-rate", 120.0f);

    media_status_t status = AMediaCodec_configure(nativeCodec, format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaFormat_delete(format);

    if (status != AMEDIA_OK) {
        LOGE("Failed to configure native codec!");
        return nullptr;
    }

    // 4. Extract the raw GPU Memory Window
    ANativeWindow* surfaceWindow;
    AMediaCodec_createInputSurface(nativeCodec, &surfaceWindow);
    
    AMediaCodec_start(nativeCodec);

    // 5. Pass it back to Kotlin so VirtualDisplay can write directly to it
    if (surfaceWindow != nullptr) {
        return ANativeWindow_toSurface(env, surfaceWindow);
    }
    
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nothingrecorder_NativeEncoder_stopHardwareEncoder(JNIEnv *env, jobject thiz) {
    if (nativeCodec != nullptr) {
        AMediaCodec_stop(nativeCodec);
        AMediaCodec_delete(nativeCodec);
        nativeCodec = nullptr;
    }
}
