package com.example.angledpaper

import android.content.Context
import android.content.res.AssetManager

/**
 * Thin JNI bridge to the native ANGLE renderer.
 *
 * Surface area (all calls from MainActivity):
 *   init(assetManager)         one-time; loads nothing on the GL side
 *   surfaceCreated(surface, w, h)  EGL + GLES context via ANGLE, compile shaders
 *   surfaceChanged(w, h)        viewport / resize
 *   surfaceDestroyed()          release EGL surface/context
 *   renderFrame()               one ruled-paper frame + present
 *   exportPng(): ByteArray?     readback (glReadPixels), ARGB top-row first
 *   status(): String            last EGL/GL status or error reason
 *   setPageParams(pxPerMm, spacingMm, marginMm) ruler uniforms
 *   destroy()                   tear down native instance
 */
class NativeRenderer {

    external fun init(assetManager: AssetManager): Unit
    external fun surfaceCreated(surface: android.view.Surface, width: Int, height: Int): Unit
    external fun surfaceChanged(width: Int, height: Int): Unit
    external fun surfaceDestroyed(): Unit
    external fun renderFrame(): Unit
    external fun exportPng(): ByteArray?
    external fun status(): String
    external fun setPageParams(pxPerMm: Float, spacingMm: Float, marginMm: Float): Unit
    external fun destroy(): Unit

    companion object {
        init {
            System.loadLibrary("angledpaper")
        }
    }
}
