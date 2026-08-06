package com.preappointment1.app.ui.components.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.preappointment1.app.ui.theme.*
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberNode
import io.github.sceneview.model.ModelInstance

/**
 * 3D mannequin viewport using Google Filament (Sceneview) for PBR rendering.
 * Black & white design.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyMap3DViewport(
    viewSide: String,
    onToggleViewSide: () -> Unit,
    zoneIds: List<String>,
    zoneLabels: List<String>,
    onSelectionChanged: (regions: List<String>, labels: List<String>) -> Unit,
    onResetSelection: () -> Unit,
    onRemoveRegion: (regionId: String, label: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var modelNode by remember { mutableStateOf<ModelNode?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(White)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
        ) {
            val engine = rememberEngine()
            val modelLoader = rememberModelLoader(engine)
            val modelNode = rememberNode {
                ModelNode(
                    modelInstance = modelLoader.createModelInstance("mannequin_pbr.glb"),
                    scaleToUnits = 0.075f,
                    centerOrigin = Position(0.0f, 0.0f, 0.0f)
                ).apply {
                    playingAnimations.clear()
                    onSingleTapUp = { _ ->
                        val regionList = listOf(
                            "head" to "Tête",
                            "chest" to "Poitrine",
                            "abdomen" to "Abdomen",
                            "back" to "Dos",
                            "arm_left" to "Bras Gauche",
                            "arm_right" to "Bras Droit",
                            "leg_left" to "Jambe Gauche",
                            "leg_right" to "Jambe Droite"
                        )
                        val nextRegion = regionList.firstOrNull { it.first !in zoneIds } ?: regionList.first()
                        onSelectionChanged(listOf(nextRegion.first), listOf(nextRegion.second))
                        true
                    }
                }
            }

            LaunchedEffect(viewSide) {
                modelNode.rotation = io.github.sceneview.math.Rotation(0.0f, if (viewSide == "back") 180.0f else 0.0f, 0.0f)
            }
            
            val cameraNode = rememberCameraNode(engine).apply {
                position = Position(0.0f, 1.4f, 22.0f)
            }

            LaunchedEffect(Unit) {
                var animTime = 0f
                while (true) {
                    kotlinx.coroutines.delay(16)
                    animTime += 0.0015f
                    modelNode.modelInstance?.animator?.apply {
                        if (animationCount > 0) {
                            applyAnimation(0, animTime % getAnimationDuration(0))
                        }
                    }
                }
            }
            
            io.github.sceneview.Scene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                cameraNode = cameraNode,
                childNodes = listOf(modelNode)
            )

            // Floating controls — top right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier.clickable { onToggleViewSide() },
                    shape = RoundedCornerShape(8.dp),
                    color = Black,
                    shadowElevation = 2.dp
                ) {
                    Text(
                        if (viewSide == "front") "Show back" else "Show front",
                        color = White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                if (zoneIds.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.clickable { onResetSelection() },
                        shape = CircleShape,
                        color = Gray200
                    ) {
                        Box(modifier = Modifier.padding(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Bottom label
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(SurfaceLight, RoundedCornerShape(8.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (zoneIds.isEmpty()) "Tap to locate pain" else "${zoneIds.size} area(s) selected",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Black
                )
            }
        }

        // Selected region chips
        if (zoneLabels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                zoneLabels.forEachIndexed { index, label ->
                    val regionId = zoneIds.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .padding(vertical = 3.dp)
                            .background(Black, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label,
                                color = White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (regionId != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = White,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onRemoveRegion(regionId, label) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
