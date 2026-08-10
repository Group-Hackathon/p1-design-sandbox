package com.preappointment1.app.ui.components.checkin

import androidx.compose.runtime.Composable
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberNode

/**
 * Monochrome 3D instruments that float beside the mannequin in the check-in scene.
 *
 * The scene assigns modelNode.scale explicitly, which overrides scaleToUnits, so the
 * mannequin renders at its native Mixamo height of roughly 1.75 world units. These
 * instruments are sized and placed against that scale, not against scaleToUnits.
 *
 *   pain ladder  x = -GADGET_X, rungs stacked from y = BASE_Y upward
 *   thermometer  x = +GADGET_X, bulb at y = BASE_Y, tube rising above it
 */
private const val BASE_Y = 0.085f
private const val GADGET_X = 0.78f

/** Shrinks both instruments without moving them: they stay anchored at BASE_Y. */
private const val GADGET_SCALE = 0.72f

const val PAIN_RUNGS = 10

private const val RUNG_SPACING = 0.125f * GADGET_SCALE
private const val PLATE_BASE_WIDTH = 0.280f

// A slight taper keeps the ladder reading as one column while still suggesting escalation.
private const val RUNG_MIN_WIDTH = 0.230f
private const val RUNG_MAX_WIDTH = 0.330f

private const val BULB_RADIUS = 0.147f * GADGET_SCALE

// Greyscale levels — the whole HUD stays black & white. The scene light runs at
// 180k lux, so an idle grey has to be very low to still read as "off".
private val ACTIVE = floatArrayOf(1.0f, 1.0f, 1.0f)
private val IDLE = floatArrayOf(0.035f, 0.035f, 0.035f)

private fun ModelNode.tint(rgb: FloatArray, alpha: Float = 1.0f) {
    modelInstance?.materialInstances?.forEach { material ->
        try {
            material.setParameter("baseColorFactor", rgb[0], rgb[1], rgb[2], alpha)
        } catch (_: Exception) {
        }
    }
}

/** The three nodes that make up the thermometer, plus the update entry point. */
class ThermometerGadget(
    val track: ModelNode,
    val fluid: ModelNode,
    val bulb: ModelNode
) {
    val nodes: List<ModelNode> get() = listOf(track, fluid, bulb)

    /** [fraction] is the 0..1 fill level of the mercury column. */
    fun update(fraction: Float) {
        val f = fraction.coerceIn(0.04f, 1.0f)
        fluid.scale = Position(GADGET_SCALE, GADGET_SCALE * f, GADGET_SCALE)
        track.tint(IDLE)
        fluid.tint(ACTIVE)
        bulb.tint(ACTIVE)
    }
}

/** The stack of rungs that make up the pain ladder, plus the update entry point. */
class PainLadderGadget(val rungs: List<ModelNode>) {
    val nodes: List<ModelNode> get() = rungs

    /** Lights up every rung at or below [level] (0..10). */
    fun update(level: Int) {
        rungs.forEachIndexed { index, rung ->
            rung.tint(if (index < level) ACTIVE else IDLE)
        }
    }
}

@Composable
fun rememberThermometerGadget(modelLoader: ModelLoader): ThermometerGadget {
    val track = rememberNode {
        ModelNode(modelLoader.createModelInstance("thermo_track.glb")!!).apply {
            isEditable = false
            position = Position(GADGET_X, BASE_Y + BULB_RADIUS * 0.6f, 0.0f)
            scale = Position(GADGET_SCALE, GADGET_SCALE, GADGET_SCALE)
        }
    }
    val fluid = rememberNode {
        ModelNode(modelLoader.createModelInstance("thermo_fluid.glb")!!).apply {
            isEditable = false
            position = Position(GADGET_X, BASE_Y + BULB_RADIUS * 0.6f, 0.0f)
            scale = Position(GADGET_SCALE, GADGET_SCALE, GADGET_SCALE)
        }
    }
    val bulb = rememberNode {
        ModelNode(modelLoader.createModelInstance("thermo_bulb.glb")!!).apply {
            isEditable = false
            position = Position(GADGET_X, BASE_Y, 0.0f)
            scale = Position(GADGET_SCALE, GADGET_SCALE, GADGET_SCALE)
        }
    }
    return ThermometerGadget(track, fluid, bulb)
}

@Composable
fun rememberPainLadderGadget(modelLoader: ModelLoader): PainLadderGadget {
    // Rungs widen as they climb, so the ladder reads as escalating intensity.
    val rungs = (0 until PAIN_RUNGS).map { index ->
        rememberNode {
            val t = index / (PAIN_RUNGS - 1).toFloat()
            val width = RUNG_MIN_WIDTH + (RUNG_MAX_WIDTH - RUNG_MIN_WIDTH) * t
            val widthScale = width / PLATE_BASE_WIDTH * GADGET_SCALE
            ModelNode(modelLoader.createModelInstance("pain_plate.glb")!!).apply {
                isEditable = false
                position = Position(-GADGET_X, BASE_Y + index * RUNG_SPACING, 0.0f)
                scale = Position(widthScale, GADGET_SCALE, GADGET_SCALE)
            }
        }
    }
    return PainLadderGadget(rungs)
}

/** Maps a temperature in °C onto the mercury column's fill fraction. */
fun temperatureFraction(celsius: Float): Float =
    ((celsius - 35.0f) / 6.0f).coerceIn(0f, 1f)
