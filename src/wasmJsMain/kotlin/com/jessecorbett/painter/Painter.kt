@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalCoroutinesApi::class)

package com.jessecorbett.painter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import web.canvas.OffscreenCanvas
import web.console.console
import web.events.EventType
import web.events.addEventHandler
import web.messaging.MessageEvent
import web.workers.DedicatedWorkerGlobalScope

/**
 * The global execution context of the Dedicated Web Worker.
 */
external val self: DedicatedWorkerGlobalScope

/**
 * Represents the message payload sent from the main application thread to the Web Worker.
 *
 * @property canvas The [OffscreenCanvas] to mount and render on. Only needs to be passed once on initialization.
 * @property width The target width of the canvas.
 * @property height The target height of the canvas.
 * @property layers The list of image layers to render.
 */
external class PainterMessage : JsAny {
    val canvas: OffscreenCanvas?
    val width: Int
    val height: Int
    val layers: JsArray<PainterLayer>?
}

/**
 * Represents an individual layer configuration passed from the JavaScript/TypeScript main thread.
 *
 * @property url The URL or local path of the image source.
 * @property hex An optional hex color string to apply as a color multiplication filter.
 * @property mirrored Whether this layer should be flipped horizontally.
 */
external class PainterLayer : JsAny {
    val url: String
    val hex: String?
    val mirrored: Boolean?
}

/**
 * Entry point for the Painter Web Worker.
 *
 * Sets up a message listener to process incoming [PainterMessage] payloads.
 *
 * A conflated [Channel] is used to enqueue rendering jobs. This ensures that if multiple
 * render requests arrive while a rendering operation is already in progress, intermediate
 * frames are discarded, and only the latest layer list is rendered, maximizing performance.
 *
 * Once rendering is complete, the resulting data URL is posted back to the main thread.
 */
fun main() {
    val scope = CoroutineScope(SupervisorJob())
    var renderer: CanvasRenderer? = null
    val renderChannel = Channel<List<Layer>>(Channel.CONFLATED)

    self.addEventHandler(EventType("message")) { event: MessageEvent<PainterMessage> ->
        val data = event.data

        data.canvas?.let { canvas ->
            console.log("Mounting canvas in Painter")
            renderer = CanvasRenderer(canvas, width = data.width, height = data.height)
        }

        data.layers?.let { layers ->
            renderChannel.trySend(layers.toList().map {
                Layer(
                    url = it.url,
                    hex = it.hex,
                    mirrored = it.mirrored ?: false
                )
            })
        }
    }

    self.postMessage("Ready")

    scope.launch {
        for (message in renderChannel) {
            val r = renderer ?: continue

            r.render(message)

            if (renderChannel.isEmpty) {
                self.postMessage(r.getDataUrl())
            }
        }
    }
}

