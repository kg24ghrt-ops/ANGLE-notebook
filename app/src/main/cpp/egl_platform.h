#ifndef ANGLEPAPER_EGL_PLATFORM_H
#define ANGLEPAPER_EGL_PLATFORM_H

#include <EGL/egl.h>
#include <android/native_window.h>
#include <string>

// Thin wrapper over an ANGLE EGL display/context/window-surface.
// App code speaks the stock GLES 3.x API; ANGLE translates it underneath.
class EglPlatform {
public:
    EglPlatform();
    ~EglPlatform();

    // Initialise EGL display + context. Returns false (and records a message)
    // on any failure -- callers must surface this to the UI, never silence it.
    bool initDisplay();

    // Create a window surface for ANativeWindow and make it current.
    bool createSurface(ANativeWindow* window, int width, int height);

    void makeCurrent();
    void swapBuffers();
    void releaseSurface();
    void release();

    int width() const { return width_; }
    int height() const { return height_; }
    const std::string& status() const { return status_; }
    EGLDisplay display() const { return display_; }
    EGLContext context() const { return context_; }

private:
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLConfig config_ = EGL_NO_CONFIG_KHR;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface surface_ = EGL_NO_SURFACE;
    ANativeWindow* window_ = nullptr;
    int width_ = 0;
    int height_ = 0;
    std::string status_;

    bool chooseConfig();
    std::string eglErrorString() const;
};

#endif // ANGLEPAPER_EGL_PLATFORM_H
