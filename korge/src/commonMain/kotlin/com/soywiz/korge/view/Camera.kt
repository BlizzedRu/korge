package com.soywiz.korge.view

import com.soywiz.klock.*
import com.soywiz.korge.tween.*
import com.soywiz.korma.geom.*
import com.soywiz.korma.geom.*
import com.soywiz.korma.interpolation.*

/**
 * Creates a new [Camera] and attaches to [this] [Container]. The [callback] argument is called with the [Camera] injected as this to be able to configure the camera.
 */
inline fun Container.camera(callback: @ViewsDslMarker Camera.() -> Unit = {}): Camera = Camera().addTo(this).apply(callback)

/**
 * A [Camera] is a [FixedSizeContainer]
 */
class Camera : FixedSizeContainer(), View.Reference {
    // @TODO: stage is not set at this point
    //override var width: Double = stage?.views?.virtualWidth?.toDouble() ?: 100.0
	//override var height: Double = stage?.views?.virtualHeight?.toDouble() ?: 100.0

    override var width: Double = 100.0
    override var height: Double = 100.0

    override fun getLocalBoundsInternal(out: Rectangle) {
		out.setTo(0, 0, width, height)
	}

	fun getLocalMatrixFittingGlobalRect(rect: Rectangle): Matrix {
		val destinationBounds = rect
		val mat = this.parent?.globalMatrix?.clone() ?: Matrix()
		mat.translate(-destinationBounds.x, -destinationBounds.y)
		mat.scale(
			width / destinationBounds.width,
			height / destinationBounds.height
		)
		//println(identityBounds)
		//println(destinationBounds)
		return mat
	}

	fun getLocalMatrixFittingView(view: View?): Matrix =
		getLocalMatrixFittingGlobalRect((view ?: stage)?.globalBounds ?: Rectangle(0, 0, 100, 100))

	fun setTo(view: View?) = run { this.localMatrix = getLocalMatrixFittingView(view) }
	fun setTo(rect: Rectangle) = run { this.localMatrix = getLocalMatrixFittingGlobalRect(rect) }

	suspend fun tweenTo(view: View?, vararg vs: V2<*>, time: TimeSpan, easing: Easing = Easing.LINEAR) = this.tween(
		this::localMatrix[this.localMatrix.clone(), getLocalMatrixFittingView(view)],
		*vs,
		time = time,
		easing = easing
	)

	suspend fun tweenTo(rect: Rectangle, vararg vs: V2<*>, time: TimeSpan, easing: Easing = Easing.LINEAR) = this.tween(
		this::localMatrix[this.localMatrix.clone(), getLocalMatrixFittingGlobalRect(rect)],
		*vs,
		time = time,
		easing = easing
	)
}
