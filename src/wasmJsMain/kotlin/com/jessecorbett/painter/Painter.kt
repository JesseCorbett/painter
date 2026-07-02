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

external val self: DedicatedWorkerGlobalScope

external class PainterMessage : JsAny {
    val canvas: OffscreenCanvas?
    val width: Int
    val height: Int
    val layers: JsArray<PainterLayer>?
}

external class PainterLayer : JsAny {
    val url: String
    val hex: String?
    val mirrored: Boolean?
}

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
