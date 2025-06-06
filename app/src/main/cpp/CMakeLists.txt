# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("mnnllmapp")

add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        llm_mnn_jni.cpp
        diffusion_jni.cpp
        diffusion_session.cpp
        llm_session.cpp
        crash_util.cpp
)

# Local include directories (no external dependencies)
include_directories("${CMAKE_SOURCE_DIR}/include/")
include_directories("${CMAKE_SOURCE_DIR}/transformers/diffusion/engine/include/")
include_directories("${CMAKE_SOURCE_DIR}/transformers/llm/engine/include")
include_directories("${CMAKE_SOURCE_DIR}/tools/audio/include")
include_directories("${CMAKE_SOURCE_DIR}/third_party")

# Use prebuilt library from jniLibs
set(LIB_PATH "${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a")

# Import the prebuilt MNN library
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${LIB_PATH}/libMNN.so")

# Link libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log
        MNN
)
