# AngledPaper — Architecture (Filament)

Native Android ruled-paper notebook prototype using **Google Filament** as the
rendering engine (prebuilt Maven AAR). No WebView, no WebGPU, no WGSL, no
browser toolchain.

## Stack

| Layer | Choice |
|-------|--------|
| UI shell | Kotlin, `TextureView`, Filament `UiHelper` |
| Rendering engine | Google Filament (prebuilt AAR from Maven Central: `com.google.android.filament:filament-android:1.77.3`) |
| Material compiler | `filament-android:filamat-android:1.77.3` (runtime material builder) |
| minSdk / targetSdk | 26 / 36 |
| ABIs | `arm64-v8a` (add `armeabi-v7a` only if required) |

## Why Filament instead of ANGLE

The original design used ANGLE (in-app GLES → Vulkan translation) with raw
GLES 3.x/EGL from C++. Building ANGLE from source in CI required depot_tools,
GN, and Ninja, which proved unreliable on self-hosted runners (missing
`python`, `/bin/bash`, etc.). Filament ships as a prebuilt AAR, so CI is just
a Gradle build — no native source compilation.

Trade-off: app code speaks Filament's API rather than raw GLES/EGL. Shaders
become Filament materials rather than raw GLSL ES 3.00.

## Filament init diagram

```
Kotlin MainActivity
  ├─ Filament.init()                    (one-time, loads JNI library)
  ├─ TextureView + UiHelper
  │     uiHelper.attachTo(textureView)
  │     uiHelper.renderCallback = RenderCallback {
  │         onNativeWindowChanged(surface)
  │         onRenderCallback(frameTimeNanos)
  │         onDetachedFromSurface()
  │     }
  ├─ Engine.create()                    (Filament engine)
  ├─ Renderer = engine.createRenderer()
  ├─ Scene = engine.createScene()
  ├─ View = engine.createView()
  ├─ Camera = engine.createCamera()
  └─ SwapChain = engine.createSwapChain(surface)   (on surface created)

Render loop (UiHelper.RenderCallback.onRenderCallback):
  renderer.beginFrame(swapChain, frameTimeNanos)
  renderer.render(swapChain, view)
  renderer.endFrame(swapChain)

PNG export (export button):
  textureView.bitmap                      (capture current frame)
  Bitmap → PNG (pure Kotlin encoder)
  FileProvider share (ACTION_SEND)
```

## Draw loop

1. `UiHelper` drives the frame callback via Choreographer.
2. `onRenderCallback` → `renderer.beginFrame` → `renderer.render` → `renderer.endFrame`.
3. Camera is orthographic (0..1 screen space).
4. Clear color = cream paper base (`0.996, 0.976, 0.890`).
5. Paused in `onPause`/`onDetachedFromSurface`; resumed on surface creation.

## Shader / material

Milestone 1 uses Filament's clear color for the cream base. Custom materials
(for fibre noise, horizontal rules, margin line) use `filamat-android` for
runtime material compilation — planned for milestone 2.

## PNG export

1. `textureView.bitmap` captures the current frame.
2. Pure Kotlin PNG encoder (uncompressed IDAT, valid PNG).
3. Saved to `cacheDir` and shared via `FileProvider` + `ACTION_SEND`.

## Failure behaviour

- Filament init failure → status text shows error (engine/renderer/swapChain null).
- Export failure → red status text, no crash.
- App never shows a blank "ready" page while native status is not OK.

## JNI surface area

None in app code — Filament's AAR handles JNI internally. The Kotlin API calls
into Filament's JNI bindings transparently.
