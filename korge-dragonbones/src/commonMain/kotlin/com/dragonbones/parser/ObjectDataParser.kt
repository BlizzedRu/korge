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
package com.dragonbones.parser

import com.dragonbones.core.*
import com.dragonbones.geom.*
import com.dragonbones.model.*
import com.dragonbones.util.*
import com.soywiz.kds.*
import com.soywiz.kmem.*
import com.soywiz.korio.lang.*
import kotlin.math.*

/**
 * @private
 */
enum class FrameValueType(val index: kotlin.Int) {
	STEP(0),
	INT(1),
	FLOAT(2),
}

/**
 * @private
 */
@Suppress("UNCHECKED_CAST", "NAME_SHADOWING", "UNUSED_CHANGED_VALUE")
open class ObjectDataParser(pool: BaseObjectPool = BaseObjectPool()) : DataParser(pool) {
	companion object {
		// dynamic tools
		internal operator fun Any?.get(key: String): Any? {
			return Dynamic.get(this, key)
		}

		internal inline operator fun Any?.contains(key: String): Boolean {
			return this.get(key) != null
		}

		internal val Any?.dynKeys get() = (this as Map<String, Any?>).keys
		internal val Any?.dynList get() = Dynamic.toList(this)
		internal val Any?.FloatArray: FloatArray get() {
			if (this is FloatArray) return this
			if (this is FloatArrayList) return this.toFloatArray()
			if (this is List<*>) return this.map { (it as Number).toFloat() }.toFloatArray()
			error("Can't cast '$this' to FloatArray")
		}
		internal val Any?.FloatArrayList: FloatArrayList get() {
			if (this is FloatArray) return FloatArrayList(*this)
			if (this is FloatArrayList) return this
			if (this is List<*>) return FloatArrayList(*this.map { (it as Number).toFloat() }.toFloatArray())
			error("Can't cast '$this' to FloatArrayList")
		}
		internal val Any?.intArrayList: IntArrayList get() {
			if (this is IntArray) return IntArrayList(*this)
			if (this is IntArrayList) return this
			if (this is List<*>) return IntArrayList(*this.map { (it as Number).toInt() }.toIntArray())
			error("Can't '$this' cast to intArrayList")
		}

		fun _getBoolean(rawData: Any?, key: String, defaultValue: Boolean): Boolean {
			val value = rawData.get(key)

			return when (value) {
				null -> defaultValue
				is Boolean -> value
				is Number -> value.toFloat() != 0f
				is String -> when (value) {
					"0", "NaN", "", "false", "null", "undefined" -> false
					else -> true
				}
				else -> defaultValue
			}
		}

		fun _getNumber(rawData: Any?, key: String, defaultValue: Float): Float {
			val value = rawData[key] as? Number?
			return if (value != null && value != Double.NaN && value != Float.NaN) {
				value.toFloat()
			} else {
				defaultValue
			}
		}

		fun _getInt(rawData: Any?, key: String, defaultValue: Int): Int {
			val value = rawData[key] as? Number?
			return if (value != null && value != Double.NaN && value != Float.NaN) {
				value.toInt()
			} else {
				defaultValue
			}
		}

		fun _getString(rawData: Any?, key: String, defaultValue: String): String {
			return rawData[key]?.toString() ?: defaultValue
		}

		//private var _objectDataParserInstance = ObjectDataParser()
		///**
		// * - Deprecated, please refer to {@link dragonBones.BaseFactory#parseDragonBonesData()}.
		// * @deprecated
		// * @language en_US
		// */
		///**
		// * - 已废弃，请参考 {@link dragonBones.BaseFactory#parseDragonBonesData()}。
		// * @deprecated
		// * @language zh_CN
		// */
		//fun getInstance(): ObjectDataParser = ObjectDataParser._objectDataParserInstance

	}

	protected var _rawTextureAtlasIndex: Int = 0
	protected val _rawBones: ArrayList<BoneData> = ArrayList()
	protected var _data: DragonBonesData? = null //
	protected var _armature: ArmatureData? = null //
	protected var _bone: BoneData? = null //
	protected var _geometry: GeometryData? = null //
	protected var _slot: SlotData? = null //
	protected var _skin: SkinData? = null //
	protected var _mesh: MeshDisplayData? = null //
	protected var _animation: AnimationData? = null //
	protected var _timeline: TimelineData? = null //
	protected var _rawTextureAtlases: ArrayList<Any?>? = null

	private var _frameValueType: FrameValueType = FrameValueType.STEP
	private var _defaultColorOffset: Int = -1
	//private var _prevClockwise: Int = 0
	private var _prevClockwise: Float = 0f
	private var _prevRotation: Float = 0f
	private var _frameDefaultValue: Float = 0f
	private var _frameValueScale: Float = 1f
	private val _helpMatrixA: Matrix = Matrix()
	private val _helpMatrixB: Matrix = Matrix()
	private val _helpTransform: Transform = Transform()
	private val _helpColorTransform: ColorTransform = ColorTransform()
	private val _helpPoint: Point = Point()
	private val _helpArray: FloatArrayList = FloatArrayList()
	private val _intArray: IntArrayList = IntArrayList()
	private val _floatArray: FloatArrayList = FloatArrayList()
	//private val _frameIntArray:  FloatArrayList = FloatArrayList()
	private val _frameIntArray: IntArrayList = IntArrayList()
	private val _frameFloatArray: FloatArrayList = FloatArrayList()
	private val _frameArray: FloatArrayList = FloatArrayList()
	private val _timelineArray: FloatArrayList = FloatArrayList()
	//private val _colorArray:  FloatArrayList = FloatArrayList()
	private val _colorArray: IntArrayList = IntArrayList()
	private val _cacheRawMeshes: ArrayList<Any> = arrayListOf()
	private val _cacheMeshes: ArrayList<MeshDisplayData> = arrayListOf()
	private val _actionFrames: ArrayList<ActionFrame> = arrayListOf()
	private val _weightSlotPose: LinkedHashMap<String, FloatArrayList> = LinkedHashMap()
	private val _weightBonePoses: LinkedHashMap<String, FloatArrayList> = LinkedHashMap()
	private val _cacheBones: LinkedHashMap<String, ArrayList<BoneData>> = LinkedHashMap()
	private val _slotChildActions: LinkedHashMap<String, ArrayList<ActionData>> = LinkedHashMap()

	private fun _getCurvePoint(
		x1: Float,
		y1: Float,
		x2: Float,
		y2: Float,
		x3: Float,
		y3: Float,
		x4: Float,
		y4: Float,
		t: Float,
		result: Point
	) {
		val l_t = 1f - t
		val powA = l_t * l_t
		val powB = t * t
		val kA = l_t * powA
		val kB = 3.0 * t * powA
		val kC = 3.0 * l_t * powB
		val kD = t * powB

		result.x = (kA * x1 + kB * x2 + kC * x3 + kD * x4).toFloat()
		result.y = (kA * y1 + kB * y2 + kC * y3 + kD * y4).toFloat()
	}

	private fun _samplingEasingCurve(curve: FloatArrayList, samples: FloatArrayList): Boolean {
		val curveCount = curve.size

		if (curveCount % 3 == 1) {
			var stepIndex = -2
			val l = samples.size
			for (i in 0 until samples.size) {
				val t: Float = (i + 1) / (l.toFloat() + 1) // float
				while ((if (stepIndex + 6 < curveCount) curve[stepIndex + 6] else 1f) < t) { // stepIndex + 3 * 2
					stepIndex += 6
				}

				val isInCurve = stepIndex >= 0 && stepIndex + 6 < curveCount
				val x1 = if (isInCurve) curve[stepIndex] else 0f
				val y1 = if (isInCurve) curve[stepIndex + 1] else 0f
				val x2 = curve[stepIndex + 2]
				val y2 = curve[stepIndex + 3]
				val x3 = curve[stepIndex + 4]
				val y3 = curve[stepIndex + 5]
				val x4 = if (isInCurve) curve[stepIndex + 6] else 1f
				val y4 = if (isInCurve) curve[stepIndex + 7] else 1f

				var lower = 0f
				var higher = 1f
				while (higher - lower > 0.0001f) {
					val percentage = (higher + lower) * 0.5f
					this._getCurvePoint(x1, y1, x2, y2, x3, y3, x4, y4, percentage, this._helpPoint)
					if (t - this._helpPoint.x > 0f) {
						lower = percentage
					} else {
						higher = percentage
					}
				}

				samples[i] = this._helpPoint.y
			}

			return true
		} else {
			var stepIndex = 0
			val l = samples.size
			for (i in 0 until samples.size) {
				val t = (i + 1) / (l + 1) // float
				while (curve[stepIndex + 6] < t) { // stepIndex + 3 * 2
					stepIndex += 6
				}

				val x1 = curve[stepIndex]
				val y1 = curve[stepIndex + 1]
				val x2 = curve[stepIndex + 2]
				val y2 = curve[stepIndex + 3]
				val x3 = curve[stepIndex + 4]
				val y3 = curve[stepIndex + 5]
				val x4 = curve[stepIndex + 6]
				val y4 = curve[stepIndex + 7]

				var lower = 0f
				var higher = 1f
				while (higher - lower > 0.0001f) {
					val percentage = (higher + lower) * 0.5f
					this._getCurvePoint(x1, y1, x2, y2, x3, y3, x4, y4, percentage, this._helpPoint)
					if (t - this._helpPoint.x > 0f) {
						lower = percentage
					} else {
						higher = percentage
					}
				}

				samples[i] = this._helpPoint.y.toFloat()
			}

			return false
		}
	}

	private fun _parseActionDataInFrame(rawData: Any?, frameStart: Int, bone: BoneData?, slot: SlotData?) {
		if (DataParser.EVENT in rawData) {
			this._mergeActionFrame(rawData[DataParser.EVENT]!!, frameStart, ActionType.Frame, bone, slot)
		}

		if (DataParser.SOUND in rawData) {
			this._mergeActionFrame(rawData[DataParser.SOUND]!!, frameStart, ActionType.Sound, bone, slot)
		}

		if (DataParser.ACTION in rawData) {
			this._mergeActionFrame(rawData[DataParser.ACTION]!!, frameStart, ActionType.Play, bone, slot)
		}

		if (DataParser.EVENTS in rawData) {
			this._mergeActionFrame(rawData[DataParser.EVENTS]!!, frameStart, ActionType.Frame, bone, slot)
		}

		if (DataParser.ACTIONS in rawData) {
			this._mergeActionFrame(rawData[DataParser.ACTIONS]!!, frameStart, ActionType.Play, bone, slot)
		}
	}

	private fun _mergeActionFrame(rawData: Any?, frameStart: Int, type: ActionType, bone: BoneData?, slot: SlotData?) {
		val actionOffset = this._armature!!.actions.size
		val actions = this._parseActionData(rawData, type, bone, slot)
		var frameIndex = 0
		var frame: ActionFrame? = null

		for (action in actions) {
			this._armature?.addAction(action, false)
		}

		if (this._actionFrames.size == 0) { // First frame.
			frame = ActionFrame()
			frame.frameStart = 0
			this._actionFrames.push(frame)
			frame = null
		}

		for (eachFrame in this._actionFrames) { // Get same frame.
			if (eachFrame.frameStart == frameStart) {
				frame = eachFrame
				break
			} else if (eachFrame.frameStart > frameStart) {
				break
			}

			frameIndex++
		}

		if (frame == null) { // Create and cache frame.
			frame = ActionFrame()
			frame.frameStart = frameStart
			this._actionFrames.splice(frameIndex, 0, frame)
		}

		for (i in 0 until actions.size) { // Cache action offsets.
			frame.actions.push(actionOffset + i)
		}
	}

