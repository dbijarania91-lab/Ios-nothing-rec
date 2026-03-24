package com.example.nothingrecorder

import android.view.Surface
import android.util.Log

class NativeEncoder {
    
    // This loads the C++ file we wrote in CMakeLists.txt
    companion object {
        init {
            try {
                System.loadLibrary("hardcore-engine")
                Log.d("NativeEncoder", "C++ Hardcore Engine Loaded Successfully!")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("NativeEncoder", "Failed to load C++ engine: ${e.message}")
            }
        }
    }

    // 1. Creates the Zero-Copy Hardware Window directly from the GPU
    external fun createHardwareSurface(): Surface?

    // 2. Kills the C++ encoder instantly when you hit stop
    external fun stopHardwareEncoder()
}

