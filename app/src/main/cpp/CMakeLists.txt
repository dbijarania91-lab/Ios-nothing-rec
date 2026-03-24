# app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.10.2)

project("nothingrecorder")

# Create our native library
add_library(
        hardcore-engine
        SHARED
        hardcore-engine.cpp )

# Link the required Android hardware and media libraries
find_library( log-lib log )
find_library( android-lib android )
find_library( mediandk-lib mediandk )

target_link_libraries(
        hardcore-engine
        ${log-lib}
        ${android-lib}
        ${mediandk-lib} )
        