	protected fun _parseArmature(rawData: Any?, scale: Float): ArmatureData {
		val armature = pool.borrowObject<ArmatureData>()
		armature.name = ObjectDataParser._getString(rawData, DataParser.NAME, "")
		armature.frameRate = ObjectDataParser._getInt(rawData, DataParser.FRAME_RATE, this._data!!.frameRate)
		armature.scale = scale

		if (DataParser.TYPE in rawData && rawData[DataParser.TYPE] is String) {
			armature.type = DataParser._getArmatureType(rawData[DataParser.TYPE]?.toString())
		} else {
			armature.type = ArmatureType[ObjectDataParser._getInt(rawData, DataParser.TYPE, ArmatureType.Armature.id)]
		}

		if (armature.frameRate == 0) { // Data error.
			armature.frameRate = 24
		}

		this._armature = armature

		if (DataParser.CANVAS in rawData) {
			val rawCanvas = rawData[DataParser.CANVAS]
			val canvas = pool.borrowObject<CanvasData>()

			canvas.hasBackground = DataParser.COLOR in rawCanvas

			canvas.color = ObjectDataParser._getInt(rawCanvas, DataParser.COLOR, 0)
			canvas.x = (ObjectDataParser._getInt(rawCanvas, DataParser.X, 0) * armature.scale).toInt()
			canvas.y = (ObjectDataParser._getInt(rawCanvas, DataParser.Y, 0) * armature.scale).toInt()
			canvas.width = (ObjectDataParser._getInt(rawCanvas, DataParser.WIDTH, 0) * armature.scale).toInt()
			canvas.height = (ObjectDataParser._getInt(rawCanvas, DataParser.HEIGHT, 0) * armature.scale).toInt()
			armature.canvas = canvas
		}

		if (DataParser.AABB in rawData) {
			val rawAABB = rawData[DataParser.AABB]
			armature.aabb.x = ObjectDataParser._getNumber(rawAABB, DataParser.X, 0f) * armature.scale
			armature.aabb.y = ObjectDataParser._getNumber(rawAABB, DataParser.Y, 0f) * armature.scale
			armature.aabb.width = ObjectDataParser._getNumber(rawAABB, DataParser.WIDTH, 0f) * armature.scale
			armature.aabb.height = ObjectDataParser._getNumber(rawAABB, DataParser.HEIGHT, 0f) * armature.scale
		}

		if (DataParser.BONE in rawData) {
			val rawBones = rawData[DataParser.BONE]
			for (rawBone in rawBones as List<Any?>) {
				val parentName = ObjectDataParser._getString(rawBone, DataParser.PARENT, "")
				val bone = this._parseBone(rawBone)

				if (parentName.isNotEmpty()) { // Get bone parent.
					val parent = armature.getBone(parentName)
					if (parent != null) {
						bone.parent = parent
					} else { // Cache.
						if (!(parentName in this._cacheBones)) {
							this._cacheBones[parentName] = arrayListOf()
						}

						this._cacheBones[parentName]?.push(bone)
					}
				}

				if (bone.name in this._cacheBones) {
					for (child in this._cacheBones[bone.name]!!) {
						child.parent = bone
					}

					this._cacheBones.remove(bone.name)
				}

				armature.addBone(bone)
				this._rawBones.push(bone) // Cache raw bones sort.
			}
		}

		if (DataParser.IK in rawData) {
			val rawIKS = rawData[DataParser.IK] as List<Map<String, Any?>>
			for (rawIK in rawIKS) {
				val constraint = this._parseIKConstraint(rawIK)
				if (constraint != null) {
					armature.addConstraint(constraint)
				}
			}
		}

		armature.sortBones()

		if (DataParser.SLOT in rawData) {
			var zOrder = 0
			val rawSlots = rawData[DataParser.SLOT] as List<Map<String, Any?>>
			for (rawSlot in rawSlots) {
				armature.addSlot(this._parseSlot(rawSlot, zOrder++))
			}
		}

		if (DataParser.SKIN in rawData) {
			val rawSkins = rawData[DataParser.SKIN] as List<Any?>
			for (rawSkin in rawSkins) {
				armature.addSkin(this._parseSkin(rawSkin))
			}
		}

		if (DataParser.PATH_CONSTRAINT in rawData) {
			val rawPaths = rawData[DataParser.PATH_CONSTRAINT] as List<Any?>
			for (rawPath in rawPaths) {
				val constraint = this._parsePathConstraint(rawPath)
				if (constraint != null) {
					armature.addConstraint(constraint)
				}
			}
		}

		//for (var i = 0, l = this._cacheRawMeshes.length; i < l; ++i) { // Link mesh.
		for (i in 0 until this._cacheRawMeshes.length) {
			val rawData = this._cacheRawMeshes[i]
			val shareName = ObjectDataParser._getString(rawData, DataParser.SHARE, "")
			if (shareName.isEmpty()) {
				continue
			}

			var skinName = ObjectDataParser._getString(rawData, DataParser.SKIN, DataParser.DEFAULT_NAME)
			if (skinName.isEmpty()) { //
				skinName = DataParser.DEFAULT_NAME
			}

			val shareMesh = armature.getMesh(skinName, "", shareName) // TODO slot;
			if (shareMesh == null) {
				continue // Error.
			}

			val mesh = this._cacheMeshes[i]
			mesh.geometry.shareFrom(shareMesh.geometry)
		}

		if (DataParser.ANIMATION in rawData) {
			val rawAnimations = rawData[DataParser.ANIMATION] as List<Any?>
			for (rawAnimation in rawAnimations) {
				val animation = this._parseAnimation(rawAnimation)
				armature.addAnimation(animation)
			}
		}

		if (DataParser.DEFAULT_ACTIONS in rawData) {
			val actions = this._parseActionData(rawData[DataParser.DEFAULT_ACTIONS], ActionType.Play, null, null)
			for (action in actions) {
				armature.addAction(action, true)

				if (action.type == ActionType.Play) { // Set default animation from default action.
					val animation = armature.getAnimation(action.name)
					if (animation != null) {
						armature.defaultAnimation = animation
					}
				}
			}
		}

		if (DataParser.ACTIONS in rawData) {
			val actions = this._parseActionData(rawData[DataParser.ACTIONS], ActionType.Play, null, null)
			for (action in actions) {
				armature.addAction(action, false)
			}
		}

		// Clear helper.
		this._rawBones.lengthSet = 0
		this._cacheRawMeshes.length = 0
		this._cacheMeshes.length = 0
		this._armature = null

		this._weightSlotPose.clear()
		this._weightBonePoses.clear()
		this._cacheBones.clear()
		this._slotChildActions.clear()

		return armature
	}

	protected fun _parseBone(rawData: Any?): BoneData {
		val isSurface: Boolean

		if (DataParser.TYPE in rawData && rawData[DataParser.TYPE] is String) {
			isSurface = DataParser._getBoneTypeIsSurface(rawData[DataParser.TYPE]?.toString())
		} else {
			isSurface = ObjectDataParser._getInt(rawData, DataParser.TYPE, 0) == 1
		}

		if (!isSurface) {
			val scale = this._armature!!.scale
			val bone = pool.borrowObject<BoneData>()
			bone.inheritTranslation = ObjectDataParser._getBoolean(rawData, DataParser.INHERIT_TRANSLATION, true)
			bone.inheritRotation = ObjectDataParser._getBoolean(rawData, DataParser.INHERIT_ROTATION, true)
			bone.inheritScale = ObjectDataParser._getBoolean(rawData, DataParser.INHERIT_SCALE, true)
			bone.inheritReflection = ObjectDataParser._getBoolean(rawData, DataParser.INHERIT_REFLECTION, true)
			bone.length = ObjectDataParser._getNumber(rawData, DataParser.LENGTH, 0f) * scale
			bone.alpha = ObjectDataParser._getNumber(rawData, DataParser.ALPHA, 1f)
			bone.name = ObjectDataParser._getString(rawData, DataParser.NAME, "")

			if (DataParser.TRANSFORM in rawData) {
				this._parseTransform(rawData[DataParser.TRANSFORM], bone.transform, scale)
			}

			return bone
		} else {

			val surface = pool.borrowObject<SurfaceData>()
			surface.alpha = ObjectDataParser._getNumber(rawData, DataParser.ALPHA, 1f)
			surface.name = ObjectDataParser._getString(rawData, DataParser.NAME, "")
			surface.segmentX = ObjectDataParser._getInt(rawData, DataParser.SEGMENT_X, 0)
			surface.segmentY = ObjectDataParser._getInt(rawData, DataParser.SEGMENT_Y, 0)
			this._parseGeometry(rawData, surface.geometry)

			return surface
		}
	}

	protected fun _parseIKConstraint(rawData: Any?): ConstraintData? {
		val bone = this._armature?.getBone(ObjectDataParser._getString(rawData, DataParser.BONE, "")) ?: return null
		val target = this._armature?.getBone(ObjectDataParser._getString(rawData, DataParser.TARGET, "")) ?: return null

		val chain = ObjectDataParser._getInt(rawData, DataParser.CHAIN, 0)
		val constraint = pool.borrowObject<IKConstraintData>()
		constraint.scaleEnabled = ObjectDataParser._getBoolean(rawData, DataParser.SCALE, false)
		constraint.bendPositive = ObjectDataParser._getBoolean(rawData, DataParser.BEND_POSITIVE, true)
		constraint.weight = ObjectDataParser._getNumber(rawData, DataParser.WEIGHT, 1f)
		constraint.name = ObjectDataParser._getString(rawData, DataParser.NAME, "")
		constraint.type = ConstraintType.IK
		constraint.target = target

		if (chain > 0 && bone.parent != null) {
			constraint.root = bone.parent
			constraint.bone = bone
		} else {
			constraint.root = bone
			constraint.bone = null
		}

		return constraint
	}

	protected fun _parsePathConstraint(rawData: Any?): ConstraintData? {
		val target = this._armature?.getSlot(ObjectDataParser._getString(rawData, DataParser.TARGET, "")) ?: return null
		val defaultSkin = this._armature?.defaultSkin ?: return null
		//TODO
		val targetDisplay = defaultSkin.getDisplay(
			target.name,
			ObjectDataParser._getString(rawData, DataParser.TARGET_DISPLAY, target.name)
		)
		if (targetDisplay == null || !(targetDisplay is PathDisplayData)) {
			return null
		}

		val bones = rawData[DataParser.BONES] as? List<String>?
		if (bones == null || bones.isEmpty()) {
			return null
		}

		val constraint = pool.borrowObject<PathConstraintData>()
		constraint.name = ObjectDataParser._getString(rawData, DataParser.NAME, "")
		constraint.type = ConstraintType.Path
		constraint.pathSlot = target
		constraint.pathDisplayData = targetDisplay
		constraint.target = target.parent
		constraint.positionMode =
				DataParser._getPositionMode(ObjectDataParser._getString(rawData, DataParser.POSITION_MODE, ""))
		constraint.spacingMode =
				DataParser._getSpacingMode(ObjectDataParser._getString(rawData, DataParser.SPACING_MODE, ""))
		constraint.rotateMode =
				DataParser._getRotateMode(ObjectDataParser._getString(rawData, DataParser.ROTATE_MODE, ""))
		constraint.position = ObjectDataParser._getNumber(rawData, DataParser.POSITION, 0f)
		constraint.spacing = ObjectDataParser._getNumber(rawData, DataParser.SPACING, 0f)
		constraint.rotateOffset = ObjectDataParser._getNumber(rawData, DataParser.ROTATE_OFFSET, 0f)
		constraint.rotateMix = ObjectDataParser._getNumber(rawData, DataParser.ROTATE_MIX, 1f)
		constraint.translateMix = ObjectDataParser._getNumber(rawData, DataParser.TRANSLATE_MIX, 1f)
		//
		for (boneName in bones) {
			val bone = this._armature?.getBone(boneName)
			if (bone != null) {
				constraint.AddBone(bone)

				if (constraint.root == null) {
					constraint.root = bone
				}
			}
		}

		return constraint
	}

