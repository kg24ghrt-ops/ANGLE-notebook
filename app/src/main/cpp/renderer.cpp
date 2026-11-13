#include "renderer.h"

#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <cstring>

#define TAG "AngledPaper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

Renderer::Renderer() = default;
Renderer::~Renderer() { destroySurface(); }

std::string Renderer::loadShaderSource(AAssetManager* mgr, const char* filename) {
    AAsset* asset = AAssetManager_open(mgr, filename, AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("Asset not found: assets/shaders/%s", filename);
        return "";
    }
    size_t length = AAsset_getLength(asset);
    std::string source;
    source.resize(length + 1);
    size_t read = AAsset_read(asset, &source[0], length);
    AAsset_close(asset);
    if (read != length) return "";
    source.resize(length);
    source.push_back('\0');
    return source;
}

GLuint Renderer::compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint ok = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        GLint logLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &logLen);
        std::string log(logLen > 0 ? logLen : 1, '\0');
        glGetShaderInfoLog(shader, logLen, nullptr, &log[0]);
        status_ = "GL: shader compile failed (" + std::string(type == GL_VERTEX_SHADER ? "vertex" : "fragment") +
                  "): " + log;
        LOGE("%s", status_.c_str());
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

bool Renderer::buildProgram() {
    std::string vertSrc = loadShaderSource(assetManager_, "shaders/paper.vert");
    std::string fragSrc = loadShaderSource(assetManager_, "shaders/paper.frag");
    if (vertSrc.empty() || fragSrc.empty()) {
        status_ = "GL: missing shader assets under assets/shaders/";
        LOGE("%s", status_.c_str());
        return false;
    }

    GLuint vert = compileShader(GL_VERTEX_SHADER, vertSrc.c_str());
    GLuint frag = compileShader(GL_FRAGMENT_SHADER, fragSrc.c_str());
    if (!vert || !frag) return false;

    program_ = glCreateProgram();
    glAttachShader(program_, vert);
    glAttachShader(program_, frag);
    glBindAttribLocation(program_, 0, "a_position");
    glLinkProgram(program_);

    GLint ok = 0;
    glGetProgramiv(program_, GL_LINK_STATUS, &ok);
    if (!ok) {
        GLint logLen = 0;
        glGetProgramiv(program_, GL_INFO_LOG_LENGTH, &logLen);
        std::string log(logLen > 0 ? logLen : 1, '\0');
        glGetProgramInfoLog(program_, logLen, nullptr, &log[0]);
        status_ = "GL: program link failed: " + log;
        LOGE("%s", status_.c_str());
        glDeleteProgram(program_);
        program_ = 0;
        return false;
    }

    glDeleteShader(vert);
    glDeleteShader(frag);

    locResolution_ = glGetUniformLocation(program_, "u_resolution");
    locPxPerMm_ = glGetUniformLocation(program_, "u_pxPerMm");
    locSpacingMm_ = glGetUniformLocation(program_, "u_ruleSpacingMm");
    locMarginMm_ = glGetUniformLocation(program_, "u_marginMm");

    // Fullscreen quad in clip space.
    const float positions[] = {
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f,
    };
    glGenBuffers(1, &vbo_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glBufferData(GL_ARRAY_BUFFER, sizeof(positions), positions, GL_STATIC_DRAW);

    glGenVertexArrays(1, &vao_);
    glBindVertexArray(vao_);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, nullptr);
    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    checkGlError("buildProgram");
    return true;
}

void Renderer::checkGlError(const char* call) {
    GLenum err = glGetError();
    if (err != GL_NO_ERROR) {
        status_ = "GL: error 0x" + std::to_string(err) + " after " + call;
        LOGE("%s", status_.c_str());
    }
}

void Renderer::updateUniforms() {
    glUniform2f(locResolution_, (float)width_, (float)height_);
    glUniform1f(locPxPerMm_, pxPerMm_);
    glUniform1f(locSpacingMm_, spacingMm_);
    glUniform1f(locMarginMm_, marginMm_);
}

bool Renderer::init(AAssetManager* assetManager) {
    assetManager_ = assetManager;
    return true;
}

bool Renderer::createSurface(ANativeWindow* window, int width, int height) {
    if (!egl_.initDisplay()) {
        status_ = egl_.status();
        return false;
    }
    if (!egl_.createSurface(window, width, height)) {
        status_ = egl_.status();
        return false;
    }

    width_ = width;
    height_ = height;

    if (!buildProgram()) {
        return false;
    }

    glViewport(0, 0, width_, height_);
    checkGlError("createSurface");
    initialized_ = true;
    status_ = "OK";
    LOGI("Renderer ready %dx%d", width, height);
    return true;
}

void Renderer::destroySurface() {
    initialized_ = false;
    program_ = 0;
    vbo_ = 0;
    vao_ = 0;
    egl_.release();
    status_ = egl_.status().empty() ? std::string("surface released") : egl_.status();
}

