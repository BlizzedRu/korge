/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2012-2018 DragonBones team and other contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.dragonbones.model

import com.dragonbones.core.*
import com.dragonbones.geom.*
import com.dragonbones.util.*
import com.soywiz.kds.*
import kotlin.math.*

/**
 * - The base class of bounding box data.
 * @see dragonBones.RectangleData
 * @see dragonBones.EllipseData
 * @see dragonBones.PolygonData
 * @version DragonBones 5.0
 * @language en_US
 */
/**
 * - 边界框数据基类。
 * @see dragonBones.RectangleData
 * @see dragonBones.EllipseData
 * @see dragonBones.PolygonData
 * @version DragonBones 5.0
 * @language zh_CN
 */
abstract class BoundingBoxData(pool: BaseObjectPool) : BaseObject(pool) {
	/**
	 * - The bounding box type.
	 * @version DragonBones 5.0
	 * @language en_US
	 */
	/**
	 * - 边界框类型。
	 * @version DragonBones 5.0
	 * @language zh_CN
	 */
	var type: BoundingBoxType = BoundingBoxType.None
	/**
	 * @private
	 */
	var color: Int = 0x000000
	/**
	 * @private
	 */
	var width: Float = 0f
	/**
	 * @private
	 */
	var height: Float = 0f

	override fun _onClear(): Unit {
		this.color = 0x000000
		this.width = 0f
		this.height = 0f
	}
	/**
	 * - Check whether the bounding box contains a specific point. (Local coordinate system)
	 * @version DragonBones 5.0
	 * @language en_US
	 */
	/**
	 * - 检查边界框是否包含特定点。（本地坐标系）
	 * @version DragonBones 5.0
	 * @language zh_CN
	 */
	abstract fun containsPoint(pX: Float, pY: Float): Boolean
	/**
	 * - Check whether the bounding box intersects a specific segment. (Local coordinate system)
	 * @version DragonBones 5.0
	 * @language en_US
	 */
	/**
	 * - 检查边界框是否与特定线段相交。（本地坐标系）
	 * @version DragonBones 5.0
	 * @language zh_CN
	 */
	abstract fun intersectsSegment(
		xA: Float, yA: Float, xB: Float, yB: Float,
		intersectionPointA: Point? = null,
		intersectionPointB: Point? = null,
		normalRadians: Point? = null
	): Int
}

/**
 * - Cohen–Sutherland algorithm https://en.wikipedia.org/wiki/Cohen%E2%80%93Sutherland_algorithm
 * ----------------------
 * | 0101 | 0100 | 0110 |
 * ----------------------
 * | 0001 | 0000 | 0010 |
 * ----------------------
 * | 1001 | 1000 | 1010 |
 * ----------------------
 */
//enum class OutCode(val id: Int) {
//	InSide(0), // 0000
//	Left(1),   // 0001
//	Right(2),  // 0010
//	Top(4),    // 0100
//	Bottom(8)  // 1000
//}

object OutCode {
	const val InSide = 0  // 0000
	const val Left = 1    // 0001
	const val Right = 2   // 0010
	const val Top = 4     // 0100
	const val Bottom = 8  // 1000
}
/**
 * - The rectangle bounding box data.
 * @version DragonBones 5.1
 * @language en_US
 */
/**
 * - 矩形边界框数据。
 * @version DragonBones 5.1
 * @language zh_CN
 */
class RectangleBoundingBoxData(pool: BaseObjectPool) : BoundingBoxData(pool) {
	override fun toString(): String {
		return "[class dragonBones.RectangleBoundingBoxData]"
	}

