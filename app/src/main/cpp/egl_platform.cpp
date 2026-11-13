#include "egl_platform.h"

#include <android/log.h>
#include <string.h>

#define TAG "AngledPaper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

EglPlatform::EglPlatform() = default;

EglPlatform::~EglPlatform() {
    release();
}

std::string EglPlatform::eglErrorString() const {
    EGLint err = eglGetError();
    switch (err) {
        case EGL_SUCCESS: return "EGL_SUCCESS";
        case EGL_NOT_INITIALIZED: return "EGL_NOT_INITIALIZED";
        case EGL_BAD_ACCESS: return "EGL_BAD_ACCESS";
        case EGL_BAD_ALLOC: return "EGL_BAD_ALLOC";
        case EGL_BAD_ATTRIBUTE: return "EGL_BAD_ATTRIBUTE";
        case EGL_BAD_CONTEXT: return "EGL_BAD_CONTEXT";
        case EGL_BAD_CONFIG: return "EGL_BAD_CONFIG";
        case EGL_BAD_CURRENT_SURFACE: return "EGL_BAD_CURRENT_SURFACE";
        case EGL_BAD_DISPLAY: return "EGL_BAD_DISPLAY";
        case EGL_BAD_SURFACE: return "EGL_BAD_SURFACE";
        case EGL_BAD_MATCH: return "EGL_BAD_MATCH";
        case EGL_BAD_PARAMETER: return "EGL_BAD_PARAMETER";
        case EGL_BAD_NATIVE_PIXMAP: return "EGL_BAD_NATIVE_PIXMAP";
        case EGL_BAD_NATIVE_WINDOW: return "EGL_BAD_NATIVE_WINDOW";
        case EGL_CONTEXT_LOST: return "EGL_CONTEXT_LOST";
        default: return "EGL_ERROR_" + std::to_string(err);
    }
}

bool EglPlatform::initDisplay() {
    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY) {
        status_ = "EGL: eglGetDisplay failed (" + eglErrorString() + ")";
        LOGE("%s", status_.c_str());
        return false;
    }

    EGLint major = 0, minor = 0;
    if (!eglInitialize(display_, &major, &minor)) {
        status_ = "EGL: eglInitialize failed (" + eglErrorString() + ")";
        LOGE("%s", status_.c_str());
        return false;
    }
    LOGI("EGL initialized %d.%d", major, minor);

    if (!eglBindAPI(EGL_OPENGL_ES_API)) {
        status_ = "EGL: eglBindAPI(OPENGL_ES_API) failed (" + eglErrorString() + ")";
        LOGE("%s", status_.c_str());
        return false;
    }

    if (!chooseConfig()) {
        LOGE("%s", status_.c_str());
        return false;
    }

    const EGLint ctxAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE
    };
    context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, ctxAttribs);
    if (context_ == EGL_NO_CONTEXT) {
        status_ = "EGL: eglCreateContext(GLES 3.0) failed (" + eglErrorString() +
                  ") -- device may lack OpenGL ES 3.0";
        LOGE("%s", status_.c_str());
        return false;
    }

    status_ = "OK";
    return true;
}

bool EglPlatform::chooseConfig() {
    const EGLint attribs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 0,
        EGL_STENCIL_SIZE, 0,
        EGL_NONE
    };
    EGLint count = 0;
    if (!eglChooseConfig(display_, attribs, &config_, 1, &count) || count == 0) {
        status_ = "EGL: no suitable RGBA8/GLES3 framebuffer config (" + eglErrorString() + ")";
        return false;
    }
    return true;
}

bool EglPlatform::createSurface(ANativeWindow* window, int width, int height) {
    releaseSurface();
    window_ = window;
    width_ = width;
    height_ = height;

    surface_ = eglCreateWindowSurface(display_, config_, window_, nullptr);
    if (surface_ == EGL_NO_SURFACE) {
        ANativeWindow_release(window_);
        window_ = nullptr;
        status_ = "EGL: eglCreateWindowSurface failed (" + eglErrorString() + ")";
        LOGE("%s", status_.c_str());
        return false;
    }

    window_ = window;
    width_ = width;
    height_ = height;

    makeCurrent();
    status_ = "OK";
    LOGI("EGL window surface %dx%d", width, height);
    return true;
}

void EglPlatform::makeCurrent() {
    if (display_ == EGL_NO_DISPLAY) return;
    eglMakeCurrent(display_, surface_, surface_, context_);
}

void EglPlatform::swapBuffers() {
    if (display_ == EGL_NO_DISPLAY || surface_ == EGL_NO_SURFACE) return;
    EGLBoolean ok = eglSwapBuffers(display_, surface_);
    if (!ok) {
        status_ = "EGL: eglSwapBuffers failed (" + eglErrorString() + ")";
        LOGE("%s", status_.c_str());
    }
}

void EglPlatform::releaseSurface() {
    if (display_ != EGL_NO_DISPLAY && surface_ != EGL_NO_SURFACE) {
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(display_, surface_);
        surface_ = EGL_NO_SURFACE;
    }
    if (window_ != nullptr) {
        ANativeWindow_release(window_);
        window_ = nullptr;
    }
    width_ = 0;
    height_ = 0;
}

void EglPlatform::release() {
    releaseSurface();
    if (context_ != EGL_NO_CONTEXT) {
        eglDestroyContext(display_, context_);
        context_ = EGL_NO_CONTEXT;
    }
    if (display_ != EGL_NO_DISPLAY) {
        eglTerminate(display_);
        display_ = EGL_NO_DISPLAY;
    }
}
