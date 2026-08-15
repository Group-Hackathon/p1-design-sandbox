package com.preappointment1.app.ui.components.checkin

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.filled.Add
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
    // Some OEM camera apps refuse to write into a target that does not exist yet.
    photoFile.parentFile?.mkdirs()
    if (!photoFile.exists()) photoFile.createNewFile()
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
}

/**
 * Readout and stepper that sits directly under one of the floating 3D instruments.
 * Monochrome by design so it reads as part of the same instrument.
 */
@Composable
private fun GadgetReadout(
    label: String,
    value: String,
    unit: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onUnitClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(104.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.45f),
            letterSpacing = 3.sp
        )
        Text(
            value,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .then(if (onUnitClick != null) Modifier.clickable { onUnitClick() } else Modifier)
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.45f),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepperButton("−", onDecrement)
            StepperButton("+", onIncrement)
        }
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
    }
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
    var cameraError by remember { mutableStateOf<String?>(null) }
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

    /**
     * Starts the capture intent. The manifest declares CAMERA, and Android refuses to
     * start ACTION_IMAGE_CAPTURE from an app that declares the permission without
     * holding it — so this must only run once the grant is in hand. The launch is
     * guarded anyway: an OEM camera that rejects the intent should surface a message,
     * not take the process down.
     */
    /** Drops the placeholder left by Confirm when a capture never happens. */
    fun dropPendingDraft() {
        val idx = photoEntries.indexOfLast { it.filename == "__pending__" }
        if (idx >= 0) photoEntries = photoEntries.toMutableList().apply { removeAt(idx) }
    }

    fun launchCamera() {
        try {
            val uri = createTempImageUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("CheckInScreen", "Camera launch failed", e)
            cameraUri = null
            dropPendingDraft()
            cameraError = "Camera unavailable — pick from the gallery instead."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            dropPendingDraft()
            cameraError = "Camera permission denied — pick from the gallery instead."
        }
    }

    fun takePhoto() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    /** Discards a draft photo: drops the entry, its file, and any stale references. */
    fun removePhotoAt(index: Int) {
        val entry = photoEntries.getOrNull(index) ?: return
        photoEntries = photoEntries.toMutableList().apply { removeAt(index) }
        if (photoFilename == entry.filename) {
            photoFilename = photoEntries.lastOrNull { it.filename != "__pending__" }?.filename
        }
        if (entry.filename != "__pending__") {
            runCatching { File(context.filesDir, entry.filename).delete() }
        }
        // Indices shift after a removal, so the cached dot/line anchors are stale.
        thumbPositions.clear()
        fullscreenPhotoIndex = -1
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
            .background(Color(0xF0121C1A))
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
            // Texture baked into GLB — no runtime baseColorFactor override needed
            modelNode.playingAnimations.forEach { (_, anim) ->
                anim.speed = 0.2f
            }
        }

        // Floating monochrome 3D instruments either side of the mannequin
        val painLadder = rememberPainLadderGadget(modelLoader)
        val thermometer = rememberThermometerGadget(modelLoader)

        LaunchedEffect(level) { painLadder.update(level) }
        LaunchedEffect(temperature) {
            thermometer.update(temperatureFraction(temperature))
        }

        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            mainLightNode = mainLightNode,
            childNodes = listOf(modelNode) + painLadder.nodes + thermometer.nodes
        )

        // Interaction overlay (between Scene and HUD): drag to spin the mannequin.
        // Photo capture is driven by the dedicated shutter button, so a stray tap
        // anywhere on the scene no longer drops the user into photo mode.
        Box(
            modifier = Modifier
                .fillMaxSize()
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

                // Compact actions, parked in the gap between the mannequin's feet and
                // the readouts so they never cover the PAIN / TEMP labels.
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 232.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clickable {
                                photoMode = false
                                frameOffsetX = 0f
                                frameOffsetY = 0f
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "CANCEL",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .clickable {
                                val savedX = frameOffsetX
                                val savedY = frameOffsetY
                                photoMode = false
                                frameOffsetX = 0f
                                frameOffsetY = 0f
                                photoEntries = photoEntries + PhotoEntry("__pending__", savedX, savedY)
                                showCameraSheet = true
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "CONFIRM",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
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
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // --- MIDDLE: readouts + steppers sitting under the 3D instruments ---
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // LEFT: pain readout + stepper, under the 3D ladder
                    GadgetReadout(
                        label = "PAIN",
                        value = "$level",
                        unit = "/ 10",
                        onDecrement = { if (level > 0) level -= 1 },
                        onIncrement = { if (level < 10) level += 1 }
                    )

                    // CENTRE: photo shutter, directly below the mannequin's feet so it
                    // never overlaps either instrument or the carousel.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "PHOTO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.45f),
                            letterSpacing = 3.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .clickable { photoMode = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add photo",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // RIGHT: temperature readout + stepper, under the 3D thermometer
                    val displayTemp = if (isCelsius) temperature else temperature * 9f / 5f + 32f
                    GadgetReadout(
                        label = "TEMP",
                        value = "%.1f".format(displayTemp),
                        unit = if (isCelsius) "°C" else "°F",
                        onUnitClick = { isCelsius = !isCelsius },
                        onDecrement = { if (temperature > 35.5f) temperature -= 0.2f },
                        onIncrement = { if (temperature < 41.0f) temperature += 0.2f }
                    )
                }
            }


            // --- BOTTOM SECTION: PHOTOS + ZONES + SAVE ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Photo carousel — occupies the slot the shutter used to sit in
                if (photoEntries.isNotEmpty()) {
                    LazyRow(
                        state = carouselState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(photoEntries) { index, entry ->
                            // Extra top/end padding leaves room for the delete badge to
                            // overhang the thumbnail without being clipped.
                            Box(modifier = Modifier.padding(top = 6.dp, end = 6.dp)) {
                                Surface(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .onGloballyPositioned { coords ->
                                            val pos = coords.positionInWindow()
                                            val sz = coords.size
                                            thumbPositions[index] = Offset(
                                                pos.x + sz.width / 2f,
                                                pos.y + sz.height / 2f
                                            )
                                        }
                                        .clickable { fullscreenPhotoIndex = index },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.06f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, Color.White.copy(alpha = 0.5f)
                                    )
                                ) {
                                    val photoFile = File(LocalContext.current.filesDir, entry.filename)
                                    AsyncImage(
                                        model = photoFile,
                                        contentDescription = "Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                // Discard this draft
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-6).dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black)
                                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                        .clickable { removePhotoAt(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected Zone Badges (Mint Badge Theme)
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
                                    .background(MintBadge)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        label,
                                        color = MintBadgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (regionId != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MintBadgeText,
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

                // Save Action Button — Sage Primary capsule
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SagePrimary)
                        .clickable {
                            val finalQualities = PAIN_QUALITIES
                                .filter { qualities.contains(it) }
                                .toMutableList()
                            // The carousel is the single source of truth, so discarded
                            // drafts stay out and nothing is recorded twice. Placeholders
                            // for shots that were never taken are skipped.
                            photoEntries
                                .map { it.filename }
                                .filter { it != "__pending__" }
                                .distinct()
                                .forEach { finalQualities.add("Photo: $it") }
                            onSubmit(
                                level, temperature, zoneIds, zoneLabels,
                                finalQualities, mobilityImpact, temporalPattern
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            submitLabel.uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = Color.White
                        )
                    }
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
                            takePhoto()
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

        // --- 4. TRANSIENT CAMERA MESSAGE ---
        cameraError?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3500)
                cameraError = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xF01A1A1A))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
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

                // Discard from the preview itself, so a draft can be dropped without
                // hunting for the small badge on the thumbnail.
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable { removePhotoAt(fullscreenPhotoIndex) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "DELETE",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