	companion object {
		/**
		 * - Compute the bit code for a point (x, y) using the clip rectangle
		 */
		private fun _computeOutCode(x: Float, y: Float, xMin: Float, yMin: Float, xMax: Float, yMax: Float): Int {
			var code = OutCode.InSide  // initialised as being inside of [[clip window]]

			if (x < xMin) {             // to the left of clip window
				code = code or OutCode.Left
			} else if (x > xMax) {        // to the right of clip window
				code = code or OutCode.Right
			}

			if (y < yMin) {             // below the clip window
				code = code or OutCode.Top
			} else if (y > yMax) {        // above the clip window
				code = code or OutCode.Bottom
			}

			return code
		}

		/**
		 * @private
		 */
		fun rectangleIntersectsSegment(
			xA: Float, yA: Float, xB: Float, yB: Float,
			xMin: Float, yMin: Float, xMax: Float, yMax: Float,
			intersectionPointA:
			Point? = null,
			intersectionPointB:
			Point? = null,
			normalRadians:
			Point? = null
		): Int {
			var xA = xA
			var yA = yA
			var xB = xB
			var yB = yB
			val inSideA = xA > xMin && xA < xMax && yA > yMin && yA < yMax
			val inSideB = xB > xMin && xB < xMax && yB > yMin && yB < yMax

			if (inSideA && inSideB) {
				return -1
			}

			var intersectionCount = 0
			var outcode0 = RectangleBoundingBoxData._computeOutCode(xA, yA, xMin, yMin, xMax, yMax)
			var outcode1 = RectangleBoundingBoxData._computeOutCode(xB, yB, xMin, yMin, xMax, yMax)

			while (true) {
				if ((outcode0 or outcode1) == 0) {
					// Bitwise OR is 0. Trivially accept and get out of loop
					intersectionCount = 2
					break
				} else if ((outcode0 and outcode1) != 0) {
					// Bitwise AND is not 0. Trivially reject and get out of loop
					break
				}

				// failed both tests, so calculate the line segment to clip
				// from an outside point to an intersection with clip edge
				var x = 0f
				var y = 0f
				var normalRadian = 0f

				// At least one endpoint is outside the clip rectangle; pick it.
				val outcodeOut = if (outcode0 != 0) outcode0 else outcode1

				// Now find the intersection point;
				if ((outcodeOut and OutCode.Top) != 0) {
					// point is above the clip rectangle
					x = xA + (xB - xA) * (yMin - yA) / (yB - yA)
					y = yMin

					if (normalRadians != null) {
						normalRadian = -PIf * 0.5f
					}
				} else if ((outcodeOut and OutCode.Bottom) != 0) {
					// point is below the clip rectangle
					x = xA + (xB - xA) * (yMax - yA) / (yB - yA)
					y = yMax

					if (normalRadians != null) {
						normalRadian = PIf * 0.5f
					}
				} else if ((outcodeOut and OutCode.Right) != 0) {
					// point is to the right of clip rectangle
					y = yA + (yB - yA) * (xMax - xA) / (xB - xA)
					x = xMax

					if (normalRadians != null) {
						normalRadian = 0f
					}
				} else if ((outcodeOut and OutCode.Left) != 0) {
					// point is to the left of clip rectangle
					y = yA + (yB - yA) * (xMin - xA) / (xB - xA)
					x = xMin

					if (normalRadians != null) {
						normalRadian = PIf
					}
				}

				// Now we move outside point to intersection point to clip
				// and get ready for next pass.
				if (outcodeOut == outcode0) {
					xA = x
					yA = y
					outcode0 = RectangleBoundingBoxData._computeOutCode(xA, yA, xMin, yMin, xMax, yMax)

					if (normalRadians != null) {
						normalRadians.x = normalRadian.toFloat()
					}
				} else {
					xB = x
					yB = y
					outcode1 = RectangleBoundingBoxData._computeOutCode(xB, yB, xMin, yMin, xMax, yMax)

					if (normalRadians != null) {
						normalRadians.y = normalRadian.toFloat()
					}
				}
			}

			if (intersectionCount != 0) {
				if (inSideA) {
					intersectionCount = 2 // 10

					if (intersectionPointA != null) {
						intersectionPointA.x = xB.toFloat()
						intersectionPointA.y = yB.toFloat()
					}

					if (intersectionPointB != null) {
						intersectionPointB.x = xB.toFloat()
						intersectionPointB.y = xB.toFloat()
					}

					if (normalRadians != null) {
						normalRadians.x = (normalRadians.y + PI).toFloat()
					}
				} else if (inSideB) {
					intersectionCount = 1 // 01

					if (intersectionPointA != null) {
						intersectionPointA.x = xA.toFloat()
						intersectionPointA.y = yA.toFloat()
					}

					if (intersectionPointB != null) {
						intersectionPointB.x = xA.toFloat()
						intersectionPointB.y = yA.toFloat()
					}

					if (normalRadians != null) {
						normalRadians.y = (normalRadians.x + PI).toFloat()
					}
				} else {
					intersectionCount = 3 // 11
					if (intersectionPointA != null) {
						intersectionPointA.x = xA.toFloat()
						intersectionPointA.y = yA.toFloat()
					}

					if (intersectionPointB != null) {
						intersectionPointB.x = xB.toFloat()
						intersectionPointB.y = yB.toFloat()
					}
				}
			}

			return intersectionCount
		}
	}