	protected fun _parseSlot(rawData: Any?, zOrder: Int): SlotData {
		val slot = pool.borrowObject<SlotData>()
		slot.displayIndex = ObjectDataParser._getInt(rawData, DataParser.DISPLAY_INDEX, 0)
		slot.zOrder = zOrder
		slot.zIndex = ObjectDataParser._getInt(rawData, DataParser.Z_INDEX, 0)
		slot.alpha = ObjectDataParser._getNumber(rawData, DataParser.ALPHA, 1f)
		slot.name = ObjectDataParser._getString(rawData, DataParser.NAME, "")
		slot.parent = this._armature?.getBone(ObjectDataParser._getString(rawData, DataParser.PARENT, "")) //

		if (DataParser.BLEND_MODE in rawData && rawData[DataParser.BLEND_MODE] is String) {
			slot.blendMode = DataParser._getBlendMode(rawData[DataParser.BLEND_MODE]?.toString())
		} else {
			slot.blendMode = BlendMode[ObjectDataParser._getInt(rawData, DataParser.BLEND_MODE, BlendMode.Normal.id)]
		}

		if (DataParser.COLOR in rawData) {
			slot.color = SlotData.createColor()
			this._parseColorTransform(rawData[DataParser.COLOR] as Map<String, Any?>, slot.color!!)
		} else {
			slot.color = pool.DEFAULT_COLOR
		}

		if (DataParser.ACTIONS in rawData) {
			this._slotChildActions[slot.name] =
					this._parseActionData(rawData[DataParser.ACTIONS], ActionType.Play, null, null)
		}

		return slot
	}

	protected fun _parseSkin(rawData: Any?): SkinData {
		val skin = pool.borrowObject<SkinData>()
		skin.name = ObjectDataParser._getString(rawData, DataParser.NAME, DataParser.DEFAULT_NAME)

		if (skin.name.isEmpty()) {
			skin.name = DataParser.DEFAULT_NAME
		}

		if (DataParser.SLOT in rawData) {
			val rawSlots = rawData[DataParser.SLOT]
			this._skin = skin

			for (rawSlot in rawSlots.dynList) {
				val slotName = ObjectDataParser._getString(rawSlot, DataParser.NAME, "")
				val slot = this._armature?.getSlot(slotName)

				if (slot != null) {
					this._slot = slot

					if (DataParser.DISPLAY in rawSlot) {
						val rawDisplays = rawSlot[DataParser.DISPLAY]
						for (rawDisplay in rawDisplays.dynList) {
							if (rawDisplay != null) {
								skin.addDisplay(slotName, this._parseDisplay(rawDisplay))
							} else {
								skin.addDisplay(slotName, null)
							}
						}
					}

					this._slot = null //
				}
			}

			this._skin = null //
		}

		return skin
	}

	protected fun _parseDisplay(rawData: Any?): DisplayData? {
		val name = ObjectDataParser._getString(rawData, DataParser.NAME, "")
		val path = ObjectDataParser._getString(rawData, DataParser.PATH, "")
		var type = DisplayType.Image
		var display: DisplayData? = null

		if (DataParser.TYPE in rawData && rawData[DataParser.TYPE] is String) {
			type = DataParser._getDisplayType(rawData[DataParser.TYPE]?.toString())
		} else {
			type = DisplayType[ObjectDataParser._getInt(rawData, DataParser.TYPE, type.id)]
		}

		when (type) {
			DisplayType.Image -> {
				display = pool.borrowObject<ImageDisplayData>()
				val imageDisplay = display
				imageDisplay.name = name
				imageDisplay.path = if (path.length > 0) path else name
				this._parsePivot(rawData, imageDisplay)
			}
			DisplayType.Armature -> {
				display = pool.borrowObject<ArmatureDisplayData>()
				val armatureDisplay = display
				armatureDisplay.name = name
				armatureDisplay.path = if (path.length > 0) path else name
				armatureDisplay.inheritAnimation = true

				if (DataParser.ACTIONS in rawData) {
					val actions = this._parseActionData(rawData[DataParser.ACTIONS], ActionType.Play, null, null)
					for (action in actions) {
						armatureDisplay.addAction(action)
					}
				} else if (this._slot?.name in this._slotChildActions) {
					val displays = this._skin?.getDisplays(this._slot?.name)
					if (if (displays == null) this._slot!!.displayIndex == 0 else this._slot!!.displayIndex == displays.length) {
						for (action in this._slotChildActions[this._slot?.name]!!) {
							armatureDisplay.addAction(action)
						}

						this._slotChildActions.remove(this._slot?.name)
					}
				}
			}
			DisplayType.Mesh -> {
				val meshDisplay = pool.borrowObject<MeshDisplayData>()
				display = meshDisplay
				meshDisplay.geometry.inheritDeform =
						ObjectDataParser._getBoolean(rawData, DataParser.INHERIT_DEFORM, true)
				meshDisplay.name = name
				meshDisplay.path = if (path.isNotEmpty()) path else name

				if (DataParser.SHARE in rawData) {
					meshDisplay.geometry.data = this._data
					this._cacheRawMeshes.push(rawData!!)
					this._cacheMeshes.push(meshDisplay)
				} else {
					this._parseMesh(rawData, meshDisplay)
				}
			}
			DisplayType.BoundingBox -> {
				val boundingBox = this._parseBoundingBox(rawData)
				if (boundingBox != null) {
					val boundingBoxDisplay = pool.borrowObject<BoundingBoxDisplayData>()
					display = boundingBoxDisplay
					boundingBoxDisplay.name = name
					boundingBoxDisplay.path = if (path.isNotEmpty()) path else name
					boundingBoxDisplay.boundingBox = boundingBox
				}
			}
			DisplayType.Path -> {
				val rawCurveLengths = rawData[DataParser.LENGTHS].FloatArray
				val pathDisplay = pool.borrowObject<PathDisplayData>()
				display = pathDisplay
				pathDisplay.closed = ObjectDataParser._getBoolean(rawData, DataParser.CLOSED, false)
				pathDisplay.constantSpeed = ObjectDataParser._getBoolean(rawData, DataParser.CONSTANT_SPEED, false)
				pathDisplay.name = name
				pathDisplay.path = if (path.isNotEmpty()) path else name
				pathDisplay.curveLengths = FloatArray(rawCurveLengths.size)

				//for (var i = 0, l = rawCurveLengths.length; i < l; ++i) {
				for (i in 0 until rawCurveLengths.size) {
					pathDisplay.curveLengths[i] = rawCurveLengths[i]
				}

				this._parsePath(rawData, pathDisplay)
			}
			else -> {
			}
		}

		if (display != null && DataParser.TRANSFORM in rawData) {
			this._parseTransform(rawData[DataParser.TRANSFORM], display.transform, this._armature!!.scale)
		}

		return display
	}

	protected fun _parsePath(rawData: Any?, display: PathDisplayData) {
		this._parseGeometry(rawData, display.geometry)
	}

	protected fun _parsePivot(rawData: Any?, display: ImageDisplayData) {
		if (DataParser.PIVOT in rawData) {
			val rawPivot = rawData[DataParser.PIVOT]
			display.pivot.x = ObjectDataParser._getNumber(rawPivot, DataParser.X, 0f).toFloat()
			display.pivot.y = ObjectDataParser._getNumber(rawPivot, DataParser.Y, 0f).toFloat()
		} else {
			display.pivot.x = 0.5f
			display.pivot.y = 0.5f
		}
	}

	protected fun _parseMesh(rawData: Any?, mesh: MeshDisplayData) {
		this._parseGeometry(rawData, mesh.geometry)

		if (DataParser.WEIGHTS in rawData) { // Cache pose data.
			val rawSlotPose = rawData[DataParser.SLOT_POSE].FloatArrayList
			val rawBonePoses = rawData[DataParser.BONE_POSE].FloatArrayList
			val meshName = "" + this._skin?.name + "_" + this._slot?.name + "_" + mesh.name
			this._weightSlotPose[meshName] = rawSlotPose
			this._weightBonePoses[meshName] = rawBonePoses
		}
	}

	protected fun _parseBoundingBox(rawData: Any?): BoundingBoxData? {
		var boundingBox: BoundingBoxData? = null
		var type = BoundingBoxType.Rectangle

		if (DataParser.SUB_TYPE in rawData && rawData[DataParser.SUB_TYPE] is String) {
			type = DataParser._getBoundingBoxType(rawData[DataParser.SUB_TYPE]?.toString())
		} else {
			type = BoundingBoxType[ObjectDataParser._getInt(rawData, DataParser.SUB_TYPE, type.id)]
		}

		when (type) {
			BoundingBoxType.Rectangle -> {
				boundingBox = pool.borrowObject<RectangleBoundingBoxData>()
			}

			BoundingBoxType.Ellipse -> {
				boundingBox = pool.borrowObject<EllipseBoundingBoxData>()
			}

			BoundingBoxType.Polygon -> {
				boundingBox = this._parsePolygonBoundingBox(rawData)
			}
			else -> {
			}
		}

		if (boundingBox != null) {
			boundingBox.color = ObjectDataParser._getInt(rawData, DataParser.COLOR, 0x000000)
			if (boundingBox.type == BoundingBoxType.Rectangle || boundingBox.type == BoundingBoxType.Ellipse) {
				boundingBox.width = ObjectDataParser._getNumber(rawData, DataParser.WIDTH, 0f)
				boundingBox.height = ObjectDataParser._getNumber(rawData, DataParser.HEIGHT, 0f)
			}
		}

		return boundingBox
	}

	protected fun _parsePolygonBoundingBox(rawData: Any?): PolygonBoundingBoxData {
		val polygonBoundingBox = pool.borrowObject<PolygonBoundingBoxData>()

		if (DataParser.VERTICES in rawData) {
			val scale = this._armature!!.scale
			val rawVertices = rawData[DataParser.VERTICES] .FloatArray
			polygonBoundingBox.vertices = FloatArray(rawVertices.size)
			val vertices = polygonBoundingBox.vertices

			//for (var i = 0, l = rawVertices.length; i < l; i += 2) {
			for (i in 0 until rawVertices.size step 2) {
				val x = rawVertices[i] * scale
				val y = rawVertices[i + 1] * scale
				vertices[i] = x
				vertices[i + 1] = y

				// AABB.
				if (i == 0) {
					polygonBoundingBox.x = x
					polygonBoundingBox.y = y
					polygonBoundingBox.width = x
					polygonBoundingBox.height = y
				} else {
					if (x < polygonBoundingBox.x) {
						polygonBoundingBox.x = x
					} else if (x > polygonBoundingBox.width) {
						polygonBoundingBox.width = x
					}

					if (y < polygonBoundingBox.y) {
						polygonBoundingBox.y = y
					} else if (y > polygonBoundingBox.height) {
						polygonBoundingBox.height = y
					}
				}
			}

			polygonBoundingBox.width -= polygonBoundingBox.x
			polygonBoundingBox.height -= polygonBoundingBox.y
		} else {
			console.warn("Data error.\n Please reexport DragonBones Data to fixed the bug.")
		}

		return polygonBoundingBox
	}

