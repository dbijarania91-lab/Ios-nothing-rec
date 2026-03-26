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
    
    // 1. THE CODEC FIX: Force HEVC (H.265) hardware creation instead of AVC
    nativeCodec = AMediaCodec_createEncoderByType("video/hevc");

    // 2. Configure for iOS-level constraints
    AMediaFormat* format = AMediaFormat_new();
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "video/hevc");
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, 2400);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, 1080);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, 60);
    
    // 3. THE APPLE WEIGHT CLASS: 40 Mbps (Brute force raw data)
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, 40000000);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, 2130708361); // COLOR_FormatSurface

    // 4. 8-BIT STABILITY FOR SNAPDRAGON 778G+
    // 1 = HEVCProfileMain (8-bit), 262144 = HEVCHighTierLevel51
    AMediaFormat_setInt32(format, "profile", 1);
    AMediaFormat_setInt32(format, "level", 262144);

    // 5. HARDWARE NDK FLAGS (Low latency bypass for zero game lag)
    AMediaFormat_setInt32(format, "priority", 0);
    AMediaFormat_setInt32(format, "low-latency", 1);
    AMediaFormat_setFloat(format, "operating-rate", 120.0f);

    // 6. THE FRAME-DROP RECOVERY (The Safety Net)
    // 16666 microseconds = exactly 1 frame at 60 FPS
    AMediaFormat_setInt64(format, "repeat-previous-frame-after", 16666);

    media_status_t status = AMediaCodec_configure(nativeCodec, format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaFormat_delete(format);

    if (status != AMEDIA_OK) {
        LOGE("Failed to configure native codec!");
        return nullptr;
    }

    // 7. Extract the raw GPU Memory Window
    ANativeWindow* surfaceWindow;
    AMediaCodec_createInputSurface(nativeCodec, &surfaceWindow);

    AMediaCodec_start(nativeCodec);

    // 8. Pass it back to Kotlin so VirtualDisplay can write directly to it
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
