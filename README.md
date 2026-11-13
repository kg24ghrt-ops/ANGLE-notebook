# AngledPaper

Native Android school-notebook / lined-paper prototype (hyper-realistic ruled
paper, cream base, fibre noise, optional ink later).

**This is a fully native app — not a WebView, not Chrome, not a TWA, not a web
app wrapper.** Kotlin UI shell (`SurfaceView`) + C++17 renderer talking OpenGL
ES 3.x through **ANGLE** (in-process GLES backend, translated to Vulkan on
modern devices).

## Milestone 1 (current)

- Cream ruled paper (Asia B5–style ruling, DPI-aware mm spacing) drawn with a
  single GLSL ES 3.00 pass.
- Correct viewport on rotate/resize.
- Export current frame to PNG (`glReadPixels` → Bitmap → share via FileProvider).
- Fail-loud EGL/GL error UI — no blank "success" screen.

## Build

Local builds are intentionally not run; compilation happens on GitHub Actions
(see `.github/workflows/android-build.yml`). The workflow builds ANGLE for
Android `arm64-v8a`, copies `libEGL.so`/`libGLESv2.so` into
`app/src/main/jniLibs/arm64-v8a/`, then assembles the debug APK.

CI artifacts: `android-debug-apk` (14-day retention).

Locally (for reference only — do not run builds here per AGENTS.md):

```bash
# Requires prebuilt ANGLE libs in app/src/main/jniLibs/arm64-v8a/
# (produced by CI). Then:
./gradlew assembleDebug
```

With armeabi-v7a: `./gradlew assembleDebug -PangleAbiV7a=true`

## Project layout

- `app/src/main/kotlin/com/example/angledpaper/` — MainActivity + NativeRenderer JNI bridge
- `app/src/main/cpp/` — EGL/ANGLE platform, GLES renderer, JNI entry points
- `app/src/main/assets/shaders/` — `paper.vert`, `paper.frag` (GLSL ES 3.00)
- `ARCHITECTURE.md` — init diagram, draw loop, uniforms, ANGLE procurement