	protected open fun _parseAnimation(rawData: Any?): AnimationData {
		val animation = pool.borrowObject<AnimationData>()
		animation.blendType =
				DataParser._getAnimationBlendType(ObjectDataParser._getString(rawData, DataParser.BLEND_TYPE, ""))
		animation.frameCount = ObjectDataParser._getInt(rawData, DataParser.DURATION, 0)
		animation.playTimes = ObjectDataParser._getInt(rawData, DataParser.PLAY_TIMES, 1)
		animation.duration = animation.frameCount.toFloat() / this._armature!!.frameRate.toFloat() // float
		animation.fadeInTime = ObjectDataParser._getNumber(rawData, DataParser.FADE_IN_TIME, 0f)
		animation.scale = ObjectDataParser._getNumber(rawData, DataParser.SCALE, 1f)
		animation.name = ObjectDataParser._getString(rawData, DataParser.NAME, DataParser.DEFAULT_NAME)

		if (animation.name.length == 0) {
			animation.name = DataParser.DEFAULT_NAME
		}

		animation.frameIntOffset = this._frameIntArray.length
		animation.frameFloatOffset = this._frameFloatArray.length
		animation.frameOffset = this._frameArray.length
		this._animation = animation

		if (DataParser.FRAME in rawData) {
			val rawFrames = rawData[DataParser.FRAME] as List<Any?>
			val keyFrameCount = rawFrames.size

			if (keyFrameCount > 0) {
				//for (var i = 0, frameStart = 0; i < keyFrameCount; ++i) {
				var frameStart = 0
				for (i in 0 until keyFrameCount) {
					val rawFrame = rawFrames[i]
					this._parseActionDataInFrame(rawFrame, frameStart, null, null)
					frameStart += ObjectDataParser._getInt(rawFrame, DataParser.DURATION, 1)
				}
			}
		}

		if (DataParser.Z_ORDER in rawData) {
			this._animation!!.zOrderTimeline = this._parseTimeline(
				rawData[DataParser.Z_ORDER], null, DataParser.FRAME, TimelineType.ZOrder,
				FrameValueType.STEP, 0,
				this::_parseZOrderFrame
			)
		}

		if (DataParser.BONE in rawData) {
			val rawTimelines = rawData[DataParser.BONE] as List<Any?>
			for (rawTimeline in rawTimelines) {
				this._parseBoneTimeline(rawTimeline)
			}
		}

		if (DataParser.SLOT in rawData) {
			val rawTimelines = rawData[DataParser.SLOT] as List<Any?>
			for (rawTimeline in rawTimelines) {
				this._parseSlotTimeline(rawTimeline)
			}
		}

		if (DataParser.FFD in rawData) {
			val rawTimelines = rawData[DataParser.FFD] as List<Any?>
			for (rawTimeline in rawTimelines) {
				var skinName = ObjectDataParser._getString(rawTimeline, DataParser.SKIN, DataParser.DEFAULT_NAME)
				val slotName = ObjectDataParser._getString(rawTimeline, DataParser.SLOT, "")
				val displayName = ObjectDataParser._getString(rawTimeline, DataParser.NAME, "")

				if (skinName.isEmpty()) { //
					skinName = DataParser.DEFAULT_NAME
				}

				this._slot = this._armature?.getSlot(slotName)
				this._mesh = this._armature?.getMesh(skinName, slotName, displayName)
				if (this._slot == null || this._mesh == null) {
					continue
				}

				val timeline = this._parseTimeline(
					rawTimeline, null, DataParser.FRAME, TimelineType.SlotDeform,
					FrameValueType.FLOAT, 0,
					this::_parseSlotDeformFrame
				)

				if (timeline != null) {
					this._animation?.addSlotTimeline(slotName, timeline)
				}

				this._slot = null //
				this._mesh = null //
			}
		}

		if (DataParser.IK in rawData) {
			val rawTimelines = rawData[DataParser.IK] as List<Any?>
			for (rawTimeline in rawTimelines) {
				val constraintName = ObjectDataParser._getString(rawTimeline, DataParser.NAME, "")
				@Suppress("UNUSED_VARIABLE")
				val constraint = this._armature!!.getConstraint(constraintName) ?: continue

				val timeline = this._parseTimeline(
					rawTimeline, null, DataParser.FRAME, TimelineType.IKConstraint,
					FrameValueType.INT, 2,
					this::_parseIKConstraintFrame
				)

				if (timeline != null) {
					this._animation?.addConstraintTimeline(constraintName, timeline)
				}
			}
		}

		if (this._actionFrames.length > 0) {
			this._animation!!.actionTimeline = this._parseTimeline(
				null, this._actionFrames as ArrayList<Any?>?, "", TimelineType.Action,
				FrameValueType.STEP, 0,
				this::_parseActionFrameRaw
			)
			this._actionFrames.length = 0
		}

		if (DataParser.TIMELINE in rawData) {
			val rawTimelines = rawData[DataParser.TIMELINE]
			loop@ for (rawTimeline in rawTimelines.dynList) {
				val timelineType =
					TimelineType[ObjectDataParser._getInt(rawTimeline, DataParser.TYPE, TimelineType.Action.id)]
				val timelineName = ObjectDataParser._getString(rawTimeline, DataParser.NAME, "")
				var timeline: TimelineData? = null

				when (timelineType) {
					TimelineType.Action -> {
						// TODO
					}

					TimelineType.SlotDisplay, // TODO
					TimelineType.SlotZIndex,
					TimelineType.BoneAlpha,
					TimelineType.SlotAlpha,
					TimelineType.AnimationProgress,
					TimelineType.AnimationWeight -> {
						if (
							timelineType == TimelineType.SlotDisplay
						) {
							this._frameValueType = FrameValueType.STEP
							this._frameValueScale = 1f
						} else {
							this._frameValueType = FrameValueType.INT

							if (timelineType == TimelineType.SlotZIndex) {
								this._frameValueScale = 1f
							} else if (
								timelineType == TimelineType.AnimationProgress ||
								timelineType == TimelineType.AnimationWeight
							) {
								this._frameValueScale = 10000f
							} else {
								this._frameValueScale = 100f
							}
						}

						if (
							timelineType == TimelineType.BoneAlpha ||
							timelineType == TimelineType.SlotAlpha ||
							timelineType == TimelineType.AnimationWeight
						) {
							this._frameDefaultValue = 1f
						} else {
							this._frameDefaultValue = 0f
						}

						if (timelineType == TimelineType.AnimationProgress && animation.blendType != AnimationBlendType.None) {
							timeline = pool.borrowObject<AnimationTimelineData>()
							val animaitonTimeline = timeline
							animaitonTimeline.x = ObjectDataParser._getNumber(rawTimeline, DataParser.X, 0f)
							animaitonTimeline.y = ObjectDataParser._getNumber(rawTimeline, DataParser.Y, 0f)
						}

						timeline = this._parseTimeline(
							rawTimeline, null, DataParser.FRAME, timelineType,
							this._frameValueType, 1,
							this::_parseSingleValueFrame, timeline
						)
					}

					TimelineType.BoneTranslate,
					TimelineType.BoneRotate,
					TimelineType.BoneScale,
					TimelineType.IKConstraint,
					TimelineType.AnimationParameter -> {
						if (
							timelineType == TimelineType.IKConstraint ||
							timelineType == TimelineType.AnimationParameter
						) {
							this._frameValueType = FrameValueType.INT

							if (timelineType == TimelineType.AnimationParameter) {
								this._frameValueScale = 10000f
							} else {
								this._frameValueScale = 100f
							}
						} else {
							if (timelineType == TimelineType.BoneRotate) {
								this._frameValueScale = Transform.DEG_RAD
							} else {
								this._frameValueScale = 1f
							}

							this._frameValueType = FrameValueType.FLOAT
						}

						if (
							timelineType == TimelineType.BoneScale ||
							timelineType == TimelineType.IKConstraint
						) {
							this._frameDefaultValue = 1f
						} else {
							this._frameDefaultValue = 0f
						}

						timeline = this._parseTimeline(
							rawTimeline, null, DataParser.FRAME, timelineType,
							this._frameValueType, 2,
							this::_parseDoubleValueFrame
						)
					}

					TimelineType.ZOrder -> {
						// TODO
					}

					TimelineType.Surface -> {
						val surface = this._armature?.getBone(timelineName) ?: continue@loop

						this._geometry = surface.geometry
						timeline = this._parseTimeline(
							rawTimeline, null, DataParser.FRAME, timelineType,
							FrameValueType.FLOAT, 0,
							this::_parseDeformFrame
						)

						this._geometry = null //
					}

					TimelineType.SlotDeform -> {
						this._geometry = null //
						for (skinName in this._armature!!.skins.keys) {
							val skin = this._armature!!.skins.getNull(skinName)
							for (slontName in skin!!.displays.keys) {
								val displays = skin.displays.getNull(slontName)!!
								for (display in displays) {
									if (display != null && display.name == timelineName) {
										this._geometry = (display as MeshDisplayData).geometry
										break
									}
								}
							}
						}

						if (this._geometry == null) {
							continue@loop
						}

						timeline = this._parseTimeline(
							rawTimeline, null, DataParser.FRAME, timelineType,
							FrameValueType.FLOAT, 0,
							this::_parseDeformFrame
						)

						this._geometry = null //
					}

					TimelineType.SlotColor -> {
						timeline = this._parseTimeline(
							rawTimeline, null, DataParser.FRAME, timelineType,
							FrameValueType.INT, 1,
							this::_parseSlotColorFrame
						)
					}
					else -> {
					}
				}

				if (timeline != null) {
					when (timelineType) {
						TimelineType.Action -> {
							// TODO
						}

						TimelineType.ZOrder -> {
							// TODO
						}

						TimelineType.BoneTranslate,
						TimelineType.BoneRotate,
						TimelineType.BoneScale,
						TimelineType.Surface,
						TimelineType.BoneAlpha -> {
							this._animation?.addBoneTimeline(timelineName, timeline)
						}

						TimelineType.SlotDisplay,
						TimelineType.SlotColor,
						TimelineType.SlotDeform,
						TimelineType.SlotZIndex,
						TimelineType.SlotAlpha -> {
							this._animation?.addSlotTimeline(timelineName, timeline)
						}

						TimelineType.IKConstraint -> {
							this._animation?.addConstraintTimeline(timelineName, timeline)
						}

						TimelineType.AnimationProgress,
						TimelineType.AnimationWeight,
						TimelineType.AnimationParameter -> {
							this._animation?.addAnimationTimeline(timelineName, timeline)
						}
						else -> {
						}
					}
				}
			}
		}

		this._animation = null //

		return animation
	}

	protected fun _parseTimeline(
		rawData: Any?, rawFrames: ArrayList<Any?>?, framesKey: String,
		timelineType: TimelineType, frameValueType: FrameValueType, frameValueCount: Int,
		frameParser: (rawData: Any?, frameStart: Int, frameCount: Int) -> Int, timeline: TimelineData? = null
	): TimelineData? {
		var timeline = timeline
		val frameParser = frameParser
		var rawFrames = rawFrames
		if (rawData != null && framesKey.isNotEmpty() && framesKey in rawData) {
			rawFrames = rawData[framesKey] as ArrayList<Any?>?
		}

		if (rawFrames == null) {
			return null
		}

		val keyFrameCount = rawFrames.length
		if (keyFrameCount == 0) {
			return null
		}

		val frameIntArrayLength = this._frameIntArray.length
		val frameFloatArrayLength = this._frameFloatArray.length
		val timelineOffset = this._timelineArray.length
		if (timeline == null) {
			timeline = pool.borrowObject<TimelineData>()
		}

		timeline.type = timelineType
		timeline.offset = timelineOffset
		this._frameValueType = frameValueType
		this._timeline = timeline
		this._timelineArray.length += 1 + 1 + 1 + 1 + 1 + keyFrameCount

		if (rawData != null) {
			this._timelineArray[timelineOffset + BinaryOffset.TimelineScale] =
					round(ObjectDataParser._getNumber(rawData, DataParser.SCALE, 1f) * 100)
			this._timelineArray[timelineOffset + BinaryOffset.TimelineOffset] =
					round(ObjectDataParser._getNumber(rawData, DataParser.OFFSET, 0f) * 100)
		} else {
			this._timelineArray[timelineOffset + BinaryOffset.TimelineScale] = 100f
			this._timelineArray[timelineOffset + BinaryOffset.TimelineOffset] = 0f
		}

		this._timelineArray[timelineOffset + BinaryOffset.TimelineKeyFrameCount] = keyFrameCount.toFloat()
		this._timelineArray[timelineOffset + BinaryOffset.TimelineFrameValueCount] = frameValueCount.toFloat()

		when (this._frameValueType) {
			FrameValueType.STEP -> {
				this._timelineArray[timelineOffset + BinaryOffset.TimelineFrameValueOffset] = 0f
			}

			FrameValueType.INT -> {
				this._timelineArray[timelineOffset + BinaryOffset.TimelineFrameValueOffset] =
						(frameIntArrayLength - this._animation!!.frameIntOffset).toFloat()
			}

			FrameValueType.FLOAT -> {
				this._timelineArray[timelineOffset + BinaryOffset.TimelineFrameValueOffset] =
						(frameFloatArrayLength - this._animation!!.frameFloatOffset).toFloat()
			}
		}

		if (keyFrameCount == 1) { // Only one frame.
			timeline.frameIndicesOffset = -1
			this._timelineArray[timelineOffset + BinaryOffset.TimelineFrameOffset + 0] =
					(frameParser(rawFrames[0], 0, 0) - this._animation!!.frameOffset).toFloat()
		} else {
			val totalFrameCount = this._animation!!.frameCount + 1 // One more frame than animation.
			val frameIndices = this._data!!.frameIndices
			val frameIndicesOffset = frameIndices.length
			frameIndices.length += totalFrameCount
			timeline.frameIndicesOffset = frameIndicesOffset

			//for (var i = 0, iK = 0, frameStart = 0, frameCount = 0;i < totalFrameCount; ++i) {
			var iK = 0
			var frameStart = 0
			var frameCount = 0
			for (i in 0 until totalFrameCount) {
				if (frameStart + frameCount <= i && iK < keyFrameCount) {
					val rawFrame = rawFrames[iK]
					frameStart = i // frame.frameStart;

					if (iK == keyFrameCount - 1) {
						frameCount = this._animation!!.frameCount - frameStart
					} else {
						if (rawFrame is ActionFrame) {
							frameCount = this._actionFrames[iK + 1].frameStart - frameStart
						} else {
							frameCount = ObjectDataParser._getNumber(rawFrame, DataParser.DURATION, 1f).toInt()
						}
					}

					this._timelineArray[timelineOffset + BinaryOffset.TimelineFrameOffset + iK] =
							(frameParser(rawFrame, frameStart, frameCount) - this._animation!!.frameOffset).toFloat()
					iK++
				}

				frameIndices[frameIndicesOffset + i] = iK - 1
			}
		}

		this._timeline = null //

		return timeline
	}

