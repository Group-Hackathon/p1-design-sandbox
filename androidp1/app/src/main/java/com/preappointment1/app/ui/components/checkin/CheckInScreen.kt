package com.preappointment1.app.ui.components.checkin

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preappointment1.app.ui.theme.*
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberNode

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput

import com.preappointment1.app.ui.components.PAIN_QUALITIES
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PhotoEntry(
    val filename: String,
    val frameX: Float,
    val frameY: Float
)

private fun savePhotoToFile(context: android.content.Context, uri: Uri): String? {
    return try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val destFile = File(context.filesDir, "photo_$timestamp.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile.name
    } catch (e: Exception) {
        Log.e("CheckInScreen", "Failed to save photo", e)
        null
    }
}

private fun createTempImageUri(context: android.content.Context): Uri {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val photoFile = File(context.filesDir, "capture_$timestamp.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
}

/**
 * Spatial Full-Page Futuristic 3D Check-In Screen.
 * Pure Monochrome (Black & White) with floating spatial HUD controls.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    submitLabel: String,
    onClose: (() -> Unit)? = null,
    onSubmit: (
        level: Int,
        temperature: Float,
        zoneIds: List<String>,
        zoneLabels: List<String>,
        qualities: List<String>,
        mobilityImpact: String,
        temporalPattern: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoneIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var zoneLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var level by remember { mutableIntStateOf(3) }
    var temperature by remember { mutableFloatStateOf(36.6f) }
    var isCelsius by remember { mutableStateOf(true) }
    var qualities by remember { mutableStateOf(setOf<String>()) }
    var mobilityImpact by remember { mutableStateOf("Normal") }
    var temporalPattern by remember { mutableStateOf("Constant") }

    var mannequinRotationY by remember { mutableFloatStateOf(0f) }
    var mannequinScaleFactor by remember { mutableFloatStateOf(1.0f) }

    var showRegionSheet by remember { mutableStateOf(false) }
    var showCameraSheet by remember { mutableStateOf(false) }
    var photoFilename by remember { mutableStateOf<String?>(null) }
    var photoEntries by remember { mutableStateOf<List<PhotoEntry>>(emptyList()) }
    var photoMode by remember { mutableStateOf(false) }
    var frameOffsetX by remember { mutableFloatStateOf(0f) }
    var frameOffsetY by remember { mutableFloatStateOf(0f) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var fullscreenPhotoIndex by remember { mutableStateOf(-1) }
    val carouselState = rememberLazyListState()
    val thumbPositions = remember { mutableStateMapOf<Int, Offset>() }
    var rootWindowOffset by remember { mutableStateOf(Offset.Zero) }

    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            val filename = savePhotoToFile(context, cameraUri!!)
            if (filename != null) {
                photoFilename = filename
                val lastIdx = photoEntries.indexOfLast { it.filename == "__pending__" }
                if (lastIdx >= 0) {
                    val pending = photoEntries[lastIdx]
                    photoEntries = photoEntries.toMutableList().apply {
                        set(lastIdx, PhotoEntry(filename, pending.frameX, pending.frameY))
                    }
                } else {
                    photoEntries = photoEntries + PhotoEntry(filename, 0f, 0f)
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val filename = savePhotoToFile(context, uri)
            if (filename != null) {
                photoFilename = filename
                val lastIdx = photoEntries.indexOfLast { it.filename == "__pending__" }
                if (lastIdx >= 0) {
                    val pending = photoEntries[lastIdx]
                    photoEntries = photoEntries.toMutableList().apply {
                        set(lastIdx, PhotoEntry(filename, pending.frameX, pending.frameY))
                    }
                } else {
                    photoEntries = photoEntries + PhotoEntry(filename, 0f, 0f)
                }
            }
        }
    }

    val allRegions = listOf(
        "head" to "Head",
        "neck" to "Neck",
        "shoulders" to "Shoulders",
        "chest" to "Chest",
        "back" to "Back",
        "abdomen" to "Abdomen",
        "arms" to "Arms",
        "hands" to "Hands",
        "legs" to "Legs",
        "feet" to "Feet"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                rootWindowOffset = Offset(pos.x, pos.y)
            }
    ) {
        // --- 1. FULL PAGE 3D SCENE BACKGROUND ---
        val engine = rememberEngine()
        val modelLoader = rememberModelLoader(engine)
        val modelNode = rememberNode {
            ModelNode(
                modelInstance = modelLoader.createModelInstance("mannequin_pbr.glb")!!,
                scaleToUnits = 0.28f,
                centerOrigin = Position(0.0f, 0.0f, 0.0f)
            ).apply {
                // No onSingleTapUp - handled by Compose overlay instead
            }
        }

        LaunchedEffect(mannequinRotationY, mannequinScaleFactor) {
            modelNode.rotation = io.github.sceneview.math.Rotation(0.0f, mannequinRotationY, 0.0f)
            modelNode.scale = Position(mannequinScaleFactor, mannequinScaleFactor, mannequinScaleFactor)
        }
        
        val mainLightNode = io.github.sceneview.rememberMainLightNode(engine).apply {
            intensity = 180000.0f
        }
        val cameraNode = rememberCameraNode(engine).apply {
            position = Position(0.0f, 0.4f, 6.0f)
        }

        LaunchedEffect(modelNode) {
            modelNode.modelInstance?.materialInstances?.forEach { material ->
                try {
                    material.setParameter("baseColorFactor", 0.75f, 0.75f, 0.75f, 1.0f)
                } catch (_: Exception) {}
            }
            modelNode.playingAnimations.forEach { (_, anim) ->
                anim.speed = 0.2f
            }
        }

        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            mainLightNode = mainLightNode,
            childNodes = listOf(modelNode)
        )

        // Interaction overlay (between Scene and HUD): drag=rotate, tap=photo mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { photoMode = true }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        mannequinRotationY += dragAmount.x * 0.15f
                    }
                }
        )

        // Photo position dots + connecting lines to thumbnails
        if (photoEntries.isNotEmpty() && rootWindowOffset != Offset.Zero) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                val dens = density

                photoEntries.forEachIndexed { index, entry ->
                    val dx = cx + entry.frameX * dens / 3f
                    val dy = cy + entry.frameY * dens / 3f

                    // Solid white dot
                    drawCircle(color = Color.White, radius = 5f, center = Offset(dx, dy))
                    drawCircle(
                        color = Color.White.copy(alpha = 0.4f),
                        radius = 10f,
                        center = Offset(dx, dy),
                        style = Stroke(width = 1f, pathEffect = dashEffect)
                    )

                    // Line to thumbnail (using window-relative positions)
                    val thumbPos = thumbPositions[index]
                    if (thumbPos != null) {
                        val relThumbX = thumbPos.x - rootWindowOffset.x
                        val relThumbY = thumbPos.y - rootWindowOffset.y
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(dx, dy + 12f),
                            end = Offset(relThumbX, relThumbY),
                            strokeWidth = 1.5f,
                            pathEffect = dashEffect
                        )
                    }
                }
            }
        }

        // --- PHOTO MODE OVERLAY: draggable frame on mannequin ---
        if (photoMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            frameOffsetX += dragAmount.x * 0.7f
                            frameOffsetY += dragAmount.y * 0.7f
                        }
                    }
            ) {
                // Top instruction bar
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp)
                        .statusBarsPadding(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xCC000000)
                ) {
                    Text(
                        "Drag frame to target area",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Draggable frame
                Box(
                    modifier = Modifier
                        .offset(x = (frameOffsetX / 3).dp, y = (frameOffsetY / 3).dp)
                        .size(width = 64.dp, height = 64.dp)
                        .align(Alignment.Center)
                        .border(2.dp, White, RoundedCornerShape(8.dp))
                )

                // Bottom buttons - above save button area
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 180.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.clickable {
                            photoMode = false
                            frameOffsetX = 0f
                            frameOffsetY = 0f
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF444444)
                    ) {
                        Text("Cancel", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Surface(
                        modifier = Modifier.clickable {
                            val savedX = frameOffsetX
                            val savedY = frameOffsetY
                            photoMode = false
                            frameOffsetX = 0f
                            frameOffsetY = 0f
                            photoEntries = photoEntries + PhotoEntry("__pending__", savedX, savedY)
                            showCameraSheet = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = White
                    ) {
                        Text("Confirm", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 2. FLOATING SPATIAL HUD OVERLAY ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- TOP HEADER (Close Button Only) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onClose != null) {
                    Surface(
                        modifier = Modifier.clickable { onClose() },
                        shape = CircleShape,
                        color = Color(0xFF222222)
                    ) {
                        Box(modifier = Modifier.padding(10.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // --- MIDDLE WIDGETS (Pain & Temp Floating Cards) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT HUD: Pain Intensity
                Surface(
                    modifier = Modifier.width(96.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141414),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "PAIN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFAAAAAA),
                            letterSpacing = 1.sp
                        )
                        Text(
                            "$level/10",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            listOf(0, 2, 4, 6, 8, 10).forEach { l ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (level == l) White else Color(0xFF262626))
                                        .clickable { level = l },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$l",
                                        color = if (level == l) Black else White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // RIGHT HUD: Temperature
                val displayTemp = if (isCelsius) temperature else temperature * 9f / 5f + 32f
                val tempUnit = if (isCelsius) "°C" else "°F"
                Surface(
                    modifier = Modifier.width(96.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141414),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "TEMP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFAAAAAA),
                            letterSpacing = 1.sp
                        )
                        Text(
                            "%.1f%s".format(displayTemp, tempUnit),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = White
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(28.dp).clickable {
                                    if (temperature > 35.5f) temperature -= 0.2f
                                },
                                shape = CircleShape,
                                color = Color(0xFF262626)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("-", fontWeight = FontWeight.Bold, color = White)
                                }
                            }
                            Surface(
                                modifier = Modifier.size(28.dp).clickable {
                                    if (temperature < 41.0f) temperature += 0.2f
                                },
                                shape = CircleShape,
                                color = White
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("+", fontWeight = FontWeight.Bold, color = Black)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { isCelsius = !isCelsius },
                            color = Color(0xFF262626)
                        ) {
                            Text(
                                if (isCelsius) "°C → °F" else "°F → °C",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFAAAAAA),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // --- BOTTOM SECTION: PHOTOS + ZONES + SAVE ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // + Photo button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { photoMode = true },
                    color = Color(0xFF1A1A1A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("+", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Photo",
                            color = White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Photo carousel
                if (photoEntries.isNotEmpty()) {
                    LazyRow(
                        state = carouselState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(photoEntries) { index, entry ->
                            Surface(
                                modifier = Modifier
                                    .size(52.dp)
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInWindow()
                                        val sz = coords.size
                                        thumbPositions[index] = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                                    }
                                    .clickable { fullscreenPhotoIndex = index },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, White)
                            ) {
                                val photoFile = File(LocalContext.current.filesDir, entry.filename)
                                AsyncImage(
                                    model = photoFile,
                                    contentDescription = "Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                // Selected Zone Badges (Pure Monochrome)
                if (zoneLabels.isNotEmpty() || photoFilename != null) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        maxItemsInEachRow = 4
                    ) {
                        zoneLabels.forEachIndexed { idx, label ->
                            val regionId = zoneIds.getOrNull(idx)
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(White)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        label,
                                        color = Black,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (regionId != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Black,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable {
                                                    zoneIds = zoneIds - regionId
                                                    zoneLabels = zoneLabels - label
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Save Action Button
                Button(
                    onClick = {
                        val finalQualities = PAIN_QUALITIES.filter { qualities.contains(it) }.toMutableList()
                        photoFilename?.let { finalQualities.add("Photo: $it") }
                        photoEntries.forEach { entry ->
                            finalQualities.add("Photo: ${entry.filename}")
                        }

                        onSubmit(
                            level,
                            temperature,
                            zoneIds,
                            zoneLabels,
                            finalQualities,
                            mobilityImpact,
                            temporalPattern
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = White,
                        contentColor = Black
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        submitLabel.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // --- 3. CAMERA / GALLERY PICKER SHEET ---
        if (showCameraSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCameraSheet = false },
                containerColor = White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "ADD PHOTO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Black
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            showCameraSheet = false
                            val uri = createTempImageUri(context)
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Black)
                    ) {
                        Text("Take Photo", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            showCameraSheet = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Black)
                    ) {
                        Text("Choose from Gallery", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        // --- 5. FULLSCREEN PHOTO VIEWER ---
        if (fullscreenPhotoIndex >= 0 && fullscreenPhotoIndex < photoEntries.size) {
            val entry = photoEntries[fullscreenPhotoIndex]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black.copy(alpha = 0.95f))
                    .clickable { fullscreenPhotoIndex = -1 }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(28.dp)
                        .clickable { fullscreenPhotoIndex = -1 }
                )
                val photoFile = File(context.filesDir, entry.filename)
                AsyncImage(
                    model = photoFile,
                    contentDescription = "Photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
