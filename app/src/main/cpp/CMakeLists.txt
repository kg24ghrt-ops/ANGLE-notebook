cmake_minimum_required(VERSION 3.22.1)
project(angledpaper LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# ANGLE backend
# ---------------------------------------------------------------------------
# ANGLE is the in-process GLES implementation: app code talks the GLES 3.x API
# and ANGLE translates it (typically to Vulkan) at runtime. This project does
# not build ANGLE itself; the CI workflow builds ANGLE for Android and copies
# libEGL.so / libGLESv2.so into src/main/jniLibs/<ABI>/ before the Gradle
# build runs. CMake links the prebuilt libraries from that location.
#
# See ARCHITECTURE.md and .github/workflows/android-build.yml.
set(ANGLE_LIB_DIR "${CMAKE_CURRENT_LIST_DIR}/../../jniLibs/${ANDROID_ABI}")

find_library(ANGLE_EGL NAMES "libEGL.so" PATHS "${ANGLE_LIB_DIR}" NO_DEFAULT_PATH)
find_library(ANGLE_GLESv2 NAMES "libGLESv2.so" PATHS "${ANGLE_LIB_DIR}" NO_DEFAULT_PATH)

if(NOT ANGLE_EGL OR NOT ANGLE_GLESv2)
    message(FATAL_ERROR
        "ANGLE prebuilt libraries not found in ${ANGLE_LIB_DIR}\n"
        "  -> Build ANGLE for Android first (see ARCHITECTURE.md) and copy\n"
        "     libEGL.so + libGLESv2.so into src/main/jniLibs/${ANDROID_ABI}/\n"
        "  -> Or run: ./gradlew assembleDebug -PanglePrebuilt=<dir>")
endif()

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

add_library(angledpaper SHARED
    egl_platform.cpp
    renderer.cpp
)

target_include_directories(angledpaper PRIVATE ${CMAKE_CURRENT_LIST_DIR})

target_compile_options(angledpaper PRIVATE -Wall -Wextra -Wno-unused-parameter)

target_link_libraries(angledpaper PRIVATE
    ${ANGLE_EGL}
    ${ANGLE_GLESv2}
    ${ANDROID_LIB}
    ${LOG_LIB}
)