	protected fun _parseBoneTimeline(rawData: Any?) {
		val bone = this._armature?.getBone(ObjectDataParser._getString(rawData, DataParser.NAME, "")) ?: return

		this._bone = bone
		this._slot = this._armature?.getSlot(this._bone?.name)

		if (DataParser.TRANSLATE_FRAME in rawData) {
			this._frameDefaultValue = 0f
			this._frameValueScale = 1f
			val timeline = this._parseTimeline(
				rawData, null, DataParser.TRANSLATE_FRAME, TimelineType.BoneTranslate,
				FrameValueType.FLOAT, 2,
				this::_parseDoubleValueFrame
			)

			if (timeline != null) {
				this._animation?.addBoneTimeline(bone.name, timeline)
			}
		}

		if (DataParser.ROTATE_FRAME in rawData) {
			this._frameDefaultValue = 0f
			this._frameValueScale = 1f
			val timeline = this._parseTimeline(
				rawData, null, DataParser.ROTATE_FRAME, TimelineType.BoneRotate,
				FrameValueType.FLOAT, 2,
				this::_parseBoneRotateFrame
			)

			if (timeline != null) {
				this._animation?.addBoneTimeline(bone.name, timeline)
			}
		}

		if (DataParser.SCALE_FRAME in rawData) {
			this._frameDefaultValue = 1f
			this._frameValueScale = 1f
			val timeline = this._parseTimeline(
				rawData, null, DataParser.SCALE_FRAME, TimelineType.BoneScale,
				FrameValueType.FLOAT, 2,
				this::_parseBoneScaleFrame
			)

			if (timeline != null) {
				this._animation?.addBoneTimeline(bone.name, timeline)
			}
		}

		if (DataParser.FRAME in rawData) {
			val timeline = this._parseTimeline(
				rawData, null, DataParser.FRAME, TimelineType.BoneAll,
				FrameValueType.FLOAT, 6,
				this::_parseBoneAllFrame
			)

			if (timeline != null) {
				this._animation?.addBoneTimeline(bone.name, timeline)
			}
		}

		this._bone = null //
		this._slot = null //
	}

	protected fun _parseSlotTimeline(rawData: Any?) {
		val slot = this._armature?.getSlot(ObjectDataParser._getString(rawData, DataParser.NAME, "")) ?: return

		val displayTimeline: TimelineData?
		val colorTimeline: TimelineData?
		this._slot = slot

		if (DataParser.DISPLAY_FRAME in rawData) {
			displayTimeline = this._parseTimeline(
				rawData, null, DataParser.DISPLAY_FRAME, TimelineType.SlotDisplay,
				FrameValueType.STEP, 0,
				this::_parseSlotDisplayFrame
			)
		} else {
			displayTimeline = this._parseTimeline(
				rawData, null, DataParser.FRAME, TimelineType.SlotDisplay,
				FrameValueType.STEP, 0,
				this::_parseSlotDisplayFrame
			)
		}

		if (DataParser.COLOR_FRAME in rawData) {
			colorTimeline = this._parseTimeline(
				rawData, null, DataParser.COLOR_FRAME, TimelineType.SlotColor,
				FrameValueType.INT, 1,
				this::_parseSlotColorFrame
			)
		} else {
			colorTimeline = this._parseTimeline(
				rawData, null, DataParser.FRAME, TimelineType.SlotColor,
				FrameValueType.INT, 1,
				this::_parseSlotColorFrame
			)
		}

		if (displayTimeline != null) {
			this._animation?.addSlotTimeline(slot.name, displayTimeline)
		}

		if (colorTimeline != null) {
			this._animation?.addSlotTimeline(slot.name, colorTimeline)
		}

		this._slot = null //
	}

	@Suppress("UNUSED_PARAMETER")
	protected fun _parseFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		val frameOffset = this._frameArray.length
		this._frameArray.length += 1
		this._frameArray[frameOffset + BinaryOffset.FramePosition] = frameStart.toFloat()

