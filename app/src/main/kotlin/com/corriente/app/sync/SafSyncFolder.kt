package com.corriente.app.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.corriente.data.sync.SyncFolder

/**
 * Общая папка синхронизации (docs/SYNC_DESIGN.md §3) через SAF-дерево, выбранное пользователем, — обычно папка, которую
 * Syncthing держит одинаковой на всех устройствах. `DocumentsContract` напрямую, как в `SafBackupFolder` (без
 * `androidx.documentfile`). Сети здесь нет (I-24): только локальный `content://`-провайдер.
 *
 * Идентификаторы документов по пути кэшируются на время жизни объекта — движок создаёт новый объект на каждый обмен.
 */
class SafSyncFolder(private val context: Context, private val treeUri: Uri) : SyncFolder {

    private val resolver get() = context.contentResolver
    private val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    private val ids = HashMap<String, String>().apply { put("", rootId) }

    private fun docUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    private data class Child(val id: String, val name: String)

    private fun children(parentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val out = mutableListOf<Child>()
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) out += Child(c.getString(0), c.getString(1) ?: continue) }
        return out
    }

    private fun split(path: String): Pair<String, String> {
        val i = path.lastIndexOf('/')
        return if (i < 0) "" to path else path.substring(0, i) to path.substring(i + 1)
    }

    private fun find(path: String): String? {
        ids[path]?.let { return it }
        val (parent, name) = split(path)
        val parentId = find(parent) ?: return null
        return children(parentId).firstOrNull { it.name == name }?.id?.also { ids[path] = it }
    }

    private fun ensureDir(path: String): String {
        find(path)?.let { return it }
        val (parent, name) = split(path)
        val created = DocumentsContract.createDocument(resolver, docUri(ensureDir(parent)), DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: error("cannot create folder $path")
        return DocumentsContract.getDocumentId(created).also { ids[path] = it }
    }

    /** Нет папки или это файл — пустой список (провайдер на файле бросает исключение). */
    override fun list(dir: String): List<String> =
        runCatching { find(dir)?.let { children(it).map(Child::name) } }.getOrNull().orEmpty()

    override fun read(path: String): ByteArray? {
        val id = find(path) ?: return null
        return resolver.openInputStream(docUri(id))?.use { it.readBytes() }
    }

    /**
     * Атомарно: пишем во временный документ и переименовываем. Провайдер без переименования — пишем поверх целевого
     * (недописанный файл движок пропустит до следующего обмена: битый JSON не применяется).
     */
    override fun write(path: String, bytes: ByteArray) {
        val (parent, name) = split(path)
        val dirId = ensureDir(parent)
        val tmpName = ".$name.tmp"
        children(dirId).filter { it.name == tmpName }.forEach { runCatching { DocumentsContract.deleteDocument(resolver, docUri(it.id)) } }
        // octet-stream: провайдер не дописывает расширение по MIME.
        val tmp = DocumentsContract.createDocument(resolver, docUri(dirId), "application/octet-stream", tmpName)
            ?: error("cannot create $path")
        resolver.openOutputStream(tmp, "wt")?.use { it.write(bytes) } ?: error("cannot write $path")

        val existing = find(path)
        if (existing != null) {
            runCatching { DocumentsContract.deleteDocument(resolver, docUri(existing)) }
            ids.remove(path)
        }
        val renamed = runCatching { DocumentsContract.renameDocument(resolver, tmp, name) }.getOrNull()
        if (renamed != null) {
            ids[path] = DocumentsContract.getDocumentId(renamed)
        } else {
            DocumentsContract.deleteDocument(resolver, tmp)
            val target = DocumentsContract.createDocument(resolver, docUri(dirId), "application/octet-stream", name)
                ?: error("cannot create $path")
            resolver.openOutputStream(target, "wt")?.use { it.write(bytes) } ?: error("cannot write $path")
            ids[path] = DocumentsContract.getDocumentId(target)
        }
    }

    override fun delete(path: String) {
        val id = find(path) ?: return
        runCatching { DocumentsContract.deleteDocument(resolver, docUri(id)) }
        ids.remove(path)
    }
}
