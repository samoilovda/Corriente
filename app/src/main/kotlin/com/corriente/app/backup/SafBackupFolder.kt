package com.corriente.app.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.corriente.app.R
import com.corriente.data.backup.AUTO_BACKUP_PREFIX
import com.corriente.data.backup.AUTO_BACKUP_SUFFIX
import com.corriente.data.backup.namesToPrune
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Один файл автобэкапа в папке (R1.3): то, что нужно экрану — имя, дата, размер, идентификатор для чтения. */
data class SafBackupFile(
    val documentId: String,
    val name: String,
    val lastModified: Long,
    val size: Long,
)

/** Что экрану нужно от папки автобэкапа: перечислить файлы и открыть один из них (A1.3 — подменяется в тестах). */
interface BackupFolderSource {
    fun list(): List<SafBackupFile>
    fun openInputStream(documentId: String): InputStream
}

/**
 * A1.3: результат перечисления папки — доступ к папке мог пропасть (удалена, разрешение отозвано),
 * это не сбой приложения, а состояние, которое экран показывает пользователю.
 */
sealed interface BackupFilesResult {
    data class Files(val files: List<SafBackupFile>) : BackupFilesResult
    data object FolderUnavailable : BackupFilesResult
}

/** Перечисляет [source]; любое исключение провайдера (SecurityException, FileNotFoundException, …) — [BackupFilesResult.FolderUnavailable]. */
fun loadBackupFiles(source: BackupFolderSource): BackupFilesResult =
    try {
        BackupFilesResult.Files(source.list())
    } catch (e: Exception) {
        BackupFilesResult.FolderUnavailable
    }

/**
 * Запись автобэкапа в выбранную пользователем папку через SAF-дерево (T5.1). Используем
 * [DocumentsContract] напрямую, без `androidx.documentfile` — этой зависимости нет в §1.3,
 * а API фреймворка достаточно для «создать файл / перечислить / удалить».
 *
 * Сеть здесь невозможна в принципе (I-24): это операции над локальным `content://`-провайдером.
 */
class SafBackupFolder(private val context: Context, private val treeUri: Uri) : BackupFolderSource {

    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    /**
     * Создаёт новый файл бэкапа и отдаёт writer через него. A1.13: через временный `.part` и переименование
     * ([writeBackupAtomically]) — сбой не оставляет файл с финальным именем.
     */
    suspend fun writeNewBackup(now: Date, write: suspend (java.io.OutputStream) -> Unit) {
        val name = "$AUTO_BACKUP_PREFIX${stamp.format(now)}$AUTO_BACKUP_SUFFIX"
        writeBackupAtomically(SafDocumentStore(), name, AUTO_BACKUP_PREFIX, write)
    }

    private inner class SafDocumentStore : BackupDocumentStore {
        private val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        private val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

        private fun docUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

        override fun create(displayName: String): String {
            // octet-stream: провайдер не дописывает к «….json.part» расширение по MIME.
            val uri = DocumentsContract.createDocument(context.contentResolver, dirUri, "application/octet-stream", displayName)
                ?: error(context.getString(R.string.safbackup_error_create_file))
            return DocumentsContract.getDocumentId(uri)
        }

        override fun openOutput(documentId: String): java.io.OutputStream =
            context.contentResolver.openOutputStream(docUri(documentId))
                ?: error(context.getString(R.string.safbackup_error_open_for_write))

        override fun rename(documentId: String, newName: String): Boolean =
            runCatching { DocumentsContract.renameDocument(context.contentResolver, docUri(documentId), newName) != null }
                .getOrDefault(false)

        override fun delete(documentId: String) {
            DocumentsContract.deleteDocument(context.contentResolver, docUri(documentId))
        }

        override fun list(): List<Pair<String, String>> {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            val out = mutableListOf<Pair<String, String>>()
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) out += c.getString(0) to (c.getString(1) ?: continue)
            }
            return out
        }
    }

    /**
     * Перечисляет файлы автобэкапа в папке (R1.3), от новых к старым. `DocumentsContract`
     * напрямую — как и везде в этом классе, без `androidx.documentfile` (не в списке
     * разрешённых зависимостей, BUILD_PLAN.md §1.3).
     */
    override fun list(): List<SafBackupFile> {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val result = mutableListOf<SafBackupFile>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val displayName = c.getString(1) ?: continue
                if (displayName.startsWith(AUTO_BACKUP_PREFIX) && displayName.endsWith(AUTO_BACKUP_SUFFIX)) {
                    result += SafBackupFile(docId, displayName, c.getLong(2), c.getLong(3))
                }
            }
        }
        return result.sortedByDescending { it.lastModified }
    }

    /** Открывает файл из этой папки на чтение (R1.3/R1.4) — для восстановления и проверки. */
    override fun openInputStream(documentId: String): InputStream {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return context.contentResolver.openInputStream(uri) ?: error(context.getString(R.string.safbackup_error_open_for_read))
    }

    /** Удаляет самые старые файлы автобэкапа, оставляя [keep] последних. */
    fun prune(keep: Int) {
        val files = list()
        val byName = files.associate { it.name to it.documentId }
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
