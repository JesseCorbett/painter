package com.jessecorbett.painter

/**
 * Represents a configuration for an image layer to be rendered.
 *
 * @property url The URL or path to the source image.
 * @property hex An optional hex color string (e.g. "#FF5733" or "F50"). If provided, the image's colors
 *               will be customized using a multiply blend operation where the grayscale intensity maps to
 *               the specified color.
 * @property mirrored Whether the layer should be rendered horizontally mirrored (flipped).
 */
data class Layer(
    val url: String,
    val hex: String?,
    val mirrored: Boolean
)

