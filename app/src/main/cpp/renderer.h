#ifndef ANGLEPAPER_RENDERER_H
#define ANGLEPAPER_RENDERER_H

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/asset_manager.h>
#include <android/native_window.h>
#include <cstdint>
#include <string>
#include <vector>

#include "egl_platform.h"

// Rules the paper: one fullscreen pass drawing cream paper + noise, horizontal
// ruling lines and a left margin line. All dimensions flow through uniforms so
// the Kotlin side can adapt to screen density and page template.
class Renderer {
public:
    Renderer();
    ~Renderer();

    // Call once from JNI with the AssetManager so GLSL sources can be read
    // from assets/shaders/. No GL calls are made here.
    bool init(AAssetManager* assetManager);

    // EGL surface lifecycle (wraps EglPlatform).
    bool createSurface(ANativeWindow* window, int width, int height);
    void destroySurface();
    void changeSurface(int width, int height);

    // Draw one frame and present it through ANGLE/EGL.
    void renderFrame();

    // Read back the current frame as ARGB8888 pixels, top row first.
    // Returns empty vector on failure; call status() for the reason.
    std::vector<uint8_t> exportFrameArgb();

    const EglPlatform& egl() const { return egl_; }
    const std::string& status() const { return status_; }

    // Page template uniforms (pixels per mm, ruling spacing in mm, margin in mm).
    void setPageParams(float pxPerMm, float spacingMm, float marginMm);

private:
    std::string loadShaderSource(AAssetManager* mgr, const char* filename);
    GLuint compileShader(GLenum type, const char* source);
    bool buildProgram();
    void checkGlError(const char* call);
    void updateUniforms();

    EglPlatform egl_;
    AAssetManager* assetManager_ = nullptr;

    GLuint program_ = 0;
    GLuint vbo_ = 0;
    GLuint vao_ = 0;

    GLint locResolution_ = -1;
    GLint locPxPerMm_ = -1;
    GLint locSpacingMm_ = -1;
    GLint locMarginMm_ = -1;

    int width_ = 0;
    int height_ = 0;
    float pxPerMm_ = 1.0f;
    float spacingMm_ = 8.0f;
    float marginMm_ = 20.0f;

    std::string status_;
    bool initialized_ = false;
};

#endif // ANGLEPAPER_RENDERER_H
