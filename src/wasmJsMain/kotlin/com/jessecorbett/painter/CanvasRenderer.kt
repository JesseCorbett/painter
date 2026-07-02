@file:OptIn(ExperimentalWasmJsInterop::class)

package com.jessecorbett.painter

import web.canvas.OffscreenCanvas
import web.canvas.OffscreenCanvasRenderingContext2D
import web.canvas.convertToBlob
import web.file.FileReaderSync
import web.http.blob
import web.http.fetch
import web.images.ImageBitmap
import web.images.createImageBitmap

private fun getContext(canvas: OffscreenCanvas): OffscreenCanvasRenderingContext2D = js("canvas.getContext('2d')")

class CanvasRenderer(
    private val canvas: OffscreenCanvas,
    private val width: Int = 512,
    private val height: Int = 512
) {
    private val ctx = getContext(canvas)
    private val imageCache = mutableMapOf<String, ImageBitmap>()
    private val bakedImages = mutableMapOf<String, OffscreenCanvas>()

    init {
        canvas.width = width.toDouble()
        canvas.height = height.toDouble()
    }

    suspend fun render(layers: List<PainterLayer>, callback: (String) -> Unit) {
        val layersToDraw: List<RenderedLayer> = layers.map { layer ->
            loadImageBitmap(layer.url).let { bitmap ->
                layer.hex?.let { color ->
                    renderCustomization(bitmap, layer.url, color)
                } ?: bitmap
            }.let { source ->
                RenderedLayer(source, layer.flipped ?: false)
            }
        }

        ctx.clearRect(0.0, 0.0, width.toDouble(), height.toDouble())
        layersToDraw.forEach { layer ->
            drawImage(layer.source, layer.mirrored)
        }

        callback(FileReaderSync().readAsDataURL(canvas.convertToBlob()))
    }

    private fun renderCustomization(image: ImageBitmap, srcUrl: String, hexColor: String): OffscreenCanvas {
        val cacheKey = "$srcUrl#$hexColor"
        bakedImages[cacheKey]?.let { return it }

        // Evict old colors to prevent memory leaks
        bakedImages.keys
            .filter { it.startsWith("$srcUrl#") }
            .forEach { bakedImages.remove(it) }

        // Create a new OffscreenCanvas for the baked customized layer

        val baseCanvas = OffscreenCanvas(this.width.toDouble(), this.height.toDouble())
        val baseCtx = getContext(baseCanvas)

        baseCtx.drawImage(image, 0.0, 0.0, width.toDouble(), height.toDouble())

        val imageData = baseCtx.getImageData(0, 0, width, height)
        val data = imageData.data
        val maskColor = hexToRgb(hexColor)

        var i = 0
        val len = data.length
        while (i < len) {
            // Read bytes and convert to unsigned Int for arithmetic
            val intensity = data[i].toInt() and 0xFF
            val alpha = data[i + 3].toInt() and 0xFF

            if (alpha != 0) {
                val intensityScale = intensity / 255.0

                // Set explicitly via Wasm-safe typed array bounds
                data[i] = (maskColor.r * intensityScale).toInt().toJsNumber()
                data[i + 1] = (maskColor.g * intensityScale).toInt().toJsNumber()
                data[i + 2] = (maskColor.b * intensityScale).toInt().toJsNumber()
            }
            i += 4
        }

        baseCtx.putImageData(imageData, 0, 0)
        bakedImages[cacheKey] = baseCanvas

        return baseCanvas
    }

    private fun drawImage(imageSource: Any, mirrored: Boolean) {
        if (mirrored) {
            ctx.save()
            ctx.scale(-1.0, 1.0)
            ctx.translate(-width.toDouble(), 0.0)
        }

        when (imageSource) {
            is ImageBitmap -> ctx.drawImage(imageSource, 0.0, 0.0, width.toDouble(), height.toDouble())
            is OffscreenCanvas -> ctx.drawImage(imageSource, 0.0, 0.0, width.toDouble(), height.toDouble())
        }

        if (mirrored) {
            ctx.restore()
        }
    }

    private suspend fun loadImageBitmap(url: String): ImageBitmap {
        imageCache[url]?.let { return it }

        val response = fetch(url)
        if (!response.ok) throw Exception("Failed to load: $url")

        val blob = response.blob()
        val bitmap = createImageBitmap(blob)

        imageCache[url] = bitmap
        return bitmap
    }

    private fun hexToRgb(hex: String): RGB {
        val rawHex = hex.removePrefix("#")
        val fullHex = if (rawHex.length == 3) rawHex.map { "$it$it" }.joinToString("") else rawHex
        return RGB(
            r = fullHex.substring(0, 2).toInt(16),
            g = fullHex.substring(2, 4).toInt(16),
            b = fullHex.substring(4, 6).toInt(16)
        )
    }

    private data class RenderedLayer(val source: Any, val mirrored: Boolean)
}