	override fun _onClear(): Unit {
		super._onClear()

		this.type = BoundingBoxType.Rectangle
	}

	/**
	 * @inheritDoc
	 */
	override fun containsPoint(pX: Float, pY: Float): Boolean {
		val widthH = this.width * 0.5
		if (pX >= -widthH && pX <= widthH) {
			val heightH = this.height * 0.5
			if (pY >= -heightH && pY <= heightH) {
				return true
			}
		}

		return false
	}

	/**
	 * @inheritDoc
	 */
	override fun intersectsSegment(
		xA: Float, yA: Float, xB: Float, yB: Float,
		intersectionPointA: Point?,
		intersectionPointB: Point?,
		normalRadians: Point?
	): Int {
		val widthH = this.width * 0.5f
		val heightH = this.height * 0.5f
		val intersectionCount = RectangleBoundingBoxData.rectangleIntersectsSegment(
			xA, yA, xB, yB,
			-widthH, -heightH, widthH, heightH,
			intersectionPointA, intersectionPointB, normalRadians
		)

		return intersectionCount
	}
}
/**
 * - The ellipse bounding box data.
 * @version DragonBones 5.1
 * @language en_US
 */
/**
 * - 椭圆边界框数据。
 * @version DragonBones 5.1
 * @language zh_CN
 */
class EllipseBoundingBoxData(pool: BaseObjectPool) : BoundingBoxData(pool) {
	override fun toString(): String {
		return "[class dragonBones.EllipseData]"
	}

	companion object {
		/**
		 * @private
		 */
		fun ellipseIntersectsSegment(
			xA: Float, yA: Float, xB: Float, yB: Float,
			xC: Float, yC: Float, widthH: Float, heightH: Float,
			intersectionPointA: Point? = null,
			intersectionPointB: Point? = null,
			normalRadians: Point? = null
		): Int {
			var xA = xA
			var xB = xB
			var yA = yA
			var yB = yB
			val d = widthH / heightH
			val dd = d * d

			yA *= d
			yB *= d

			val dX = xB - xA
			val dY = yB - yA
			val lAB = sqrt(dX * dX + dY * dY)
			val xD = dX / lAB
			val yD = dY / lAB
			val a = (xC - xA) * xD + (yC - yA) * yD
			val aa = a * a
			val ee = xA * xA + yA * yA
			val rr = widthH * widthH
			val dR = rr - ee + aa
			var intersectionCount = 0

			if (dR >= 0f) {
				val dT = sqrt(dR)
				val sA = a - dT
				val sB = a + dT
				val inSideA = if (sA < 0f) -1 else if (sA <= lAB) 0 else 1
				val inSideB = if (sB < 0f) -1 else if (sB <= lAB) 0 else 1
				val sideAB = inSideA * inSideB

				if (sideAB < 0) {
					return -1
				} else if (sideAB == 0) {
					if (inSideA == -1) {
						intersectionCount = 2 // 10
						xB = xA + sB * xD
						yB = (yA + sB * yD) / d

						if (intersectionPointA != null) {
							intersectionPointA.x = xB.toFloat()
							intersectionPointA.y = yB.toFloat()
						}

						if (intersectionPointB != null) {
							intersectionPointB.x = xB.toFloat()
							intersectionPointB.y = yB.toFloat()
						}

						if (normalRadians != null) {
							normalRadians.x = atan2(yB / rr * dd, xB / rr).toFloat()
							normalRadians.y = (normalRadians.x + PI).toFloat()
						}
					} else if (inSideB == 1) {
						intersectionCount = 1 // 01
						xA = xA + sA * xD
						yA = (yA + sA * yD) / d

						if (intersectionPointA != null) {
							intersectionPointA.x = xA.toFloat()
							intersectionPointA.y = yA.toFloat()
						}

						if (intersectionPointB != null) {
							intersectionPointB.x = xA.toFloat()
							intersectionPointB.y = yA.toFloat()
						}

						if (normalRadians != null) {
							normalRadians.x = atan2(yA / rr * dd, xA / rr).toFloat()
							normalRadians.y = (normalRadians.x + PI).toFloat()
						}
					} else {
						intersectionCount = 3 // 11

						if (intersectionPointA != null) {
							intersectionPointA.x = (xA + sA * xD).toFloat()
							intersectionPointA.y = ((yA + sA * yD) / d).toFloat()

							if (normalRadians != null) {
								normalRadians.x = atan2(intersectionPointA.y / rr * dd, intersectionPointA.x / rr).toFloat()
							}
						}

						if (intersectionPointB != null) {
							intersectionPointB.x = (xA + sB * xD).toFloat()
							intersectionPointB.y = ((yA + sB * yD) / d).toFloat()

							if (normalRadians != null) {
								normalRadians.y = atan2(intersectionPointB.y / rr * dd, intersectionPointB.x / rr).toFloat()
							}
						}
					}
				}
			}

			return intersectionCount
		}
	}

