@file:OptIn(ExperimentalWasmJsInterop::class)

package com.jessecorbett.painter

import web.canvas.CanvasImageSource
import web.canvas.OffscreenCanvas
import web.canvas.OffscreenCanvasRenderingContext2D
import web.canvas.convertToBlob
import web.file.FileReaderSync
import web.http.blob
import web.http.fetch
import web.images.ImageBitmap
import web.images.createImageBitmap

@Suppress("unused")
private fun getContext(canvas: OffscreenCanvas): OffscreenCanvasRenderingContext2D = js("canvas.getContext('2d')")

/**
 * Manages rendering of multiple [Layer]s onto an [OffscreenCanvas].
 *
 * It handles resource loading (via fetch), caching of both raw and color-baked assets,
 * image color customization (blending), and horizontal mirroring transformations.
 *
 * @param canvas The target [OffscreenCanvas] to draw onto.
 * @param width The target width of the canvas in pixels.
 * @param height The target height of the canvas in pixels.
 */
class CanvasRenderer(
    private val canvas: OffscreenCanvas,
    private val width: Int,
    private val height: Int
) {
    private val ctx = getContext(canvas)
    private val imageCache = mutableMapOf<String, CanvasImageSource>()
    private val imageColors = mutableMapOf<String, String?>()

    init {
        canvas.width = width.toDouble()
        canvas.height = height.toDouble()
    }

    /**
     * Renders a list of [Layer] objects in sequence onto the canvas.
     *
     * This function clears the canvas, fetches/processes all required images asynchronously,
     * and draws them sequentially.
     *
     * @param layers The list of layers to render, in bottom-to-top order.
     */
    suspend fun render(layers: List<Layer>) {
        val layersToDraw: List<RenderedLayer> = layers.map { layer ->
            RenderedLayer(getImageSource(layer.url, layer.hex), layer.mirrored)
        }

        ctx.clearRect(0.0, 0.0, width.toDouble(), height.toDouble())
        layersToDraw.forEach { layer ->
            drawImage(layer.source, layer.mirrored)
        }
    }

    /**
     * Converts the current state of the offscreen canvas into a base64 Data URL.
     *
     * @return The base64-encoded Data URL representing the rendered canvas (typically image/png).
     */
    suspend fun getDataUrl(): String = FileReaderSync().readAsDataURL(canvas.convertToBlob())

    /**
     * Retrieves the [CanvasImageSource] for a given URL and color customization hex.
     *
     * This method fetches the image if not already cached. If a [hex] color is provided,
     * it bakes the color overlay into a separate canvas and caches the result.
     *
     * @param url The image source URL.
     * @param hex The optional color hex string to tint the image with.
     * @return The prepared [CanvasImageSource] (either [ImageBitmap] or [OffscreenCanvas]).
     */
    private suspend fun getImageSource(url: String, hex: String?): CanvasImageSource {
        val cached = imageCache[url]
        val cachedHex = imageColors[url]

        if (cached != null && cachedHex == hex) {
            return cached
        }

        val bitmap = if (cached is ImageBitmap && cachedHex == null) {
            cached
        } else {
            val response = fetch(url)
            if (!response.ok) throw Exception("Failed to load: $url")
            createImageBitmap(response.blob())
        }

        val result = if (hex != null) {
            bakeCustomization(bitmap, hex)
        } else {
            bitmap
        }

        imageCache[url] = result
        imageColors[url] = hex

        return result
    }

    /**
     * Applies a color multiplication (tint) filter to a source [ImageBitmap].
     *
     * It maps the grayscale intensity of each source pixel's channel to a scaled version
     * of the specified [hexColor] RGB values. Transparent pixels are preserved.
     *
     * @param image The source [ImageBitmap].
     * @param hexColor The hex color string to apply.
     * @return A new [OffscreenCanvas] containing the tinted image.
     */
    private fun bakeCustomization(image: ImageBitmap, hexColor: String): OffscreenCanvas {
        val baseCanvas = OffscreenCanvas(this.width.toDouble(), this.height.toDouble())
        val baseCtx = getContext(baseCanvas)

        baseCtx.drawImage(image, 0.0, 0.0, width.toDouble(), height.toDouble())

        val imageData = baseCtx.getImageData(0, 0, width, height)
        val data = imageData.data
        val maskColor = hexToRgb(hexColor)

        var i = 0
        val len = data.length
        while (i < len) {
            val intensity = data[i].toInt() and 0xFF
            val alpha = data[i + 3].toInt() and 0xFF

            if (alpha != 0) {
                val intensityScale = intensity / 255.0

                data[i] = (maskColor.r * intensityScale).toInt().toJsNumber()
                data[i + 1] = (maskColor.g * intensityScale).toInt().toJsNumber()
                data[i + 2] = (maskColor.b * intensityScale).toInt().toJsNumber()
            }
            i += 4
        }

        baseCtx.putImageData(imageData, 0, 0)

        return baseCanvas
    }

    /**
     * Draws the image source to the main canvas context, applying horizontal mirroring if requested.
     *
     * @param imageSource The image or canvas to draw.
     * @param mirrored Whether to flip the image horizontally.
     */
    private fun drawImage(imageSource: CanvasImageSource, mirrored: Boolean) {
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


    /**
     * Parses a hex color string into an [RGB] color object.
     *
     * Supports both short form (e.g. "#F00" -> "#FF0000") and long form (e.g. "#FF0000").
     * The leading '#' prefix is optional.
     *
     * @param hex The hex color string to parse.
     * @return The parsed [RGB] color.
     */
    private fun hexToRgb(hex: String): RGB {
        val rawHex = hex.removePrefix("#")
        val fullHex = if (rawHex.length == 3) rawHex.map { "$it$it" }.joinToString("") else rawHex
        return RGB(
            r = fullHex.substring(0, 2).toInt(16),
            g = fullHex.substring(2, 4).toInt(16),
            b = fullHex.substring(4, 6).toInt(16)
        )
    }

    private data class RenderedLayer(val source: CanvasImageSource, val mirrored: Boolean)
}
