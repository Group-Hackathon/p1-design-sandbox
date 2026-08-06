package com.preappointment1.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.preappointment1.app.R
import com.preappointment1.app.data.SessionManager
import com.preappointment1.app.data.repository.DocumentsRepository
import com.preappointment1.app.data.repository.ReportRepository
import com.preappointment1.app.data.repository.TimelineRepository
import com.preappointment1.app.report.PdfReportGenerator
import com.preappointment1.app.ui.components.*
import com.preappointment1.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReportScreen(
    followUp: FollowUpUi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isGenerating by remember { mutableStateOf(true) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Generate the PDF on screen load
    LaunchedEffect(followUp.id) {
        try {
            val events = withContext(Dispatchers.IO) {
                TimelineRepository.getEvents(followUp.id)
            }
            val patientName = SessionManager.getUserName() ?: "Patient"

            val persistentFile = File(ReportRepository.reportsDir(), "report_${followUp.id}.pdf")
            val file = withContext(Dispatchers.IO) {
                val generated = PdfReportGenerator.generate(
                    context = context,
                    followUp = followUp,
                    events = events,
                    patientName = patientName
                )
                generated.copyTo(persistentFile, overwrite = true)
                ReportRepository.cacheReport(followUp.id, persistentFile.absolutePath)
                persistentFile
            }
            pdfFile = file

            // Render pages as bitmaps for preview
            val bitmaps = withContext(Dispatchers.IO) {
                renderPdfPages(file)
            }
            pageBitmaps = bitmaps
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Failed to generate report: ${e.message}"
        } finally {
            isGenerating = false
        }
    }

    Scaffold(
        topBar = { LpmTopBar(title = "Medical Report", onBack = onBack) },
        containerColor = Gray50,
        modifier = modifier
    ) { padding ->
        if (isGenerating) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Generating report...", color = Gray600)
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("⚠️", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray600,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LpmPrimaryButton("Try again", onClick = onBack)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // PDF Preview
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pageBitmaps.size) { index ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = White)
                        ) {
                            Image(
                                bitmap = pageBitmaps[index].asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        Text(
                            "${pageBitmaps.size} page${if (pageBitmaps.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Gray400,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Action buttons
                Surface(
                    color = White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pdfFile?.let { file ->
                                    try {
                                        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val printAdapter = android.print.PrintDocumentAdapter::class.java
                                        // Use WebView print for simplicity
                                        Toast.makeText(context, "Use the share button to print", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Print not available", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Black)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print", color = Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val file = pdfFile ?: return@OutlinedButton
                                if (isSaving) return@OutlinedButton
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            DocumentsRepository.saveReportPdf(
                                                followUpId = followUp.id,
                                                sourcePdf = file,
                                                title = "Medical report — ${followUp.title}"
                                            )
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.report_saved_to_folder),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.report_save_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = pdfFile != null && !isSaving,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Black)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder),
                                contentDescription = null,
                                tint = Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.report_save_to_folder),
                                color = Black,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = {
                                pdfFile?.let { file ->
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Medical Follow-Up Report — ${followUp.title}")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share report"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Black)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders all pages of a PDF file as bitmaps for preview.
 */
private fun renderPdfPages(file: File): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(fd)

    for (i in 0 until renderer.pageCount) {
        val page = renderer.openPage(i)
        // Render at 2x for quality
        val scale = 2
        val bitmap = Bitmap.createBitmap(
            page.width * scale,
            page.height * scale,
            Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmaps.add(bitmap)
        page.close()
    }

    renderer.close()
    fd.close()
    return bitmaps
}
