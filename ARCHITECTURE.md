# AngledPaper — Architecture

Native Android ruled-paper notebook prototype. App code speaks OpenGL ES 3.x
(EGL entry points); **ANGLE** is the in-process GLES implementation and
translates calls (typically to Vulkan) at runtime. No WebView, no WebGPU, no
WGSL, no browser toolchain.

## Stack

| Layer | Choice |
|-------|--------|
| UI shell | Kotlin, `SurfaceView` + `SurfaceHolder.Callback`, Choreographer-driven frame loop |
| Graphics backend | ANGLE (in-app): `libEGL.so` + `libGLESv2.so` packaged per ABI under `src/main/jniLibs/<abi>/` |
| Native | C++17, CMake via AGP `externalNativeBuild`, NDK r28 |
| Shaders | GLSL ES 3.00, loaded at runtime from `assets/shaders/` (`paper.vert`, `paper.frag`) |
| PNG export | `glReadPixels` → RGBA→ARGB swizzle → `Bitmap.compress(PNG)` → FileProvider share |
| minSdk / targetSdk | 26 / 36 |
| ABIs | `arm64-v8a` (add `armeabi-v7a` only if required via `-PangleAbiV7a=true`) |

## Build-time ANGLE procurement

ANGLE is **not** built locally by developers and is **not** checked in.
GitHub Actions builds ANGLE from source
(`chromium.googlesource.com/angle/angle`, pinned depth-1 clone) for Android
`arm64-v8a`, then copies `out/Release/**/libEGL.so` and `libGLESv2.so` into
`app/src/main/jniLibs/arm64-v8a/`. The Gradle CMake build links those prebuilt
shared libraries into `libangledpaper.so`. If the libraries are missing, CMake
fails loud with a message pointing at `ARCHITECTURE.md` / the CI workflow.

See `.github/workflows/android-build.yml` (job `build-angle` + `build-apk`).
ANGLE outputs are restored from `actions/cache` (`key: angle-arm64-v8a-v1`)
so the APK job does not rebuild ANGLE on every commit.

## EGL + ANGLE init (native/)

```
Kotlin MainActivity
  ├─ SurfaceHolder.Callback.surfaceCreated(surface)
  │      NativeRenderer.nativeInit(assets)            (asset manager, one-time)
  │      NativeRenderer.nativeSurfaceCreated(surface)
  │         ├─ ANativeWindow_fromSurface
  │         ├─ EglPlatform::initDisplay()
  │         │    eglGetDisplay(EGL_DEFAULT_DISPLAY)   <-- ANGLE libEGL
  │         │    eglInitialize
  │         │    eglBindAPI(EGL_OPENGL_ES_API)
  │         │    eglChooseConfig (RGBA8, EGL_OPENGL_ES3_BIT, window bit)
  │         │    eglCreateContext (EGL_CONTEXT_CLIENT_VERSION = 3)
  │         └─ eglCreateWindowSurface + eglMakeCurrent
  │         └─ Renderer::buildProgram()
  │              load assets/shaders/paper.vert / .frag (AAssetManager)
  │              glCompileShader / glLinkProgram, quad VAO/VBO
  └─ Choreographer.FrameCallback
         NativeRenderer.nativeRenderFrame()  ×60 fps
              glClear + fullscreen quad draw (paper.vert/paper.frag)
              eglSwapBuffers
         NativeRenderer.nativeExportPng()    (on export button)
              glReadPixels RGBA → flip + RGBA→ARGB → Bitmap.compress(PNG)
```

All EGL/GL failures are recorded in `Renderer::status_` and surfaced in the
Kotlin UI as a red status line. There is no silent fallback to a blank page.

## Draw loop

1. `glClear` with cream clear colour (the shader also writes cream, so clear
   and fragment base agree).
2. Use the compiled GLSL ES 3.00 program, bind the fullscreen quad VAO
   (clip-space triangle strip, 4 vertices), update uniforms, draw.
3. `eglSwapBuffers` presents through ANGLE.
4. Loop is paused in `onPause` / `surfaceDestroyed` and resumed in
   `onResume` / `surfaceCreated` so GPU work stops when the page is not visible.

Rotation/resize: activity declares
`configChanges="orientation\|screenSize\|keyboardHidden"`; `surfaceChanged`
resets the viewport to the new size and the shader receives
`u_resolution` each frame, so ruling spacing stays constant in millimetres.

## Shader uniforms (`assets/shaders/paper.frag`)

| Uniform | Type | Meaning | Example |
|---------|------|---------|---------|
| `u_resolution` | vec2 | viewport width/height in pixels | set per frame |
| `u_pxPerMm` | float | pixels per millimetre (`densityDpi / 25.4`) | ~26.5 at 675 dpi |
| `u_ruleSpacingMm` | float | ruling spacing in mm | 8.0 |
| `u_marginMm` | float | left margin line position in mm | 20.0 |

Output: cream base (`0.996, 0.976, 0.890`) + two octaves of hash noise for
fibre/grain, horizontal rules computed with `mod()` over `u_ruleSpacingMm`,
and a pinkish margin line at `u_marginMm`. All colour values RGBA8; no exotic
formats.

## JNI bridge (`com.example.angledpaper.NativeRenderer`)

| Method | Direction | Purpose |
|--------|-----------|---------|
| `init(assetManager)` | K → N | one-time; provides AAssetManager for shader loads |
| `surfaceCreated(surface, w, h)` | K → N | EGL display/context + window surface via ANGLE; compile shaders |
| `surfaceChanged(w, h)` | K → N | viewport resize on rotation/size change |
| `surfaceDestroyed()` | K → N | release EGL surface/context |
| `renderFrame()` | K → N | one paper frame + present |
| `exportPng(): ByteArray?` | N → K | readback; null on failure |
| `status(): String` | N → K | last EGL/GL status or error reason (shown in UI) |
| `setPageParams(pxPerMm, spacingMm, marginMm)` | K → N | ruling template uniforms |

## Failure behaviour

- EGL init failure → red status text with reason (e.g. `EGL: eglCreateContext(GLES 3.0) failed ...`).
- Shader compile/link failure → red status with compiler log.
- Readback/export failure → red status, no crash.
- The app never shows a blank "ready" page while native status is an error.

## Milestone 1 scope

Colored clear + ruled-paper fullscreen quad on screen, correct viewport on
rotate/resize, PNG export, error UI. Handwriting/strokes, multiple pages and
dirty-frame rendering are Milestone 2.
