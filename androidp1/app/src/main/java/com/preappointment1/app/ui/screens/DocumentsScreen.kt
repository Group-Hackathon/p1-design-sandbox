package com.preappointment1.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.preappointment1.app.R
import com.preappointment1.app.data.local.DocumentSource
import com.preappointment1.app.data.local.LocalDocumentEntity
import com.preappointment1.app.data.repository.DocumentsRepository
import com.preappointment1.app.ui.theme.Black
import com.preappointment1.app.ui.theme.Gray200
import com.preappointment1.app.ui.theme.Gray400
import com.preappointment1.app.ui.theme.Gray600
import com.preappointment1.app.ui.theme.White
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    followUp: FollowUpUi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documents by DocumentsRepository.observeDocuments(followUp.id)
        .collectAsState(initial = emptyList())
    var pendingDelete by remember { mutableStateOf<LocalDocumentEntity?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }

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
        containerColor = White,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.documents_title),
                            fontWeight = FontWeight.Bold,
                            color = Black
                        )
                        Text(
                            followUp.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray600,
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
                            tint = Black
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Black)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        }
    ) { padding ->
        if (documents.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = Gray400,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.documents_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.documents_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray600
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
    val sourceLabel = when (document.source) {
        DocumentSource.REPORT -> stringResource(R.string.documents_source_report)
        DocumentSource.PHOTO -> stringResource(R.string.documents_source_photo)
        DocumentSource.PDF -> stringResource(R.string.documents_source_pdf)
        else -> stringResource(R.string.documents_source_other)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Gray200.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (document.source) {
            DocumentSource.REPORT, DocumentSource.PDF -> {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = Black,
                    modifier = Modifier.size(28.dp)
                )
            }
            else -> {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = Black,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                document.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$sourceLabel · $dateLabel",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = Gray600)
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
