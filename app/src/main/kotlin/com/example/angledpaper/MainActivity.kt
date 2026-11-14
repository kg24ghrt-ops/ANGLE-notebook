package com.example.angledpaper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.angledpaper.databinding.ActivityMainBinding
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.EntityManager
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.RenderTarget
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.VertexBuffer
import com.google.android.filament.RenderableManager
import com.google.android.filament.android.UiHelper
import com.google.android.filament.filamat.BlendingMode
import com.google.android.filament.filamat.CullingMode
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialDomain
import com.google.android.filament.filamat.Platform
import com.google.android.filament.filamat.Shading
import com.google.android.filament.filamat.TargetApi
import com.google.android.filament.filamat.VertexDomain
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charsets
import java.util.zip.CRC32
import java.util.zip.Deflater

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

    private val mainHandler = Handler(Looper.getMainLooper())

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var cameraEntity: Int = 0
    private var swapChain: SwapChain? = null

    private var renderableManager: RenderableManager? = null
    private var quadEntity: Int = 0
    private var paperMaterial: Material? = null
    private var paperInstance: MaterialInstance? = null
    private var quadVb: VertexBuffer? = null
    private var quadIb: IndexBuffer? = null

    // Offscreen render target used for readPixels-based PNG export.
    private var exportTexture: Texture? = null
    private var exportRenderTarget: RenderTarget? = null

    private var width = 0
    private var height = 0
    private var rendering = false
    private var lastStatus = ""

    // Ruling parameters (pixels).
    private val lineSpacingPx = 30f
    private val lineWidthPx = 1.5f
    private val marginXPx = 32f
    private val topOffsetPx = 32f

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
                    updateSurface(w, h)
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
                updateSurface(w, h)
            }
        })

        uiHelper.attachTo(textureView)

        engine = Engine.create()
        renderer = engine?.createRenderer()
        scene = engine?.createScene()
        view = engine?.createView()

        renderableManager = engine?.getRenderableManager()

        val entityManager = EntityManager.get()
        cameraEntity = entityManager.create()
        camera = engine?.createCamera(cameraEntity)

        view?.camera = camera
        view?.scene = scene

        // Cream paper base clear color.
        renderer?.setClearOptions(
            Renderer.ClearOptions().apply {
                clearColor = doubleArrayOf(0.996, 0.976, 0.890, 1.0)
                clear = true
            }
        )

        buildPaperMaterial()
        updateSurface(textureView.width, textureView.height)

        exportButton.setOnClickListener {
            exportPng()
        }

        pushStatus()
    }

    override fun onResume() {
        super.onResume()
        if (rendering && uiHelper.isReadyToRender()) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onPause() {
        super.onPause()
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!rendering || !uiHelper.isReadyToRender()) return
        renderFrame(frameTimeNanos)
        pushStatus()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun updateSurface(w: Int, h: Int) {
        width = w
        height = h
        view?.viewport = Viewport(0, 0, w, h)
        setupScene(w, h)
        buildPaperGeometry(w, h)
        rebuildExportTarget(w, h)
    }

    private fun setupScene(w: Int, h: Int) {
        val aspect = w.toDouble() / h.toDouble()
        val (right, top) = if (w >= h) {
            aspect to 1.0
        } else {
            1.0 to 1.0 / aspect
        }
        camera?.setProjection(
            Camera.Projection.ORTHO,
            0.0, right,
            0.0, top,
            -1.0, 1.0
        )
        camera?.lookAt(
            0.0, 0.0, 1.0,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0
        )
    }

    private fun buildPaperMaterial() {
        val eng = engine ?: return
        MaterialBuilder.init()
        val pkg = MaterialBuilder()
            .name("paper")
            .materialDomain(MaterialDomain.SURFACE)
            .shading(Shading.UNLIT)
            .blending(BlendingMode.OPAQUE)
            .culling(CullingMode.NONE)
            .depthWrite(false)
            .depthCulling(false)
            .vertexDomain(VertexDomain.DEVICE)
            .material("""
                void material(inout MaterialInputs material) {
                    prepareMaterial(material);
                    material.baseColor = material.color.rgb;
                }
            """.trimIndent())
            .platform(Platform.MOBILE)
            .targetApi(TargetApi.OPENGL)
            .build(eng)
        var ok = false
        if (pkg.isValid()) {
            paperMaterial = Material.Builder().payload(pkg.getBuffer(), pkg.getBuffer().remaining()).build(eng)
            paperInstance = paperMaterial?.getDefaultInstance()
            ok = true
        }
        MaterialBuilder.shutdown()
        if (!ok) {
            pushStatus("Material build failed")
        }
    }

    private fun buildPaperGeometry(w: Int, h: Int) {
        val eng = engine ?: return
        val rm = renderableManager ?: return
        val inst = paperInstance ?: return

        // Destroy previous geometry if any.
        if (quadEntity != 0) {
            rm.destroy(quadEntity)
        }
        quadVb?.let { eng.destroyVertexBuffer(it) }
        quadIb?.let { eng.destroyIndexBuffer(it) }
        quadVb = null
        quadIb = null

        val verts = mutableListOf<Float>()   // interleaved: posX,posY,posZ, colR,colG,colB,colA
        val idxs = mutableListOf<Short>()

        val cream = floatArrayOf(0.996f, 0.976f, 0.890f, 1.0f)
        val rule = floatArrayOf(0.50f, 0.55f, 0.60f, 1.0f)

        fun addQuad(left: Float, right: Float, top: Float, bottom: Float, color: FloatArray) {
            val base = (verts.size / 7).toShort()
            verts.addAll(listOf(left, top, 0.0f) + color.toList())
            verts.addAll(listOf(right, top, 0.0f) + color.toList())
            verts.addAll(listOf(left, bottom, 0.0f) + color.toList())
            verts.addAll(listOf(right, bottom, 0.0f) + color.toList())
            idxs.addAll(listOf(base, (base + 1).toShort(), (base + 2).toShort(),
                (base + 1).toShort(), (base + 3).toShort(), (base + 2).toShort()))
        }

        // Fullscreen paper quad (NDC -1..1).
        addQuad(-1.0f, 1.0f, 1.0f, -1.0f, cream)

        // Ruled lines: horizontal strips spanning the page width.
        val pageLeft = marginXPx / width * 2.0f - 1.0f
        val pageRight = 1.0f
        var yPx = topOffsetPx
        while (yPx + lineWidthPx <= height - topOffsetPx) {
            val topNdc = 1.0f - (yPx / height) * 2.0f
            val bottomNdc = 1.0f - ((yPx + lineWidthPx) / height) * 2.0f
            addQuad(pageLeft, pageRight, topNdc, bottomNdc, rule)
            yPx += lineSpacingPx
        }

        // Left margin rule.
        val marginRight = marginXPx / width * 2.0f - 1.0f
        addQuad(-1.0f, marginRight, 1.0f, -1.0f, rule)

        val vertexCount = verts.size / 7
        val indexCount = idxs.size
        if (vertexCount == 0 || indexCount == 0) return

        // Build interleaved vertex buffer (pos float3 + color float4, 7 floats = 28 bytes per vertex).
        val vb = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, 28)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0,
                VertexBuffer.AttributeType.FLOAT4, 12, 28)
            .build(eng)
        val vbb = ByteBuffer.allocateDirect(vertexCount * 28).order(ByteOrder.nativeOrder())
        for (i in verts.indices step 7) {
            vbb.putFloat(verts[i]).putFloat(verts[i + 1]).putFloat(verts[i + 2])
            vbb.putFloat(verts[i + 3]).putFloat(verts[i + 4]).putFloat(verts[i + 5]).putFloat(verts[i + 6])
        }
        vbb.flip()
        vb.setBufferAt(eng, 0, vbb)

        val ib = IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(IndexBuffer.IndexType.USHORT)
            .build(eng)
        val ibb = ByteBuffer.allocateDirect(indexCount * 2).order(ByteOrder.nativeOrder())
        for (i in idxs) ibb.putShort(i)
        ibb.flip()
        ib.setBuffer(eng, ibb)

        quadEntity = EntityManager.get().create()
        rm.Builder(1)
            .boundingBox(Box(0.0f, 0.0f, 0.0f, 2.0f, 2.0f, 2.0f))
            .culling(false)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .material(0, inst)
            .build(eng, quadEntity)
        scene?.addEntity(quadEntity)

        quadVb = vb
        quadIb = ib
    }

    private fun rebuildExportTarget(w: Int, h: Int) {
        val eng = engine ?: return
        exportTexture?.let { eng.destroyTexture(it) }
        exportRenderTarget?.let { eng.destroyRenderTarget(it) }
        exportTexture = null
        exportRenderTarget = null
        if (w <= 0 || h <= 0) return
        exportTexture = Texture.Builder()
            .width(w)
            .height(h)
            .levels(1)
            .format(Texture.InternalFormat.RGBA8)
            .usage(Texture.Usage.COLOR_ATTACHMENT)
            .build(eng)
        exportRenderTarget = RenderTarget.Builder()
            .texture(RenderTarget.AttachmentPoint.COLOR, exportTexture!!)
            .build(eng)
    }

    private fun renderFrame(frameTimeNanos: Long) {
        val sc = swapChain ?: return
        val rend = renderer ?: return
        if (!rend.beginFrame(sc, frameTimeNanos)) return
        rend.render(view!!)
        rend.endFrame()
    }

    private fun exportPng() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0 || exportRenderTarget == null || exportTexture == null) {
            setStatus("Export failed: no surface", error = true)
            return
        }
        val eng = engine ?: return
        val rend = renderer ?: return
        val v = view ?: return

        val size = w * h * 4
        val bb = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        val pbd = Texture.PixelBufferDescriptor(
            bb, Texture.Format.RGBA, Texture.Type.UBYTE,
            1, 0, 0, 0, mainHandler,
            {
                writePng(bb, w, h)
                v.setRenderTarget(null)
            }
        )

        setStatus("Exporting...", error = false)

        // Render into the offscreen render target, then read pixels back.
        v.setRenderTarget(exportRenderTarget!!)
        if (!rend.beginFrame(swapChain, System.nanoTime())) {
            v.setRenderTarget(null)
            setStatus("Export failed: beginFrame", error = true)
            return
        }
        rend.render(v)
        rend.readPixels(exportRenderTarget!!, 0, 0, w, h, pbd)
        rend.endFrame()
    }

    private fun writePng(bb: ByteBuffer, w: Int, h: Int) {
        bb.rewind()
        val rgba = ByteArray(w * h * 4)
        bb.get(rgba)
        val png = encodePng(rgba, w, h)

        val file = File(cacheDir, "page_${System.currentTimeMillis()}.png")
        var ok = false
        var message = ""
        try {
            FileOutputStream(file).use { out -> out.write(png) }
            ok = true
            message = getString(R.string.msg_export_saved, file.absolutePath)
        } catch (e: Exception) {
            message = getString(R.string.msg_export_failed)
        }

        if (ok) {
            try {
                val uri: Uri = FileProvider.getUriForFile(
                    this, "${packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, null)
                if (chooser.resolveActivity(packageManager) != null) {
                    startActivity(chooser)
                }
            } catch (e: Exception) {
                // Sharing is optional; the file was still written.
            }
        }
        setStatus(message, error = !ok)
    }

    private fun encodePng(rgba: ByteArray, w: Int, h: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        // PNG signature.
        out.write(byteArrayOf(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        // IHDR.
        val ihdr = ByteArray(13)
        putIntBE(ihdr, 0, w)
        putIntBE(ihdr, 4, h)
        ihdr[8] = 8   // bit depth
        ihdr[9] = 6   // color type RGBA
        ihdr[10] = 0  // compression
        ihdr[11] = 0  // filter
        ihdr[12] = 0  // interlace
        writeChunk(out, "IHDR", ihdr)
        // IDAT: no filtering, deflate.
        val filtered = ByteArray(h * (1 + w * 4))
        var pos = 0
        for (y in 0 until h) {
            filtered[pos++] = 0 // filter type none
            System.arraycopy(rgba, y * w * 4, filtered, pos, w * 4)
            pos += w * 4
        }
        val deflater = Deflater()
        deflater.setInput(filtered)
        deflater.finish()
        val buf = ByteArray(4096)
        val idat = java.io.ByteArrayOutputStream()
        while (!deflater.finished) {
            val len = deflater.deflate(buf)
            if (len > 0) idat.write(buf, 0, len)
        }
        deflater.end()
        writeChunk(out, "IDAT", idat.toByteArray())
        // IEND.
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun putIntBE(b: ByteArray, off: Int, v: Int) {
        b[off] = (v >>> 24).toByte()
        b[off + 1] = (v >>> 16).toByte()
        b[off + 2] = (v >>> 8).toByte()
        b[off + 3] = v.toByte()
    }

    private fun writeChunk(out: java.io.ByteArrayOutputStream, type: String, data: ByteArray) {
        val len = data.size
        out.write((len >>> 24).toByte())
        out.write((len >>> 16).toByte())
        out.write((len >>> 8).toByte())
        out.write(len.toByte())
        out.write(type.toByteArray(Charsets.UTF_8))
        out.write(data)
        val crc = CRC32()
        crc.update(type.toByteArray(Charsets.UTF_8))
        crc.update(data)
        val c = crc.value.toInt()
        out.write((c >>> 24).toByte())
        out.write((c >>> 16).toByte())
        out.write((c >>> 8).toByte())
        out.write(c.toByte())
    }

    private fun setStatus(message: String, error: Boolean) {
        statusView.text = message
        statusView.setTextColor(ContextCompat.getColor(
            this,
            if (error) R.color.status_error else R.color.status_ok
        ))
    }

    private fun pushStatus() {
        val ready = engine != null && renderer != null && swapChain != null
        val base = if (ready) "${width}x${height} viewport, swapChain ok" else "not ready"
        setStatus(base, error = !ready)
    }

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(this)
        rendering = false
        uiHelper.detach()
        exportRenderTarget?.let { engine?.destroyRenderTarget(it) }
        exportTexture?.let { engine?.destroyTexture(it) }
        quadVb?.let { engine?.destroyVertexBuffer(it) }
        quadIb?.let { engine?.destroyIndexBuffer(it) }
        if (quadEntity != 0) {
            renderableManager?.destroy(quadEntity)
        }
        paperInstance?.let { engine?.destroyMaterialInstance(it) }
        paperMaterial?.let { engine?.destroyMaterial(it) }
        swapChain?.let { engine?.destroySwapChain(it) }
        engine?.destroyCameraComponent(cameraEntity)
        view?.let { engine?.destroyView(it) }
        scene?.let { engine?.destroyScene(it) }
        renderer?.let { engine?.destroyRenderer(it) }
        engine?.destroy()
        engine = null
        renderer = null
        scene = null
        view = null
        camera = null
        swapChain = null
        renderableManager = null
        paperMaterial = null
        paperInstance = null
        quadVb = null
        quadIb = null
        exportTexture = null
        exportRenderTarget = null
        super.onDestroy()
    }
}
