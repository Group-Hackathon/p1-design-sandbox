package com.preappointment1.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.preappointment1.app.ui.theme.Gray400
import com.preappointment1.app.ui.theme.White
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeasurementPhotoCapture(
    onPhotoCaptured: (String) -> Unit,
    modifier: Modifier = Modifier,
    previewHeight: Int = 200
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasPermission = granted }
    )

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = modifier) {
        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera access is required.", color = Gray400)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LpmPrimaryButton(
                text = "Allow camera",
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        } else {
            LpmBodyText("Place the area to track inside the frame.")
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (exc: Exception) {
                                Log.e("MeasurementPhoto", "Camera bind failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .align(Alignment.Center)
                        .alpha(0.4f)
                        .border(2.dp, White, RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            LpmPrimaryButton(
                text = if (isCapturing) "Capturing…" else "Take photo",
                onClick = {
                    if (isCapturing) return@LpmPrimaryButton
                    isCapturing = true
                    val photoFile = File(
                        context.filesDir,
                        "measurement_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
                    )
                    photoFile.parentFile?.mkdirs()
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                isCapturing = false
                                onPhotoCaptured(photoFile.name)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("MeasurementPhoto", "Capture failed", exception)
                                isCapturing = false
                            }
                        }
                    )
                },
                enabled = !isCapturing
            )
        }
    }
}
