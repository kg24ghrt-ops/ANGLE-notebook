package com.example.angledpaper

import android.content.Intent
import android.os.Bundle
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.angledpaper.databinding.ActivityMainBinding
import com.example.angledpaper.NativeRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, Choreographer.FrameCallback {

    private lateinit var binding: ActivityMainBinding
    private val renderer = NativeRenderer()
    private var rendering = false
    private var lastStatus = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )
            windowInsets
        }

        // One-time JNI setup: hand the asset manager over to the renderer so it
        // can load GLSL sources from assets/shaders at context creation time.
        renderer.init(assets)

        binding.surface.holder.addCallback(this)

        binding.export.setOnClickListener {
            exportPng()
        }

        // Page template: Asia B5–style ruling. DPI-aware so rules stay at the
        // same millimetre spacing on every screen density.
        val metrics = resources.displayMetrics
        val pxPerMm = metrics.densityDpi.toFloat() / 25.4f
        renderer.setPageParams(pxPerMm, 8.0f, 20.0f)
    }

    override fun onResume() {
        super.onResume()
        if (rendering) Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onPause() {
        Choreographer.getInstance().removeFrameCallback(this)
        super.onPause()
    }

    override fun onDestroy() {
        binding.surface.holder.removeCallback(this)
        renderer.destroy()
        super.onDestroy()
    }

    // SurfaceHolder.Callback -------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        val surface = binding.surface.holder.surface
        val w = surface.width
        val h = surface.height
        if (w > 0 && h > 0) {
            initRenderer(surface, w, h)
        }
        pushStatus()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        val surface = binding.surface.holder.surface
        if (w > 0 && h > 0 && !rendering) {
            initRenderer(surface, w, h)
        }
        renderer.surfaceChanged(w, h)
        pushStatus()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        rendering = false
        Choreographer.getInstance().removeFrameCallback(this)
        renderer.surfaceDestroyed()
        pushStatus()
    }

    private fun initRenderer(surface: Surface, w: Int, h: Int) {
        renderer.surfaceCreated(surface, w, h)
        rendering = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    // Choreographer.FrameCallback -------------------------------------------

    override fun doFrame(frameTimeNanos: Long) {
        if (!rendering) return
        renderer.renderFrame()
        pushStatus()
        Choreographer.getInstance().postFrameCallback(this)
    }

    // Export -----------------------------------------------------------------

    private fun exportPng() {
        val png = renderer.exportPng() ?: run {
            binding.status.text = getString(R.string.msg_export_failed)
            binding.status.setTextColor(ContextCompat.getColor(this, R.color.status_error))
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val file = java.io.File(cacheDir, "page_${System.currentTimeMillis()}.png")
            try {
                file.outputStream().use { out -> out.write(png) }
                val uri = FileProvider.getUriForFile(
                    this@MainActivity, "${packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, null)
                if (chooser.resolveActivity(this@MainActivity.packageManager) != null) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        this@MainActivity.startActivity(chooser)
                    }
                }
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.status.text = getString(R.string.msg_export_saved, file.absolutePath)
                    binding.status.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_ok))
                }
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.status.text = getString(R.string.msg_export_failed)
                    binding.status.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_error))
                }
            }
        }
    }

    private fun pushStatus() {
        val status = renderer.status()
        if (status == lastStatus) return
        lastStatus = status
        binding.status.text = if (status.isEmpty()) getString(R.string.status_ready) else status
        binding.status.setTextColor(
            ContextCompat.getColor(
                this,
                if (status.startsWith("Error") || status.contains("failed", ignoreCase = true))
                    R.color.status_error else R.color.status_ok
            )
        )
    }
}
