package com.preappointment1.app.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.preappointment1.app.data.local.AppDatabase
import com.preappointment1.app.data.local.DocumentSource
import com.preappointment1.app.data.local.LocalDocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object DocumentsRepository {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val dao get() = AppDatabase.get(appContext).localDocumentDao()

    fun observeDocuments(followUpId: String): Flow<List<LocalDocumentEntity>> =
        dao.observeForFollowUp(followUpId)

    fun documentsDir(followUpId: String): File {
        val dir = File(appContext.filesDir, "documents/$followUpId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun resolveFile(entity: LocalDocumentEntity): File =
        File(appContext.filesDir, entity.relativePath)

    suspend fun saveReportPdf(
        followUpId: String,
        sourcePdf: File,
        title: String = "Medical report"
    ): LocalDocumentEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val fileName = "report_${System.currentTimeMillis()}.pdf"
        val dest = File(documentsDir(followUpId), fileName)
        sourcePdf.copyTo(dest, overwrite = true)
        val entity = LocalDocumentEntity(
            id = id,
            followUpId = followUpId,
            title = title,
            mimeType = "application/pdf",
            relativePath = "documents/$followUpId/$fileName",
            source = DocumentSource.REPORT
        )
        dao.upsert(entity)
        entity
    }

    suspend fun importFromUri(
        followUpId: String,
        uri: Uri,
        displayName: String?,
        mimeTypeHint: String?
    ): LocalDocumentEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val mime = mimeTypeHint
            ?: appContext.contentResolver.getType(uri)
            ?: "application/octet-stream"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: when {
                mime.startsWith("image/") -> "jpg"
                mime == "application/pdf" -> "pdf"
                else -> "bin"
            }
        val safeTitle = displayName?.takeIf { it.isNotBlank() }
            ?: "Document ${System.currentTimeMillis()}"
        val fileName = "${System.currentTimeMillis()}_$id.$ext"
        val dest = File(documentsDir(followUpId), fileName)
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read selected file")

        val source = when {
            mime.startsWith("image/") -> DocumentSource.PHOTO
            mime == "application/pdf" -> DocumentSource.PDF
            else -> DocumentSource.OTHER
        }
        val entity = LocalDocumentEntity(
            id = id,
            followUpId = followUpId,
            title = safeTitle,
            mimeType = mime,
            relativePath = "documents/$followUpId/$fileName",
            source = source
        )
        dao.upsert(entity)
        entity
    }

    suspend fun delete(entity: LocalDocumentEntity) = withContext(Dispatchers.IO) {
        val file = resolveFile(entity)
        if (file.exists()) file.delete()
        dao.deleteById(entity.id)
    }
}