void Renderer::changeSurface(int width, int height) {
    if (!egl_.display()) return;
    width_ = width;
    height_ = height;
    glViewport(0, 0, width_, height_);
    checkGlError("changeSurface");
}

void Renderer::renderFrame() {
    if (!initialized_ || program_ == 0) return;
    egl_.makeCurrent();

    glClearColor(0.996f, 0.976f, 0.890f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(program_);
    glBindVertexArray(vao_);
    updateUniforms();
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindVertexArray(0);
    glUseProgram(0);

    checkGlError("renderFrame");

    egl_.swapBuffers();
    // Preserve any EGL/GL error already recorded; otherwise report OK.
    if (status_.find("EGL:") == std::string::npos &&
        status_.find("GL:") == std::string::npos) {
        status_ = "OK";
    }
}

std::vector<uint8_t> Renderer::exportFrameArgb() {
    if (!initialized_ || program_ == 0) {
        status_ = "Export: renderer not initialised";
        return {};
    }
    egl_.makeCurrent();
    checkGlError("exportFrameArgb pre");

    GLint vp[4];
    glGetIntegerv(GL_VIEWPORT, vp);
    int w = vp[2], h = vp[3];
    if (w <= 0 || h <= 0) {
        status_ = "Export: invalid viewport";
        return {};
    }

    size_t rowBytes = (size_t)w * 4;
    std::vector<uint8_t> rgba(rowBytes * h);
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
    checkGlError("glReadPixels");

    // Convert GL RGBA (bottom row first) to Android ARGB8888 (top row first).
    std::vector<uint8_t> argb(rowBytes * h);
    for (int y = 0; y < h; ++y) {
        const uint8_t* srcRow = rgba.data() + (size_t)(h - 1 - y) * rowBytes;
        uint8_t* dstRow = argb.data() + (size_t)y * rowBytes;
        for (int x = 0; x < w; ++x) {
            const uint8_t* px = srcRow + (size_t)x * 4;
            // RGBA -> ARGB
            dstRow[(size_t)x * 4 + 0] = px[3]; // A
            dstRow[(size_t)x * 4 + 1] = px[0]; // R
            dstRow[(size_t)x * 4 + 2] = px[1]; // G
            dstRow[(size_t)x * 4 + 3] = px[2]; // B
        }
    }
    status_ = "OK";
    return argb;
}

void Renderer::setPageParams(float pxPerMm, float spacingMm, float marginMm) {
    pxPerMm_ = pxPerMm;
    spacingMm_ = spacingMm;
    marginMm_ = marginMm;
}

// ---------------------------------------------------------------------------
// JNI entry points
// ---------------------------------------------------------------------------

#include <jni.h>

static Renderer* gRenderer() {    static Renderer instance;
    return &instance;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_angledpaper_NativeRenderer_nativeInit(
        JNIEnv* env, jobject, jobject assetManager) {
    gRenderer()->init(AAssetManager_fromJava(env, assetManager));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_angledpaper_NativeRenderer_nativeSurfaceCreated(
        JNIEnv* env, jobject, jobject surface, jint width, jint height) {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("EGL: ANativeWindow_fromSurface returned null");
        return;
    }
    bool ok = gRenderer()->createSurface(window, (int)width, (int)height);
    if (!ok) {
        // createSurface already released the window on failure; the next
        // surfaceDestroyed/destroySurface run finishes EGL teardown.
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_angledpaper_NativeRenderer_nativeSurfaceChanged(
        JNIEnv* env, jobject, jint width, jint height) {
    gRenderer()->changeSurface((int)width, (int)height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_angledpaper_NativeRenderer_nativeSurfaceDestroyed(
        JNIEnv* jenv, jobject) {
    gRenderer()->destroySurface();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_angledpaper_NativeRenderer_nativeRenderFrame(
        JNIEnv*, jobject) {
    gRenderer()->renderFrame();
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_angledpaper_NativeRenderer_nativeExportPng(
        JNIEnv* env, jobject) {
    std::vector<uint8_t> bytes = gRenderer()->exportFrameArgb();
    if (bytes.empty()) return nullptr;
    jbyteArray arr = env->NewByteArray((jsize)bytes.size());
    env->SetByteArrayRegion(arr, 0, (jsize)bytes.size(),
                            reinterpret_cast<const jbyte*>(bytes.data()));
    return arr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_angledpaper_NativeRenderer_nativeStatus(
        JNIEnv* env, jobject) {
    return env->NewStringUTF(gRenderer()->status().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_angledpaper_NativeRenderer_nativeSetPageParams(
        JNIEnv*, jobject, jfloat pxPerMm, jfloat spacingMm, jfloat marginMm) {
    gRenderer()->setPageParams((float)pxPerMm, (float)spacingMm, (float)marginMm);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_angledpaper_NativeRenderer_nativeDestroy(
        JNIEnv*, jobject) {
    gRenderer()->destroySurface();
}