		return frameOffset
	}

	protected fun _parseTweenFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		val frameOffset = this._parseFrame(rawData, frameStart, frameCount)

		if (frameCount > 0) {
			if (DataParser.CURVE in rawData) {
				val sampleCount = frameCount + 1
				this._helpArray.length = sampleCount
				val isOmited = this._samplingEasingCurve(rawData[DataParser.CURVE].FloatArrayList, this._helpArray)

				this._frameArray.length += 1 + 1 + this._helpArray.length
				this._frameArray[frameOffset + BinaryOffset.FrameTweenType] = TweenType.Curve.id.toFloat()
				this._frameArray[frameOffset + BinaryOffset.FrameTweenEasingOrCurveSampleCount] =
						(if (isOmited) sampleCount else -sampleCount).toFloat()
				//for (var i = 0; i < sampleCount; ++i) {
				for (i in 0 until sampleCount) {
					this._frameArray[frameOffset + BinaryOffset.FrameCurveSamples + i] =
							round(this._helpArray[i] * 10000f)
				}
			} else {
				val noTween = -2.0f
				var tweenEasing = noTween
				if (DataParser.TWEEN_EASING in rawData) {
					tweenEasing = ObjectDataParser._getNumber(rawData, DataParser.TWEEN_EASING, noTween)
				}

				if (tweenEasing == noTween) {
					this._frameArray.length += 1
					this._frameArray[frameOffset + BinaryOffset.FrameTweenType] = TweenType.None.id.toFloat()
				} else if (tweenEasing == 0f) {
					this._frameArray.length += 1
					this._frameArray[frameOffset + BinaryOffset.FrameTweenType] = TweenType.Line.id.toFloat()
				} else if (tweenEasing < 0f) {
					this._frameArray.length += 1 + 1
					this._frameArray[frameOffset + BinaryOffset.FrameTweenType] = TweenType.QuadIn.id.toFloat()
					this._frameArray[frameOffset + BinaryOffset.FrameTweenEasingOrCurveSampleCount] =
							round(-tweenEasing * 100f)
				} else if (tweenEasing <= 1f) {
					this._frameArray.length += 1 + 1
					this._frameArray[frameOffset + BinaryOffset.FrameTweenType] = TweenType.QuadOut.id.toFloat()
					this._frameArray[frameOffset + BinaryOffset.FrameTweenEasingOrCurveSampleCount] =
							round(tweenEasing * 100f)
				} else {
					this._frameArray.length += 1 + 1
					this._frameArray[frameOffset + BinaryOffset.FrameTweenType] =
							TweenType.QuadInOut.id.toFloat()
					this._frameArray[frameOffset + BinaryOffset.FrameTweenEasingOrCurveSampleCount] =
							round(tweenEasing * 100f - 100f)
				}
			}
		} else {
			this._frameArray.length += 1
			this._frameArray[frameOffset + BinaryOffset.FrameTweenType] = TweenType.None.id.toFloat()
		}

		return frameOffset
	}

	protected fun _parseSingleValueFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		var frameOffset = 0
		when (this._frameValueType) {
			FrameValueType.STEP -> {
				frameOffset = this._parseFrame(rawData, frameStart, frameCount)
				this._frameArray.length += 1
				this._frameArray[frameOffset + 1] =
						ObjectDataParser._getNumber(rawData, DataParser.VALUE, this._frameDefaultValue)
			}

			FrameValueType.INT -> {
				frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
				val frameValueOffset = this._frameIntArray.length
				this._frameIntArray.length += 1
				this._frameIntArray[frameValueOffset] = round(
					ObjectDataParser._getNumber(
						rawData,
						DataParser.VALUE,
						this._frameDefaultValue
					) * this._frameValueScale
				).toInt()
			}

			FrameValueType.FLOAT -> {
				frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
				val frameValueOffset = this._frameFloatArray.length
				this._frameFloatArray.length += 1
				this._frameFloatArray[frameValueOffset] = ObjectDataParser._getNumber(
					rawData,
					DataParser.VALUE,
					this._frameDefaultValue
				) * this._frameValueScale
			}
		}

		return frameOffset
	}

	protected fun _parseDoubleValueFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		var frameOffset = 0
		when (this._frameValueType) {
			FrameValueType.STEP -> {
				frameOffset = this._parseFrame(rawData, frameStart, frameCount)
				this._frameArray.length += 2
				this._frameArray[frameOffset + 1] =
						ObjectDataParser._getNumber(rawData, DataParser.X, this._frameDefaultValue)
				this._frameArray[frameOffset + 2] =
						ObjectDataParser._getNumber(rawData, DataParser.Y, this._frameDefaultValue)
			}

			FrameValueType.INT -> {
				frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
				val frameValueOffset = this._frameIntArray.length
				this._frameIntArray.length += 2
				this._frameIntArray[frameValueOffset] = round(
					ObjectDataParser._getNumber(
						rawData,
						DataParser.X,
						this._frameDefaultValue
					) * this._frameValueScale
				).toInt()
				this._frameIntArray[frameValueOffset + 1] = round(
					ObjectDataParser._getNumber(
						rawData,
						DataParser.Y,
						this._frameDefaultValue
					) * this._frameValueScale
				).toInt()
			}

			FrameValueType.FLOAT -> {
				frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
				val frameValueOffset = this._frameFloatArray.length
				this._frameFloatArray.length += 2
				this._frameFloatArray[frameValueOffset] = ObjectDataParser._getNumber(
					rawData,
					DataParser.X,
					this._frameDefaultValue
				) * this._frameValueScale
				this._frameFloatArray[frameValueOffset + 1] = ObjectDataParser._getNumber(
					rawData,
					DataParser.Y,
					this._frameDefaultValue
				) * this._frameValueScale
			}
		}

		return frameOffset
	}

	protected fun _parseActionFrameRaw(frame: Any?, frameStart: Int, frameCount: Int): Int =
		_parseActionFrame(frame as ActionFrame, frameStart, frameCount)

	protected fun _parseActionFrame(frame: ActionFrame, frameStart: Int, frameCount: Int): Int {
		val frameOffset = this._frameArray.length
		val actionCount = frame.actions.length
		this._frameArray.length += 1 + 1 + actionCount
		this._frameArray[frameOffset + BinaryOffset.FramePosition] = frameStart.toFloat()
		this._frameArray[frameOffset + BinaryOffset.FramePosition + 1] = actionCount.toFloat() // Action count.

		//for (var i = 0; i < actionCount; ++i) { // Action offsets.
		for (i in 0 until actionCount) { // Action offsets.
			this._frameArray[frameOffset + BinaryOffset.FramePosition + 2 + i] = frame.actions[i].toFloat()
		}

		return frameOffset
	}

	protected fun _parseZOrderFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		val rawData = rawData as Map<String, Any?>
		val frameOffset = this._parseFrame(rawData, frameStart, frameCount)

		if (DataParser.Z_ORDER in rawData) {
			val rawZOrder = rawData[DataParser.Z_ORDER] .FloatArray
			if (rawZOrder.size > 0) {
				val slotCount = this._armature!!.sortedSlots.length
				val unchanged = IntArray(slotCount - rawZOrder.size / 2)
				val zOrders = IntArray(slotCount)

				//for (var i = 0; i < unchanged.length; ++i) {
				for (i in 0 until unchanged.size) {
					unchanged[i] = 0
				}

				//for (var i = 0; i < slotCount; ++i) {
				for (i in 0 until slotCount) {
					zOrders[i] = -1
				}

				var originalIndex = 0
				var unchangedIndex = 0
				//for (var i = 0, l = rawZOrder.length; i < l; i += 2) {
				for (i in 0 until rawZOrder.size step 2) {
					val slotIndex = rawZOrder[i].toInt()
					val zOrderOffset = rawZOrder[i + 1].toInt()

					while (originalIndex != slotIndex) {
						unchanged[unchangedIndex++] = originalIndex++
					}

					val index = originalIndex + zOrderOffset
					zOrders[index] = originalIndex++
				}

				while (originalIndex < slotCount) {
					unchanged[unchangedIndex++] = originalIndex++
				}

				this._frameArray.length += 1 + slotCount
				this._frameArray[frameOffset + 1] = slotCount.toFloat()

				var i = slotCount
				while (i-- > 0) {
					if (zOrders[i] == -1) {
						this._frameArray[frameOffset + 2 + i] = unchanged[--unchangedIndex].toFloat()
					} else {
						this._frameArray[frameOffset + 2 + i] = zOrders[i].toFloat()
					}
				}

				return frameOffset
			}
		}

		this._frameArray.length += 1
		this._frameArray[frameOffset + 1] = 0f

		return frameOffset
	}

	protected fun _parseBoneAllFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		this._helpTransform.identity()
		if (DataParser.TRANSFORM in rawData) {
			this._parseTransform(rawData[DataParser.TRANSFORM], this._helpTransform, 1f)
		}

		// Modify rotation.
		var rotation = this._helpTransform.rotation
		if (frameStart != 0) {
			if (this._prevClockwise == 0f) {
				rotation = (this._prevRotation + normalizeRadian(rotation - this._prevRotation)).toFloat()
			} else {
				if (if (this._prevClockwise > 0) rotation >= this._prevRotation else rotation <= this._prevRotation) {
					this._prevClockwise =
							if (this._prevClockwise > 0) this._prevClockwise - 1 else this._prevClockwise + 1
				}

				rotation = (this._prevRotation + rotation - this._prevRotation + PI_D * this._prevClockwise).toFloat()
			}
		}

		this._prevClockwise = ObjectDataParser._getNumber(rawData, DataParser.TWEEN_ROTATE, 0f)
		this._prevRotation = rotation.toFloat()
		//
		val frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
		var frameFloatOffset = this._frameFloatArray.length
		this._frameFloatArray.length += 6
		this._frameFloatArray[frameFloatOffset++] = this._helpTransform.x
		this._frameFloatArray[frameFloatOffset++] = this._helpTransform.y
		this._frameFloatArray[frameFloatOffset++] = rotation
		this._frameFloatArray[frameFloatOffset++] = this._helpTransform.skew
		this._frameFloatArray[frameFloatOffset++] = this._helpTransform.scaleX
		this._frameFloatArray[frameFloatOffset++] = this._helpTransform.scaleY
		this._parseActionDataInFrame(rawData, frameStart, this._bone, this._slot)

		return frameOffset
	}

	protected fun _parseBoneTranslateFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		val frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
		var frameFloatOffset = this._frameFloatArray.length
		this._frameFloatArray.length += 2
		this._frameFloatArray[frameFloatOffset++] = ObjectDataParser._getNumber(rawData, DataParser.X, 0f)
		this._frameFloatArray[frameFloatOffset++] = ObjectDataParser._getNumber(rawData, DataParser.Y, 0f)

		return frameOffset
	}

	protected fun _parseBoneRotateFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		// Modify rotation.
		var rotation = ObjectDataParser._getNumber(rawData, DataParser.ROTATE, 0f) * DEG_RAD

		if (frameStart != 0) {
			if (this._prevClockwise == 0f) {
				rotation = this._prevRotation + normalizeRadian(rotation - this._prevRotation)
			} else {
				if (if (this._prevClockwise > 0) rotation >= this._prevRotation else rotation <= this._prevRotation) {
					this._prevClockwise =
							if (this._prevClockwise > 0) this._prevClockwise - 1 else this._prevClockwise + 1
				}

				rotation = this._prevRotation + rotation - this._prevRotation + PI_D * this._prevClockwise
			}
		}

		this._prevClockwise = ObjectDataParser._getNumber(rawData, DataParser.CLOCK_WISE, 0f)
		this._prevRotation = rotation
		//
		val frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
		var frameFloatOffset = this._frameFloatArray.length
		this._frameFloatArray.length += 2
		this._frameFloatArray[frameFloatOffset++] = rotation
		this._frameFloatArray[frameFloatOffset++] = ObjectDataParser._getNumber(rawData, DataParser.SKEW, 0f) *
				DEG_RAD

		return frameOffset
	}

	protected fun _parseBoneScaleFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		val frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
		var frameFloatOffset = this._frameFloatArray.length
		this._frameFloatArray.length += 2
		this._frameFloatArray[frameFloatOffset++] = ObjectDataParser._getNumber(rawData, DataParser.X, 1f)
		this._frameFloatArray[frameFloatOffset++] = ObjectDataParser._getNumber(rawData, DataParser.Y, 1f)

		return frameOffset
	}

	protected fun _parseSlotDisplayFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		val frameOffset = this._parseFrame(rawData, frameStart, frameCount)
		this._frameArray.length += 1

		if (DataParser.VALUE in rawData) {
			this._frameArray[frameOffset + 1] = ObjectDataParser._getNumber(rawData, DataParser.VALUE, 0f)
		} else {
			this._frameArray[frameOffset + 1] = ObjectDataParser._getNumber(rawData, DataParser.DISPLAY_INDEX, 0f)
		}

		this._parseActionDataInFrame(rawData, frameStart, this._slot?.parent, this._slot)

		return frameOffset
	}

	protected fun _parseSlotColorFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		val frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
		var colorOffset = -1

		if (DataParser.VALUE in rawData || DataParser.COLOR in rawData) {
			val rawColor = rawData[DataParser.VALUE] ?: rawData[DataParser.COLOR]
			// @TODO: Kotlin-JS: Caused by: java.lang.IllegalStateException: Value at LOOP_RANGE_ITERATOR_RESOLVED_CALL must not be null for BINARY_WITH_TYPE
			//for (k in (rawColor as List<Any?>)) { // Detects the presence of color.
			//for (let k in rawColor) { // Detects the presence of color.
			for (k in rawColor.dynKeys) { // Detects the presence of color.
				this._parseColorTransform(rawColor, this._helpColorTransform)
				colorOffset = this._colorArray.length
				this._colorArray.length += 8
				this._colorArray[colorOffset++] = round(this._helpColorTransform.alphaMultiplier * 100).toInt()
				this._colorArray[colorOffset++] = round(this._helpColorTransform.redMultiplier * 100).toInt()
				this._colorArray[colorOffset++] = round(this._helpColorTransform.greenMultiplier * 100).toInt()
				this._colorArray[colorOffset++] = round(this._helpColorTransform.blueMultiplier * 100).toInt()
				this._colorArray[colorOffset++] = round(this._helpColorTransform.alphaOffset.toDouble()).toInt()
				this._colorArray[colorOffset++] = round(this._helpColorTransform.redOffset.toDouble()).toInt()
				this._colorArray[colorOffset++] = round(this._helpColorTransform.greenOffset.toDouble()).toInt()
				this._colorArray[colorOffset++] = round(this._helpColorTransform.blueOffset.toDouble()).toInt()
				colorOffset -= 8
				break
			}
		}

		if (colorOffset < 0) {
			if (this._defaultColorOffset < 0) {
				colorOffset = this._colorArray.length
				this._defaultColorOffset = colorOffset
				this._colorArray.length += 8
				this._colorArray[colorOffset++] = 100
				this._colorArray[colorOffset++] = 100
				this._colorArray[colorOffset++] = 100
				this._colorArray[colorOffset++] = 100
				this._colorArray[colorOffset++] = 0
				this._colorArray[colorOffset++] = 0
				this._colorArray[colorOffset++] = 0
				this._colorArray[colorOffset++] = 0
			}

			colorOffset = this._defaultColorOffset
		}

		val frameIntOffset = this._frameIntArray.length
		this._frameIntArray.length += 1
		this._frameIntArray[frameIntOffset] = colorOffset

		return frameOffset
	}

	protected fun _parseSlotDeformFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		val frameFloatOffset = this._frameFloatArray.length
		val frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
		val rawVertices = rawData[DataParser.VERTICES]?.FloatArray
		val offset = ObjectDataParser._getInt(rawData, DataParser.OFFSET, 0) // uint
		val vertexCount = this._intArray[this._mesh!!.geometry.offset + BinaryOffset.GeometryVertexCount]
		val meshName = "" + this._mesh?.parent?.name + "_" + this._slot?.name + "_" + this._mesh?.name
		val weight = this._mesh?.geometry!!.weight

		var x: Float
		var y: Float
		var iB = 0
		var iV = 0
		if (weight != null) {
			val rawSlotPose = this._weightSlotPose[meshName]
			this._helpMatrixA.copyFromArray(rawSlotPose!!.data, 0)
			this._frameFloatArray.length += weight.count * 2
			iB = weight.offset + BinaryOffset.WeigthBoneIndices + weight.bones.length
		} else {
			this._frameFloatArray.length += vertexCount * 2
		}

		//for (var i = 0; i < vertexCount * 2; i += 2) {
		for (i in 0 until vertexCount * 2 step 2) {
			if (rawVertices == null) { // Fill 0.
				x = 0f
				y = 0f
			} else {
				if (i < offset || i - offset >= rawVertices.size) {
					x = 0f
				} else {
					x = rawVertices[i - offset].toFloat()
				}

				if (i + 1 < offset || i + 1 - offset >= rawVertices.size) {
					y = 0f
				} else {
					y = rawVertices[i + 1 - offset].toFloat()
				}
			}

			if (weight != null) { // If mesh is skinned, transform point by bone bind pose.
				val rawBonePoses = this._weightBonePoses[meshName]!!
				val vertexBoneCount = this._intArray[iB++]

				this._helpMatrixA.transformPoint(x, y, this._helpPoint, true)
				x = this._helpPoint.x
				y = this._helpPoint.y

				//for (var j = 0; j < vertexBoneCount; ++j) {
				for (j in 0 until vertexBoneCount) {
					val boneIndex = this._intArray[iB++]
					this._helpMatrixB.copyFromArray(rawBonePoses.data, boneIndex * 7 + 1)
					this._helpMatrixB.invert()
					this._helpMatrixB.transformPoint(x, y, this._helpPoint, true)

					this._frameFloatArray[frameFloatOffset + iV++] = this._helpPoint.x
					this._frameFloatArray[frameFloatOffset + iV++] = this._helpPoint.y
				}
			} else {
				this._frameFloatArray[frameFloatOffset + i] = x
				this._frameFloatArray[frameFloatOffset + i + 1] = y
			}
		}

		if (frameStart == 0) {
			val frameIntOffset = this._frameIntArray.length
			this._frameIntArray.length += 1 + 1 + 1 + 1 + 1
			this._frameIntArray[frameIntOffset + BinaryOffset.DeformVertexOffset] = this._mesh!!.geometry.offset
			this._frameIntArray[frameIntOffset + BinaryOffset.DeformCount] = this._frameFloatArray.length -
					frameFloatOffset
			this._frameIntArray[frameIntOffset + BinaryOffset.DeformValueCount] = this._frameFloatArray.length -
					frameFloatOffset
			this._frameIntArray[frameIntOffset + BinaryOffset.DeformValueOffset] = 0
			this._frameIntArray[frameIntOffset + BinaryOffset.DeformFloatOffset] = frameFloatOffset -
					this._animation!!.frameFloatOffset
			this._timelineArray[this._timeline!!.offset + BinaryOffset.TimelineFrameValueCount] =
					(frameIntOffset - this._animation!!.frameIntOffset).toFloat()
		}

		return frameOffset
	}

	protected fun _parseIKConstraintFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		val frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
		var frameIntOffset = this._frameIntArray.length
		this._frameIntArray.length += 2
		this._frameIntArray[frameIntOffset++] =
				if (ObjectDataParser._getBoolean(rawData, DataParser.BEND_POSITIVE, true)) 1 else 0
		this._frameIntArray[frameIntOffset++] =
				round(ObjectDataParser._getNumber(rawData, DataParser.WEIGHT, 1f) * 100f).toInt()

		return frameOffset
	}

	protected fun _parseActionData(
		rawData: Any?,
		type: ActionType,
		bone: BoneData?,
		slot: SlotData?
	): ArrayList<ActionData> {
		val actions = ArrayList<ActionData>()

		if (rawData is String) {
			val action = pool.borrowObject<ActionData>()
			action.type = type
			action.name = rawData
			action.bone = bone
			action.slot = slot
			actions.push(action)
		} else if (rawData is List<*>) {
			for (rawAction in rawData as List<Map<String, Any?>>) {
				val action = pool.borrowObject<ActionData>()

				if (DataParser.GOTO_AND_PLAY in rawAction) {
					action.type = ActionType.Play
					action.name = ObjectDataParser._getString(rawAction, DataParser.GOTO_AND_PLAY, "")
				} else {
					if (DataParser.TYPE in rawAction && rawAction[DataParser.TYPE] is String) {
						action.type = DataParser._getActionType(rawAction[DataParser.TYPE]?.toString())
					} else {
						action.type = ActionType[ObjectDataParser._getInt(rawAction, DataParser.TYPE, type.id)]
					}

					action.name = ObjectDataParser._getString(rawAction, DataParser.NAME, "")
				}

				if (DataParser.BONE in rawAction) {
					val boneName = ObjectDataParser._getString(rawAction, DataParser.BONE, "")
					action.bone = this._armature?.getBone(boneName)
				} else {
					action.bone = bone
				}

				if (DataParser.SLOT in rawAction) {
					val slotName = ObjectDataParser._getString(rawAction, DataParser.SLOT, "")
					action.slot = this._armature?.getSlot(slotName)
				} else {
					action.slot = slot
				}

				var userData: UserData? = null

				if (DataParser.INTS in rawAction) {
					if (userData == null) {
						userData = pool.borrowObject<UserData>()
					}

					val rawInts = rawAction[DataParser.INTS] .intArrayList
					for (rawValue in rawInts) {
						userData.addInt(rawValue)
					}
				}

				if (DataParser.FLOATS in rawAction) {
					if (userData == null) {
						userData = pool.borrowObject<UserData>()
					}

					val rawFloats = rawAction[DataParser.FLOATS].FloatArrayList
					for (rawValue in rawFloats) {
						userData.addFloat(rawValue)
					}
				}

				if (DataParser.STRINGS in rawAction) {
					if (userData == null) {
						userData = pool.borrowObject<UserData>()
					}

					val rawStrings = rawAction[DataParser.STRINGS] as ArrayList<String>
					for (rawValue in rawStrings) {
						userData.addString(rawValue)
					}
				}

				action.data = userData
				actions.push(action)
			}
		}

		return actions
	}

	protected fun _parseDeformFrame(rawData: Any?, frameStart: Int, frameCount: Int): Int {
		val frameFloatOffset = this._frameFloatArray.length
		val frameOffset = this._parseTweenFrame(rawData, frameStart, frameCount)
		val rawVertices = if (DataParser.VERTICES in rawData)
			rawData[DataParser.VERTICES]?.FloatArrayList else
			rawData[DataParser.VALUE]?.FloatArrayList
		val offset = ObjectDataParser._getNumber(rawData, DataParser.OFFSET, 0f).toInt() // uint
		val vertexCount = this._intArray[this._geometry!!.offset + BinaryOffset.GeometryVertexCount]
		val weight = this._geometry!!.weight
		var x: Float
		var y: Float

		if (weight != null) {
			// TODO
		} else {
			this._frameFloatArray.length += vertexCount * 2

			//for (var i = 0;i < vertexCount * 2;i += 2) {
			for (i in 0 until (vertexCount * 2) step 2) {
				if (rawVertices != null) {
					if (i < offset || i - offset >= rawVertices.length) {
						x = 0f
					} else {
						x = rawVertices[i - offset]
					}

					if (i + 1 < offset || i + 1 - offset >= rawVertices.length) {
						y = 0f
					} else {
						y = rawVertices[i + 1 - offset]
					}
				} else {
					x = 0f
					y = 0f
				}

				this._frameFloatArray[frameFloatOffset + i] = x
				this._frameFloatArray[frameFloatOffset + i + 1] = y
			}
		}

		if (frameStart == 0) {
			val frameIntOffset = this._frameIntArray.length
			this._frameIntArray.length += 1 + 1 + 1 + 1 + 1
			this._frameIntArray[frameIntOffset + BinaryOffset.DeformVertexOffset] = this._geometry!!.offset
			this._frameIntArray[frameIntOffset + BinaryOffset.DeformCount] = this._frameFloatArray.length -
					frameFloatOffset
			this._frameIntArray[frameIntOffset + BinaryOffset.DeformValueCount] = this._frameFloatArray.length -
					frameFloatOffset
			this._frameIntArray[frameIntOffset + BinaryOffset.DeformValueOffset] = 0
			this._frameIntArray[frameIntOffset + BinaryOffset.DeformFloatOffset] = frameFloatOffset -
					this._animation!!.frameFloatOffset
			this._timelineArray[this._timeline!!.offset + BinaryOffset.TimelineFrameValueCount] =
					(frameIntOffset - this._animation!!.frameIntOffset).toFloat()
		}

		return frameOffset
	}

	protected fun _parseTransform(rawData: Any?, transform: Transform, scale: Float) {
		transform.x = (ObjectDataParser._getNumber(rawData, DataParser.X, 0f) * scale).toFloat()
		transform.y = (ObjectDataParser._getNumber(rawData, DataParser.Y, 0f) * scale).toFloat()

		if (DataParser.ROTATE in rawData || DataParser.SKEW in rawData) {
			transform.rotation = normalizeRadian(
				ObjectDataParser._getNumber(
					rawData,
					DataParser.ROTATE,
					0f
				) * DEG_RAD
			).toFloat()
			transform.skew = normalizeRadian(
				ObjectDataParser._getNumber(
					rawData,
					DataParser.SKEW,
					0f
				) * DEG_RAD
			).toFloat()
		} else if (DataParser.SKEW_X in rawData || DataParser.SKEW_Y in rawData) {
			transform.rotation = normalizeRadian(
				ObjectDataParser._getNumber(
					rawData,
					DataParser.SKEW_Y,
					0f
				) * DEG_RAD
			).toFloat()
			transform.skew = (normalizeRadian(
				ObjectDataParser._getNumber(
					rawData,
					DataParser.SKEW_X,
					0f
				) * DEG_RAD
			) - transform.rotation).toFloat()
		}

		transform.scaleX = ObjectDataParser._getNumber(rawData, DataParser.SCALE_X, 1f)
		transform.scaleY = ObjectDataParser._getNumber(rawData, DataParser.SCALE_Y, 1f)
	}

	protected fun _parseColorTransform(rawData: Any?, color: ColorTransform) {
		color.alphaMultiplier = ObjectDataParser._getNumber(rawData, DataParser.ALPHA_MULTIPLIER, 100f) * 0.01f
		color.redMultiplier = ObjectDataParser._getNumber(rawData, DataParser.RED_MULTIPLIER, 100f) * 0.01f
		color.greenMultiplier = ObjectDataParser._getNumber(rawData, DataParser.GREEN_MULTIPLIER, 100f) * 0.01f
		color.blueMultiplier = ObjectDataParser._getNumber(rawData, DataParser.BLUE_MULTIPLIER, 100f) * 0.01f
		color.alphaOffset = ObjectDataParser._getInt(rawData, DataParser.ALPHA_OFFSET, 0)
		color.redOffset = ObjectDataParser._getInt(rawData, DataParser.RED_OFFSET, 0)
		color.greenOffset = ObjectDataParser._getInt(rawData, DataParser.GREEN_OFFSET, 0)
		color.blueOffset = ObjectDataParser._getInt(rawData, DataParser.BLUE_OFFSET, 0)
	}

	open protected fun _parseGeometry(rawData: Any?, geometry: GeometryData) {
		val rawVertices = rawData[DataParser.VERTICES] .FloatArray
		val vertexCount: Int = rawVertices.size / 2 // uint
		var triangleCount = 0
		val geometryOffset = this._intArray.length
		val verticesOffset = this._floatArray.length
		//
		geometry.offset = geometryOffset
		geometry.data = this._data
		//
		this._intArray.length += 1 + 1 + 1 + 1
		this._intArray[geometryOffset + BinaryOffset.GeometryVertexCount] = vertexCount
		this._intArray[geometryOffset + BinaryOffset.GeometryFloatOffset] = verticesOffset
		this._intArray[geometryOffset + BinaryOffset.GeometryWeightOffset] = -1 //
		//
		this._floatArray.length += vertexCount * 2
		//for (var i = 0, l = vertexCount * 2; i < l; ++i) {
		for (i in 0 until vertexCount * 2) {
			this._floatArray[verticesOffset + i] = rawVertices[i]
		}

		if (DataParser.TRIANGLES in rawData) {
			val rawTriangles = rawData[DataParser.TRIANGLES] .FloatArray
			triangleCount = rawTriangles.size / 3 // uint
			//
			this._intArray.length += triangleCount * 3
			//for (var i = 0, l = triangleCount * 3; i < l; ++i) {
			for (i in 0 until triangleCount * 3) {
				this._intArray[geometryOffset + BinaryOffset.GeometryVertexIndices + i] = rawTriangles[i].toInt()
			}
		}
		// Fill triangle count.
		this._intArray[geometryOffset + BinaryOffset.GeometryTriangleCount] = triangleCount

		if (DataParser.UVS in rawData) {
			val rawUVs = rawData[DataParser.UVS] .FloatArray
			val uvOffset = verticesOffset + vertexCount * 2
			this._floatArray.length += vertexCount * 2
			//for (var i = 0, l = vertexCount * 2; i < l; ++i) {
			for (i in 0 until vertexCount * 2) {
				this._floatArray[uvOffset + i] = rawUVs[i]
			}
		}

		if (DataParser.WEIGHTS in rawData) {
			val rawWeights = rawData[DataParser.WEIGHTS] .FloatArray
			val weightCount = (rawWeights.size - vertexCount) / 2 // uint
			val weightOffset = this._intArray.length
			val floatOffset = this._floatArray.length
			var weightBoneCount = 0
			val sortedBones = this._armature?.sortedBones
			val weight = pool.borrowObject<WeightData>()
			weight.count = weightCount
			weight.offset = weightOffset

			this._intArray.length += 1 + 1 + weightBoneCount + vertexCount + weightCount
			this._intArray[weightOffset + BinaryOffset.WeigthFloatOffset] = floatOffset

			if (DataParser.BONE_POSE in rawData) {
				val rawSlotPose = rawData[DataParser.SLOT_POSE] .FloatArray
				val rawBonePoses = rawData[DataParser.BONE_POSE] .FloatArray
				val weightBoneIndices = IntArrayList()

				weightBoneCount = (rawBonePoses.size / 7) // uint
				weightBoneIndices.length = weightBoneCount

				//for (var i = 0; i < weightBoneCount; ++i) {
				for (i in 0 until weightBoneCount) {
					val rawBoneIndex = rawBonePoses[i * 7].toInt() // uint
					val bone = this._rawBones[rawBoneIndex]
					weight.addBone(bone)
					weightBoneIndices[i] = rawBoneIndex
					this._intArray[weightOffset + BinaryOffset.WeigthBoneIndices + i] =
							sortedBones!!.indexOf(bone)
				}

				this._floatArray.length += weightCount * 3
				this._helpMatrixA.copyFromArray(rawSlotPose, 0)

				// for (var i = 0, iW = 0, iB = weightOffset + BinaryOffset.WeigthBoneIndices + weightBoneCount, iV = floatOffset; i < vertexCount; ++i) {
				var iW = 0
				var iB = weightOffset + BinaryOffset.WeigthBoneIndices + weightBoneCount
				var iV = floatOffset
				for (i in 0 until vertexCount) {
					val iD = i * 2
					val vertexBoneCount = rawWeights[iW++].toInt() // uint
					this._intArray[iB++] = vertexBoneCount

					var x = this._floatArray[verticesOffset + iD].toFloat()
					var y = this._floatArray[verticesOffset + iD + 1].toFloat()
					this._helpMatrixA.transformPoint(x, y, this._helpPoint)
					x = this._helpPoint.x
					y = this._helpPoint.y

					//for (var j = 0; j < vertexBoneCount; ++j) {
					for (j in 0 until vertexBoneCount) {
						val rawBoneIndex = rawWeights[iW++].toInt() // uint
						val boneIndex = weightBoneIndices.indexOf(rawBoneIndex)
						this._helpMatrixB.copyFromArray(rawBonePoses, boneIndex * 7 + 1)
						this._helpMatrixB.invert()
						this._helpMatrixB.transformPoint(x, y, this._helpPoint)
						this._intArray[iB++] = boneIndex
						this._floatArray[iV++] = rawWeights[iW++]
						this._floatArray[iV++] = this._helpPoint.x
						this._floatArray[iV++] = this._helpPoint.y
					}
				}
			} else {
				val rawBones = rawData[DataParser.BONES] .FloatArray
				weightBoneCount = rawBones.size

				//for (var i = 0; i < weightBoneCount; i++) {
				for (i in 0 until weightBoneCount) {
					val rawBoneIndex = rawBones[i].toInt()
					val bone = this._rawBones[rawBoneIndex]
					weight.addBone(bone)
					this._intArray[weightOffset + BinaryOffset.WeigthBoneIndices + i] =
							sortedBones!!.indexOf(bone)
				}

				this._floatArray.length += weightCount * 3
				//for (var i = 0, iW = 0, iV = 0, iB = weightOffset + BinaryOffset.WeigthBoneIndices + weightBoneCount, iF = floatOffset; i < weightCount; i++) {
				var iW = 0
				var iV = 0
				var iB = weightOffset + BinaryOffset.WeigthBoneIndices + weightBoneCount
				var iF = floatOffset
				for (i in 0 until weightCount) {
					val vertexBoneCount = rawWeights[iW++].toInt()
					this._intArray[iB++] = vertexBoneCount

					//for (var j = 0; j < vertexBoneCount; j++) {
					for (j in 0 until vertexBoneCount) {
						val boneIndex = rawWeights[iW++]
						val boneWeight = rawWeights[iW++]
						val x = rawVertices[iV++]
						val y = rawVertices[iV++]

						this._intArray[iB++] = rawBones.indexOf(boneIndex)
						this._floatArray[iF++] = boneWeight
						this._floatArray[iF++] = x
						this._floatArray[iF++] = y
					}
				}
			}

			geometry.weight = weight
		}
	}

	protected open fun _parseArray(@Suppress("UNUSED_PARAMETER") rawData: Any?) {
		this._intArray.length = 0
		this._floatArray.length = 0
		this._frameIntArray.length = 0
		this._frameFloatArray.length = 0
		this._frameArray.length = 0
		this._timelineArray.length = 0
		this._colorArray.length = 0
	}

	protected fun _modifyArray() {
		// Align.
		if ((this._intArray.length % 2) != 0) {
			this._intArray.push(0)
		}

		if ((this._frameIntArray.length % 2) != 0) {
			this._frameIntArray.push(0)
		}

		if ((this._frameArray.length % 2) != 0) {
			this._frameArray.push(0f)
		}

		if ((this._timelineArray.length % 2) != 0) {
			//this._timelineArray.push(0)
			this._timelineArray.push(0f)
		}

		if ((this._timelineArray.length % 2) != 0) {
			this._colorArray.push(0)
		}

		val l1 = this._intArray.length * 2
		val l2 = this._floatArray.length * 4
		val l3 = this._frameIntArray.length * 2
		val l4 = this._frameFloatArray.length * 4
		val l5 = this._frameArray.length * 2
		val l6 = this._timelineArray.length * 2
		val l7 = this._colorArray.length * 2
		val lTotal = l1 + l2 + l3 + l4 + l5 + l6 + l7
		//
		val binary = MemBufferAlloc(lTotal)
		val intArray = binary.sliceInt16BufferByteOffset(0, this._intArray.length)
		val floatArray = binary.sliceFloat32BufferByteOffset(l1, this._floatArray.length)
		val frameIntArray = binary.sliceInt16BufferByteOffset(l1 + l2, this._frameIntArray.length)
		val frameFloatArray = binary.sliceFloat32BufferByteOffset(l1 + l2 + l3, this._frameFloatArray.length)
		val frameArray = binary.sliceInt16BufferByteOffset(l1 + l2 + l3 + l4, this._frameArray.length)
		val timelineArray = binary.sliceUint16BufferByteOffset(l1 + l2 + l3 + l4 + l5, this._timelineArray.length)
		val colorArray = binary.sliceInt16BufferByteOffset(l1 + l2 + l3 + l4 + l5 + l6, this._colorArray.length)

		for (i in 0 until this._intArray.length) {
			intArray[i] = this._intArray[i].toShort()
		}

		for (i in 0 until this._floatArray.length) {
			floatArray[i] = this._floatArray[i].toFloat()
		}

		//for (var i = 0, l = this._frameIntArray.length; i < l; ++i) {
		for (i in 0 until this._frameIntArray.length) {
			frameIntArray[i] = this._frameIntArray[i].toShort()
		}

		//for (var i = 0, l = this._frameFloatArray.length; i < l; ++i) {
		for (i in 0 until this._frameFloatArray.length) {
			frameFloatArray[i] = this._frameFloatArray[i].toFloat()
		}

		//for (var i = 0, l = this._frameArray.length; i < l; ++i) {
		for (i in 0 until this._frameArray.length) {
			frameArray[i] = this._frameArray[i].toShort()
		}

		//for (var i = 0, l = this._timelineArray.length; i < l; ++i) {
		for (i in 0 until this._timelineArray.length) {
			timelineArray[i] = this._timelineArray[i].toInt()
		}

		//for (var i = 0, l = this._colorArray.length; i < l; ++i) {
		for (i in 0 until this._colorArray.length) {
			colorArray[i] = this._colorArray[i].toShort()
		}

		this._data?.binary = binary
		this._data?.intArray = intArray
		this._data?.floatArray = floatArray
		this._data?.frameIntArray = frameIntArray
		this._data?.frameFloatArray = frameFloatArray
		this._data?.frameArray = frameArray
		this._data?.timelineArray = timelineArray
		this._data?.colorArray = colorArray
		this._defaultColorOffset = -1
	}

	override fun parseDragonBonesData(rawData: Any?, scale: Float): DragonBonesData? {
		//console.assert(rawData != null && rawData != null, "Data error.")

		val version = ObjectDataParser._getString(rawData, DataParser.VERSION, "")
		val compatibleVersion = ObjectDataParser._getString(rawData, DataParser.COMPATIBLE_VERSION, "")

		if (
			DataParser.DATA_VERSIONS.indexOf(version) >= 0 ||
			DataParser.DATA_VERSIONS.indexOf(compatibleVersion) >= 0
		) {
			val data = pool.borrowObject<DragonBonesData>()
			data.version = version
			data.name = ObjectDataParser._getString(rawData, DataParser.NAME, "")
			data.frameRate = ObjectDataParser._getInt(rawData, DataParser.FRAME_RATE, 24)

			if (data.frameRate == 0) { // Data error.
				data.frameRate = 24
			}

			if (DataParser.ARMATURE in rawData) {
				this._data = data
				this._parseArray(rawData)

				val rawArmatures = rawData[DataParser.ARMATURE] as List<Any?>
				for (rawArmature in rawArmatures) {
					data.addArmature(this._parseArmature(rawArmature, scale))
				}

				if (this._data?.binary == null) { // DragonBones.webAssembly ? 0 : null;
					this._modifyArray()
				}

				if (DataParser.STAGE in rawData) {
					data.stage = data.getArmature(ObjectDataParser._getString(rawData, DataParser.STAGE, ""))
				} else if (data.armatureNames.length > 0) {
					data.stage = data.getArmature(data.armatureNames[0])
				}

				this._data = null
			}

			if (DataParser.TEXTURE_ATLAS in rawData) {
				this._rawTextureAtlases = rawData[DataParser.TEXTURE_ATLAS] as ArrayList<Any?>?
			}

			return data
		} else {
			console.assert(
				false,
				"Nonsupport data version: " + version + "\n" +
						"Please convert DragonBones data to support version.\n" +
						"Read more: https://github.com/DragonBones/Tools/"
			)
		}

		return null
	}

	override fun parseTextureAtlasData(rawData: Any?, textureAtlasData: TextureAtlasData, scale: Float): Boolean {
		if (rawData == null) {
			if (this._rawTextureAtlases == null || this._rawTextureAtlases!!.length == 0) {
				return false
			}

			val rawTextureAtlas = this._rawTextureAtlases!![this._rawTextureAtlasIndex++]
			this.parseTextureAtlasData(rawTextureAtlas, textureAtlasData, scale)

			if (this._rawTextureAtlasIndex >= this._rawTextureAtlases!!.length) {
				this._rawTextureAtlasIndex = 0
				this._rawTextureAtlases = null
			}

			return true
		}

		// Texture format.
		textureAtlasData.width = ObjectDataParser._getInt(rawData, DataParser.WIDTH, 0)
		textureAtlasData.height = ObjectDataParser._getInt(rawData, DataParser.HEIGHT, 0)
		textureAtlasData.scale =
				if (scale == 1f) (1f / ObjectDataParser._getNumber(rawData, DataParser.SCALE, 1f)) else scale
		textureAtlasData.name = ObjectDataParser._getString(rawData, DataParser.NAME, "")
		textureAtlasData.imagePath = ObjectDataParser._getString(rawData, DataParser.IMAGE_PATH, "")

		if (DataParser.SUB_TEXTURE in rawData) {
			val rawTextures = rawData[DataParser.SUB_TEXTURE] as ArrayList<*>
			//for (var i = 0, l = rawTextures.length; i < l; ++i) {
			for (i in 0 until rawTextures.length) {
				val rawTexture = rawTextures[i]
				val frameWidth = ObjectDataParser._getNumber(rawTexture, DataParser.FRAME_WIDTH, -1f)
				val frameHeight = ObjectDataParser._getNumber(rawTexture, DataParser.FRAME_HEIGHT, -1f)
				val textureData = textureAtlasData.createTexture()

				textureData.rotated = ObjectDataParser._getBoolean(rawTexture, DataParser.ROTATED, false)
				textureData.name = ObjectDataParser._getString(rawTexture, DataParser.NAME, "")
				textureData.region.x = ObjectDataParser._getNumber(rawTexture, DataParser.X, 0f)
				textureData.region.y = ObjectDataParser._getNumber(rawTexture, DataParser.Y, 0f)
				textureData.region.width = ObjectDataParser._getNumber(rawTexture, DataParser.WIDTH, 0f)
				textureData.region.height = ObjectDataParser._getNumber(rawTexture, DataParser.HEIGHT, 0f)

				if (frameWidth > 0f && frameHeight > 0f) {
					textureData.frame = TextureData.createRectangle()
					textureData.frame!!.x = ObjectDataParser._getNumber(rawTexture, DataParser.FRAME_X, 0f)
					textureData.frame!!.y = ObjectDataParser._getNumber(rawTexture, DataParser.FRAME_Y, 0f)
					textureData.frame!!.width = frameWidth
					textureData.frame!!.height = frameHeight
				}

				textureAtlasData.addTexture(textureData)
			}
		}

		return true
	}
}

/**
 * @private
 */
class ActionFrame {
	var frameStart: Int = 0
	//public val actions:  FloatArrayList = FloatArrayList()
	val actions: IntArrayList = IntArrayList()
}

