# AngledPaper

Native Android school-notebook / lined-paper prototype (hyper-realistic ruled
paper, cream base, fibre noise, optional ink later).

**This is a fully native app — not a WebView, not Chrome, not a TWA, not a web
app wrapper.** Kotlin UI shell (`TextureView`) + **Google Filament** rendering
engine (prebuilt AAR from Maven Central). No WebView, no WebGPU, no WGSL, no
browser toolchain.

## Milestone 1 (current)

- Cream ruled paper base rendered by Filament (clear color = cream).
- `TextureView` + Filament `Engine`/`Renderer`/`Scene`/`View`/`Camera`/`SwapChain`.
- PNG export via `TextureView.bitmap` → PNG → FileProvider share.
- Fail-loud status UI — no blank "success" screen.

## Architecture change (Filament vs ANGLE)

The original design used ANGLE (in-app GLES translation layer) with raw GLES
3.x/EGL from C++. ANGLE from source required a heavy CI build (depot_tools,
GN, Ninja) that was unreliable on self-hosted runners. We switched to
**Google Filament** as a prebuilt Maven AAR (`com.google.android.filament:
filament-android`), which:

- Ships as a prebuilt AAR — no native source compilation in CI.
- Picks the best backend per device (OpenGL ES 3.0+, Vulkan).
- Provides a Kotlin-friendly API (`Engine`, `Renderer`, `Scene`, `View`,
  `Camera`, `SwapChain`, `UiHelper`).

Trade-off: app code speaks Filament's API rather than raw GLES/EGL. Shaders
become Filament materials (`.mat`/filamat) rather than raw GLSL ES 3.00.

## Build

CI builds the debug APK with Gradle (Filament AARs resolved from Maven
Central). No ANGLE/Depot_Tools build needed.

Locally (for reference only — do not run builds here per AGENTS.md):

```bash
./gradlew assembleDebug
```

## Project layout

- `app/src/main/kotlin/com/example/angledpaper/MainActivity.kt` — Filament
  Android API, TextureView lifecycle, PNG export, status UI
- `app/src/main/res/layout/activity_main.xml` — TextureView + status + export
- `app/src/main/res/values/` — strings, colors, themes
- `app/src/main/AndroidManifest.xml` — FileProvider + MainActivity
- `ARCHITECTURE.md` — Filament init diagram, draw loop, export path
- `.github/workflows/android-build.yml` — simple Gradle CI
