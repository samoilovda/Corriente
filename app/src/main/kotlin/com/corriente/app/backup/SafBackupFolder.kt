package com.corriente.app.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.corriente.data.backup.AUTO_BACKUP_PREFIX
import com.corriente.data.backup.AUTO_BACKUP_SUFFIX
import com.corriente.data.backup.namesToPrune
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Запись автобэкапа в выбранную пользователем папку через SAF-дерево (T5.1). Используем
 * [DocumentsContract] напрямую, без `androidx.documentfile` — этой зависимости нет в §1.3,
 * а API фреймворка достаточно для «создать файл / перечислить / удалить».
 *
 * Сеть здесь невозможна в принципе (I-24): это операции над локальным `content://`-провайдером.
 */
class SafBackupFolder(private val context: Context, private val treeUri: Uri) {

    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    /** Создаёт новый файл бэкапа и отдаёт writer через него. */
    suspend fun writeNewBackup(now: Date, write: suspend (java.io.OutputStream) -> Unit) {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val name = "$AUTO_BACKUP_PREFIX${stamp.format(now)}$AUTO_BACKUP_SUFFIX"
        val fileUri = DocumentsContract.createDocument(context.contentResolver, dirUri, "application/json", name)
            ?: error("не удалось создать файл в выбранной папке")
        val out = context.contentResolver.openOutputStream(fileUri) ?: error("не удалось открыть файл на запись")
        out.use { write(it) }
    }

    /** Удаляет самые старые файлы автобэкапа, оставляя [keep] последних. */
    fun prune(keep: Int) {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val byName = mutableMapOf<String, String>() // name -> documentId
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val displayName = c.getString(1) ?: continue
                if (displayName.startsWith(AUTO_BACKUP_PREFIX) && displayName.endsWith(AUTO_BACKUP_SUFFIX)) {
                    byName[displayName] = docId
                }
            }
        }
        namesToPrune(byName.keys.toList(), keep).forEach { name ->
            val docId = byName[name] ?: return@forEach
            runCatching {
                DocumentsContract.deleteDocument(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                )
            }
        }
    }
}
