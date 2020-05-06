package com.soywiz.korge.view

import com.soywiz.korim.color.*
import com.soywiz.korma.geom.vector.*

/**
 * Creates a [Circle] of [radius] and [color].
 * The [autoScaling] determines if the underlying texture will be updated when the hierarchy is scaled.
 * The [callback] allows to configure the [Circle] instance.
 */
inline fun Container.circle(radius: Double = 16.0, color: RGBA = Colors.WHITE, autoScaling: Boolean = true, callback: Circle.() -> Unit = {}): Circle = Circle(radius, color, autoScaling).addTo(this).apply(callback)

/**
 * A [Graphics] class that automatically keeps a circle shape with [radius] and [color].
 * The [autoScaling] property determines if the underlying texture will be updated when the hierarchy is scaled.
 */
open class Circle(radius: Double = 16.0, color: RGBA = Colors.WHITE, autoScaling: Boolean = true) : Graphics(autoScaling = autoScaling) {
    /** Radius of the circle */
    var radius: Double = radius
        set(value) {
            field = value; updateGraphics()
        }

    override val bwidth get() = radius * 2
    override val bheight get() = radius * 2

    /** Color of the circle. Internally it uses [colorMul] for coloring the circle */
    var color: RGBA
        get() = colorMul
        set(value) = run { colorMul = value }

    init {
        this.color = color
        updateGraphics()
    }

    private fun updateGraphics() {
        clear()
        fill(Colors.WHITE) {
            circle(radius, radius, radius)
        }
    }
}
