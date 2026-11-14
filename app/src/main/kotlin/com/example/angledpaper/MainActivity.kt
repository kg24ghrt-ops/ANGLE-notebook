package com.example.angledpaper

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.angledpaper.databinding.ActivityMainBinding
import com.google.android.filament.Camera
import com.google.android.filament.Color
import com.google.android.filament.EntityManager
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.UiHelper
import com.google.android.filament.View

class MainActivity : AppCompatActivity(), Choreographer.FrameCallback {

    companion object {
        // Loads the Filament JNI library. Must run before any Filament call.
        init {
            Filament.init()
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var uiHelper: UiHelper
    private lateinit var textureView: TextureView
    private lateinit var statusView: TextView
    private lateinit var exportButton: Button

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var cameraEntity: Long = 0
    private var swapChain: SwapChain? = null

    private var width = 0
    private var height = 0
    private var rendering = false
    private var lastStatus = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        textureView = TextureView(this)
        textureView.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        binding.root.addView(textureView, 0)

        statusView = binding.status
        exportButton = binding.export

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.setRenderCallback(object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                val eng = engine ?: return
                swapChain = eng.createSwapChain(surface)
                val w = textureView.width
                val h = textureView.height
                if (w > 0 && h > 0) {
                    width = w
                    height = h
                    setupScene(w, h)
                    rendering = true
                    Choreographer.getInstance().postFrameCallback(this@MainActivity)
                }
            }

            override fun onDetachedFromSurface() {
                rendering = false
                Choreographer.getInstance().removeFrameCallback(this@MainActivity)
                val eng = engine ?: return
                swapChain?.let { eng.destroySwapChain(it) }
                swapChain = null
            }

            override fun onResized(w: Int, h: Int) {
                width = w
                height = h
            }
        })

        uiHelper.attachTo(textureView)

        engine = Engine.create()
        renderer = engine?.createRenderer()
        scene = engine?.createScene()
        view = engine?.createView()

        val entityManager = EntityManager.get()
        cameraEntity = entityManager.create()
        camera = engine?.createCamera(cameraEntity)

        view?.camera = camera
        view?.scene = scene

        // Cream paper base clear color.
        renderer?.setClearColor(Color(0.996f, 0.976f, 0.890f, 1.0f))

        exportButton.setOnClickListener {
            exportPng()
        }

        pushStatus()
    }

    override fun onResume() {
        super.onResume()
        if (rendering && uiHelper.isReadyToRender) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onPause() {
        super.onPause()
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!rendering || !uiHelper.isReadyToRender) return
        renderFrame()
        pushStatus()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun setupScene(w: Int, h: Int) {
        camera?.setProjection(
            Camera.Projection.ORTHO,
            0f, 1f,
            0f, 1f,
            -1f, 1f
        )
        camera?.lookAt(
            0f, 0f, 1f,
            0f, 0f, 0f,
            0f, 1f, 0f
        )
    }

    private fun renderFrame() {
        val sc = swapChain ?: return
        val rend = renderer ?: return
        if (!rend.beginFrame(sc)) return
        rend.render(view!!)
        rend.endFrame()
    }

    private fun exportPng() {
        val bitmap = textureView.bitmap ?: run {
            statusView.text = getString(R.string.msg_export_failed)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.status_error))
            return
        }

        val file = java.io.File(cacheDir, "page_${System.currentTimeMillis()}.png")
        try {
            val out = file.outputStream()
            try {
                val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                if (!success) throw Exception("PNG compress failed")
            } finally {
                out.close()
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "${packageName}.provider", file)
            val intent = android.content.Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, null)
            if (chooser.resolveActivity(packageManager) != null) {
                startActivity(chooser)
            }
            statusView.text = getString(R.string.msg_export_saved, file.absolutePath)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.status_ok))
        } catch (e: Exception) {
            statusView.text = getString(R.string.msg_export_failed)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.status_error))
        }
    }

    private fun pushStatus() {
        val ready = engine != null && renderer != null && swapChain != null
        val status = if (ready) "OK" else ""
        if (status == lastStatus) return
        lastStatus = status
        statusView.text = if (status.isEmpty()) getString(R.string.status_ready) else status
        statusView.setTextColor(
            ContextCompat.getColor(
                this,
                if (status.isEmpty()) R.color.status_error else R.color.status_ok
            )
        )
    }

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(this)
        rendering = false
        uiHelper.detach()
        swapChain?.let { engine?.destroySwapChain(it) }
        camera?.let { engine?.destroyCamera(it) }
        val entityManager = EntityManager.get()
        entityManager.destroy(cameraEntity)
        view?.let { engine?.destroyView(it) }
        scene?.let { engine?.destroyScene(it) }
        renderer?.let { engine?.destroyRenderer(it) }
        engine?.let { Engine.destroy(it) }
        engine = null
        renderer = null
        scene = null
        view = null
        camera = null
        swapChain = null
        super.onDestroy()
    }
}
