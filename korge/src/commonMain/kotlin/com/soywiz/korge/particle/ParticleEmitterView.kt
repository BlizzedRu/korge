package com.soywiz.korge.particle

import com.soywiz.korge.render.*
import com.soywiz.korge.time.*
import com.soywiz.korge.view.*
import com.soywiz.korio.util.*
import com.soywiz.korma.geom.*

inline fun Container.particleEmitter(
	emitter: ParticleEmitter, emitterPos: Point = Point(),
	callback: @ViewsDslMarker ParticleEmitterView.() -> Unit = {}
) = ParticleEmitterView(emitter, emitterPos).addTo(this).apply(callback)

class ParticleEmitterView(val emitter: ParticleEmitter, emitterPos: Point = Point()) : View() {
	val simulator = ParticleEmitter.Simulator(emitter, emitterPos)

	var timeUntilStop by simulator::timeUntilStop.redirect()
	val emitterPos by simulator::emitterPos.redirect()
	var emitting by simulator::emitting.redirect()
	val aliveCount by simulator::aliveCount.redirect()
	val anyAlive by simulator::anyAlive.redirect()

	init {
		addUpdatable { time ->
			simulator.simulate(time)
		}
	}

	suspend fun waitComplete() {
		while (anyAlive) waitFrame()
	}

	// @TODO: Make ultra-fast rendering flushing ctx and using a custom shader + vertices + indices
	override fun renderInternal(ctx: RenderContext) {
		if (!visible) return
		//ctx.flush()

		val context = ctx.ctx2d
		val texture = emitter.texture ?: return
		val cx = texture.width * 0.5f
		val cy = texture.height * 0.5f
		context.keep {
			context.blendFactors = emitter.blendFactors
			context.setMatrix(globalMatrix)

			for (p in simulator.particles) {
				val scale = p.scale
				context.multiplyColor = p.colorInt
				context.imageScale(ctx.getTex(texture), p.x - cx * scale, p.y - cy * scale, scale)
			}
		}
	}
}