	override fun _onClear(): Unit {
		super._onClear()

		this.type = BoundingBoxType.Ellipse
	}

	/**
	 * @inheritDoc
	 */
	override fun containsPoint(pX: Float, pY: Float): Boolean {
		var pY = pY
		val widthH = this.width * 0.5f
		if (pX >= -widthH && pX <= widthH) {
			val heightH = this.height * 0.5f
			if (pY >= -heightH && pY <= heightH) {
				pY *= widthH / heightH
				return sqrt(pX * pX + pY * pY) <= widthH
			}
		}

		return false
	}

	/**
	 * @inheritDoc
	 */
	override fun intersectsSegment(
		xA: Float, yA: Float, xB: Float, yB: Float,
		intersectionPointA: Point?,
		intersectionPointB: Point?,
		normalRadians: Point?
	): Int {
		val intersectionCount = EllipseBoundingBoxData.ellipseIntersectsSegment(
			xA, yA, xB, yB,
			0f, 0f, this.width * 0.5f, this.height * 0.5f,
			intersectionPointA, intersectionPointB, normalRadians
		)

		return intersectionCount
	}
}
/**
 * - The polygon bounding box data.
 * @version DragonBones 5.1
 * @language en_US
 */
/**
 * - 多边形边界框数据。
 * @version DragonBones 5.1
 * @language zh_CN
 */
class PolygonBoundingBoxData(pool: BaseObjectPool) : BoundingBoxData(pool) {
	override fun toString(): String {
		return "[class dragonBones.PolygonBoundingBoxData]"
	}

