package com.preappointment1.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.preappointment1.app.ui.components.LpmPrimaryButton
import com.preappointment1.app.ui.components.StitchBottomNavBar
import com.preappointment1.app.ui.components.StitchTab
import com.preappointment1.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    followUp: FollowUpUi,
    onBack: () -> Unit,
    activeTab: StitchTab = StitchTab.PROGRESS,
    onTabSelected: ((StitchTab) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isGenerating by remember { mutableStateOf(true) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.report_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SagePrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CanvasBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            if (onTabSelected != null) {
                StitchBottomNavBar(
                    currentTab = activeTab,
                    onTabSelected = onTabSelected
                )
            }
        },
        containerColor = CanvasBackground,
        modifier = modifier
    ) { padding ->
        if (isGenerating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = SagePrimary,
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.5.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.report_generating),
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LpmPrimaryButton(
                        text = stringResource(R.string.action_go_back),
                        onClick = onBack,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // PDF Preview with Sage/Mint cards
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Summary Header Card
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, RoundedCornerShape(22.dp), spotColor = SagePrimary.copy(alpha = 0.08f))
                                .clip(RoundedCornerShape(22.dp))
                                .background(CardBackground)
                                .border(1.dp, CardBorderSoft, RoundedCornerShape(22.dp))
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MintBadge),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = SagePrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.report_file_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.report_file_subtitle),
                                        fontSize = 13.sp,
                                        color = TextSecondary,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }

                    items(pageBitmaps.size) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.08f))
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, CardBorderSoft, RoundedCornerShape(16.dp))
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
                            color = TextMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Action Bar
                Surface(
                    color = CardBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                                                title = context.getString(R.string.report_doc_title, followUp.title)
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
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(CardBorderSoft)
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder),
                                contentDescription = null,
                                tint = SagePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isSaving) stringResource(R.string.report_saving) else stringResource(R.string.report_save_to_prep),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = {
                                pdfFile?.let { file ->
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Doctor Briefing — ${followUp.title}")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.report_share_chooser_title)))
                                }
                            },
                            enabled = pdfFile != null,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(52.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SagePrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.report_share_pdf), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun renderPdfPages(file: File): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    try {
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val scale = 2
            val bitmap = Bitmap.createBitmap(
                page.width * scale,
                page.height * scale,
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmaps.add(bitmap)
        }
    } finally {
        renderer.close()
        pfd.close()
    }
    return bitmaps
}
