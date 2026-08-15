package com.preappointment1.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.preappointment1.app.R
import com.preappointment1.app.data.local.DocumentSource
import com.preappointment1.app.data.local.LocalDocumentEntity
import com.preappointment1.app.data.repository.DocumentsRepository
import com.preappointment1.app.ui.components.StitchBottomNavBar
import com.preappointment1.app.ui.components.StitchTab
import com.preappointment1.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    followUp: FollowUpUi,
    onBack: () -> Unit,
    activeTab: StitchTab = StitchTab.PREP,
    onTabSelected: ((StitchTab) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documents by DocumentsRepository.observeDocuments(followUp.id)
        .collectAsState(initial = emptyList())
    var pendingDelete by remember { mutableStateOf<LocalDocumentEntity?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }

    LaunchedEffect(followUp.id) {
        DocumentsRepository.seedSampleDocumentsIfEmpty(followUp.id)
    }

    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers do not support persistable permissions.
        }
        scope.launch {
            try {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
                DocumentsRepository.importFromUri(
                    followUpId = followUp.id,
                    uri = uri,
                    displayName = name,
                    mimeTypeHint = context.contentResolver.getType(uri)
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.documents_added),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.documents_add_failed, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.documents_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )
                        Text(
                            followUp.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = SagePrimary
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = SagePrimary)
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.documents_add_photo)) },
                                onClick = {
                                    showAddMenu = false
                                    pickDocument.launch(arrayOf("image/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.documents_add_pdf)) },
                                onClick = {
                                    showAddMenu = false
                                    pickDocument.launch(arrayOf("application/pdf"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.documents_add_any)) },
                                onClick = {
                                    showAddMenu = false
                                    pickDocument.launch(arrayOf("*/*"))
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasBackground)
            )
        },
        bottomBar = {
            if (onTabSelected != null) {
                StitchBottomNavBar(
                    currentTab = activeTab,
                    onTabSelected = onTabSelected
                )
            }
        }
    ) { padding ->
        if (documents.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MintBadge),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = SagePrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(R.string.documents_empty_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.documents_empty_body),
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showAddMenu = true },
                    modifier = Modifier.height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SagePrimary)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.documents_add_btn), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    DocumentRow(
                        document = doc,
                        onOpen = {
                            openDocument(context, doc)
                        },
                        onDelete = { pendingDelete = doc }
                    )
                }
            }
        }
    }

    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.documents_delete_title)) },
            text = { Text(stringResource(R.string.documents_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        DocumentsRepository.delete(doc)
                        pendingDelete = null
                    }
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DocumentRow(
    document: LocalDocumentEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val dateLabel = remember(document.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(document.createdAt))
    }
    val sourceLabel = stringResource(
        when (document.source) {
            DocumentSource.REPORT -> R.string.documents_source_report
            DocumentSource.PHOTO -> R.string.documents_source_photo
            DocumentSource.PDF -> R.string.documents_source_pdf
            else -> R.string.documents_source_other
        }
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp), spotColor = SagePrimary.copy(alpha = 0.06f))
            .clip(RoundedCornerShape(20.dp))
            .background(CardBackground)
            .border(1.dp, CardBorderSoft, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MintBadge),
                contentAlignment = Alignment.Center
            ) {
                when (document.source) {
                    DocumentSource.REPORT, DocumentSource.PDF -> {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DocumentSource.PHOTO -> {
                        Icon(
                            imageVector = Icons.Outlined.Photo,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$sourceLabel · $dateLabel",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun openDocument(context: android.content.Context, document: LocalDocumentEntity) {
    try {
        val file = DocumentsRepository.resolveFile(document)
        if (!file.exists()) {
            Toast.makeText(context, context.getString(R.string.documents_missing_file), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, document.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.documents_open)))
    } catch (e: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.documents_open_failed, e.message ?: ""),
            Toast.LENGTH_SHORT
        ).show()
    }
}
