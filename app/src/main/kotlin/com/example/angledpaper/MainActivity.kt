package com.example.angledpaper

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.PNG
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.angledpaper.databinding.ActivityMainBinding
import com.google.android.filament.Camera
import com.google.android.filament.Color
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.UiHelper
import com.google.android.filament.View

class MainActivity : AppCompatActivity() {

    companion object {
        // Loads the Filament JNI library. Must run before any Filament call.
        init {
            Filament.init()
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var uiHelper: UiHelper
    private lateinit var textureView: TextureView
    private lateinit var statusView: android.widget.TextView
    private lateinit var exportButton: android.widget.Button

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null

    private var width = 0
    private var height = 0
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
        uiHelper.renderCallback = object : UiHelper.RenderCallback {
            override fun onRenderCallback(frameTimeNanos: Long) {
                renderFrame()
                pushStatus()
            }

            override fun onNativeWindowChanged(surface: Surface) {
                val eng = engine ?: return
                swapChain = eng.createSwapChain(surface)
                val w = textureView.width
                val h = textureView.height
                if (w > 0 && h > 0) {
                    width = w
                    height = h
                    setupScene(w, h)
                }
            }

            override fun onDetachedFromSurface() {
                val eng = engine ?: return
                swapChain?.let { eng.destroySwapChain(it) }
                swapChain = null
            }
        }

        uiHelper.attachTo(textureView)

        engine = Engine.create()
        renderer = engine?.createRenderer()
        scene = engine?.createScene()
        view = engine?.createView()
        camera = engine?.createCamera()

        view?.camera = camera
        view?.scene = scene

        exportButton.setOnClickListener {
            exportPng()
        }

        pushStatus()
    }

    private fun setupScene(w: Int, h: Int) {
        // Configure camera for orthographic screen-space rendering (0..1 in X and Y).
        camera?.setProjection(
            Camera.Projection.ORTHOGRAPHIC,
            0f, 1f,
            0f, 1f,
            -1f, 1f
        )
        camera?.lookAt(
            0f, 0f, 1f,
            0f, 0f, 0f,
            0f, 1f, 0f
        )

        // Cream paper base clear color (matches Filament sample pattern).
        renderer?.clearOptions = renderer?.clearOptions?.apply {
            this.clear = true
            this.color = Color(0.996f, 0.976f, 0.890f, 1.0f)
        }
    }

    private fun renderFrame() {
        val sc = swapChain ?: return
        val rend = renderer ?: return
        val frameTime = System.nanoTime()
        rend.beginFrame(sc, frameTime)
        rend.render(sc, view!!)
        rend.endFrame(sc)
    }

    private fun exportPng() {
        val bitmap = textureView.bitmap ?: run {
            statusView.text = getString(R.string.msg_export_failed)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.status_error))
            return
        }

        val bytes = BitmapToPng.encode(bitmap)
        if (bytes.isEmpty()) {
            statusView.text = getString(R.string.msg_export_failed)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.status_error))
            return
        }

        val file = java.io.File(cacheDir, "page_${System.currentTimeMillis()}.png")
        try {
            file.outputStream().use { out -> out.write(bytes) }
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
        uiHelper.detachFromView()
        swapChain?.let { engine?.destroySwapChain(it) }
        camera?.let { engine?.destroyCamera(it) }
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

// ---------------------------------------------------------------------------
// Bitmap -> PNG encoder (pure Kotlin, no native code needed)
// ---------------------------------------------------------------------------
object BitmapToPng {
    fun encode(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val rowBytes = ((width * 8 + 31) / 32) * 4
        val rawSize = rowBytes * height + height  // +1 filter byte per row
        val headerSize = 33
        val output = ByteArray(headerSize + rawSize + 36)

        var pos = 0
        // PNG signature
        val sig = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        System.arraycopy(sig, 0, output, 0, 8)
        pos = 8

        // IHDR chunk
        pos = writeChunk(output, pos, "IHDR", byteArrayOf(
            (width shr 24).toByte(), (width shr 16).toByte(), (width shr 8).toByte(), width.toByte(),
            (height shr 24).toByte(), (height shr 16).toByte(), (height shr 8).toByte(), height.toByte(),
            8,  // bit depth
            6,  // color type: RGBA
            0,  // compression
            0,  // filter
            0   // interlace
        ))

        // IDAT chunk (raw, uncompressed - valid PNG, just larger)
        val idatContent = ByteArray(rawSize)
        var ip = 0
        for (y in 0 until height) {
            idatContent[ip++] = 0  // filter: none
            for (x in 0 until width) {
                val c = bitmap.getPixel(x, y)
                idatContent[ip++] = ((c ushr 16) and 0xff).toByte()  // R
                idatContent[ip++] = ((c ushr 8) and 0xff).toByte()   // G
                idatContent[ip++] = (c and 0xff).toByte()            // B
                idatContent[ip++] = ((c ushr 24) and 0xff).toByte()  // A
            }
        }
        pos = writeChunk(output, pos, "IDAT", idatContent)

        // IEND chunk
        pos = writeChunk(output, pos, "IEND", byteArrayOf())

        return output.copyOfRange(0, pos)
    }

    private fun writeChunk(out: ByteArray, pos: Int, type: String, data: ByteArray): Int {
        val len = data.size
        out[pos] = (len shr 24).toByte()
        out[pos + 1] = (len shr 16).toByte()
        out[pos + 2] = (len shr 8).toByte()
        out[pos + 3] = len.toByte()
        var crcPos = pos + 4
        System.arraycopy(type.toByteArray(Charsets.UTF_8), 0, out, crcPos, 4)
        crcPos += 4
        System.arraycopy(data, 0, out, crcPos, len)
        crcPos += len
        val crc = crc32((type.toByteArray(Charsets.UTF_8) + data))
        out[crcPos] = (crc shr 24).toByte()
        out[crcPos + 1] = (crc shr 16).toByte()
        out[crcPos + 2] = (crc shr 8).toByte()
        out[crcPos + 3] = crc.toByte()
        return crcPos + 4
    }

    private fun crc32(data: ByteArray): Int {
        var crc = 0xFFFFFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xff)
            for (i in 0 until 8) {
                if ((crc and 1) == 1) {
                    crc = (crc ushr 1) xor 0xEDB88320
                } else {
                    crc = crc ushr 1
                }
            }
        }
        return crc inv
    }
}
