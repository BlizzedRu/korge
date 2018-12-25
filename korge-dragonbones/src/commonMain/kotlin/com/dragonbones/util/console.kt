package com.dragonbones.util

object console {
	fun warn(vararg msg: String) {
		println(msg.joinToString("\n"))
	}

	fun error(vararg msg: String) {
		println(msg.joinToString("\n"))
	}

	fun assert(cond: Boolean, msg: String): Unit {
		if (cond) throw AssertionError(msg)
	}
}