	/**
	 * @private
	 */
	fun polygonIntersectsSegment(
		xA: Float, yA: Float, xB: Float, yB: Float,
		vertices: FloatArray,
		intersectionPointA: Point? = null,
		intersectionPointB: Point? = null,
		normalRadians: Point? = null
	): Int {
		var xA = xA
		var yA = yA
		if (xA == xB) xA = xB + 0.000001f
		if (yA == yB) yA = yB + 0.000001f

		val count = vertices.size
		val dXAB = xA - xB
		val dYAB = yA - yB
		val llAB = xA * yB - yA * xB
		var intersectionCount = 0
		var xC = vertices[count - 2]
		var yC = vertices[count - 1]
		var dMin = 0f
		var dMax = 0f
		var xMin = 0f
		var yMin = 0f
		var xMax = 0f
		var yMax = 0f

		for (i in 0 until count step 2) {
			val xD = vertices[i + 0]
			val yD = vertices[i + 1]

			if (xC == xD) {
				xC = xD + 0.0001f
			}

			if (yC == yD) {
				yC = yD + 0.0001f
			}

			val dXCD: Float = xC - xD
			val dYCD: Float = yC - yD
			val llCD: Float = xC * yD - yC * xD
			val ll: Float = dXAB * dYCD - dYAB * dXCD
			val x: Float = (llAB * dXCD - dXAB * llCD) / ll

			if (((x >= xC && x <= xD) || (x >= xD && x <= xC)) && (dXAB == 0f || (x >= xA && x <= xB) || (x >= xB && x <= xA))) {
				val y = (llAB * dYCD - dYAB * llCD) / ll
				if (((y >= yC && y <= yD) || (y >= yD && y <= yC)) && (dYAB == 0f || (y >= yA && y <= yB) || (y >= yB && y <= yA))) {
					if (intersectionPointB != null) {
						var d = x - xA
						if (d < 0f) {
							d = -d
						}

						if (intersectionCount == 0) {
							dMin = d
							dMax = d
							xMin = x
							yMin = y
							xMax = x
							yMax = y

							if (normalRadians != null) {
								normalRadians.x = (atan2(yD - yC, xD - xC) - PI * 0.5).toFloat()
								normalRadians.y = normalRadians.x
							}
						} else {
							if (d < dMin) {
								dMin = d
								xMin = x
								yMin = y

								if (normalRadians != null) {
									normalRadians.x = (atan2(yD - yC, xD - xC) - PI * 0.5).toFloat()
								}
							}

							if (d > dMax) {
								dMax = d
								xMax = x
								yMax = y

								if (normalRadians != null) {
									normalRadians.y = (atan2(yD - yC, xD - xC) - PI * 0.5).toFloat()
								}
							}
						}

						intersectionCount++
					} else {
						xMin = x
						yMin = y
						xMax = x
						yMax = y
						intersectionCount++

						if (normalRadians != null) {
							normalRadians.x = (atan2(yD - yC, xD - xC) - PI * 0.5).toFloat()
							normalRadians.y = normalRadians.x
						}
						break
					}
				}
			}

			xC = xD
			yC = yD
		}

		if (intersectionCount == 1) {
			if (intersectionPointA != null) {
				intersectionPointA.x = xMin
				intersectionPointA.y = yMin
			}

			if (intersectionPointB != null) {
				intersectionPointB.x = xMin
				intersectionPointB.y = yMin
			}

			if (normalRadians != null) {
				normalRadians.y = (normalRadians.x + PI).toFloat()
			}
		} else if (intersectionCount > 1) {
			intersectionCount++

			if (intersectionPointA != null) {
				intersectionPointA.x = xMin
				intersectionPointA.y = yMin
			}

			if (intersectionPointB != null) {
				intersectionPointB.x = xMax
				intersectionPointB.y = yMax
			}
		}

		return intersectionCount
	}

	/**
	 * @private
	 */
	var x: Float = 0f
	/**
	 * @private
	 */
	var y: Float = 0f
	/**
	 * - The polygon vertices.
	 * @version DragonBones 5.1
	 * @language en_US
	 */
	/**
	 * - 多边形顶点。
	 * @version DragonBones 5.1
	 * @language zh_CN
	 */
	var vertices: FloatArray = FloatArray(0)

	override fun _onClear(): Unit {
		super._onClear()

		this.type = BoundingBoxType.Polygon
		this.x = 0f
		this.y = 0f
		this.vertices = FloatArray(0)
	}

	/**
	 * @inheritDoc
	 */
	override fun containsPoint(pX: Float, pY: Float): Boolean {
		var isInSide = false
		if (pX >= this.x && pX <= this.width && pY >= this.y && pY <= this.height) {
			var iP = this.vertices.size - 2
			for (i in 0 until this.vertices.size step 2) {
				val yA = this.vertices[iP + 1]
				val yB = this.vertices[i + 1]
				if ((yB < pY && yA >= pY) || (yA < pY && yB >= pY)) {
					val xA = this.vertices[iP]
					val xB = this.vertices[i]
					if ((pY - yB) * (xA - xB) / (yA - yB) + xB < pX) {
						isInSide = !isInSide
					}
				}

				iP = i
			}
		}

		return isInSide
	}

	/**
	 * @inheritDoc
	 */
	override fun intersectsSegment(
		xA: Float, yA: Float, xB: Float, yB: Float,
		intersectionPointA: Point?,
		intersectionPointB: Point?,
		normalRadians: Point?
	): Int {
		var intersectionCount = 0
		if (RectangleBoundingBoxData.rectangleIntersectsSegment(
				xA,
				yA,
				xB,
				yB,
				this.x,
				this.y,
				this.x + this.width,
				this.y + this.height,
				null,
				null,
				null
			) != 0
		) {
			intersectionCount = polygonIntersectsSegment(
				xA, yA, xB, yB,
				this.vertices,
				intersectionPointA, intersectionPointB, normalRadians
			)
		}

		return intersectionCount
	}
}
