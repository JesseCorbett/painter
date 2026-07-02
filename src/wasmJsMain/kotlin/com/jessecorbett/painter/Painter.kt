@file:OptIn(ExperimentalWasmJsInterop::class)

package com.jessecorbett.painter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
    val flipped: Boolean?
}

fun main() {
    val scope = CoroutineScope(SupervisorJob())
    var renderer: CanvasRenderer? = null

    self.addEventHandler(EventType("message")) { event: MessageEvent<PainterMessage> ->
        val data = event.data

        data.canvas?.let { canvas ->
            console.log("Mounting canvas in Painter")
            renderer = CanvasRenderer(canvas, width = data.width, height = data.height)
        }

        data.layers?.let { layers ->
            renderer?.let { renderer ->
                scope.launch {
                    console.log("Drawing ${layers.length} layers in Painter")
                    renderer.render(layers.toList()) { dataUrl ->
                        self.postMessage(dataUrl)
                    }
                }
            }
        }
    }
}
