package com.corriente.app.backup

import java.io.OutputStream

/** Суффикс временного файла автобэкапа (A1.13): не совпадает с фильтром списка/ротации (`*.json`). */
const val BACKUP_PART_SUFFIX = ".part"

/**
 * Что нужно записи бэкапа от папки (A1.13). Реализация — SAF ([SafBackupFolder]); в тестах — фейк, поэтому
 * порядок «временный файл → запись → переименование» проверяется без `ContentResolver`.
 */
interface BackupDocumentStore {
    /** Создаёт пустой документ с именем [displayName], возвращает его идентификатор. */
    fun create(displayName: String): String

    fun openOutput(documentId: String): OutputStream

    /** Переименовывает; возвращает `false`, если провайдер не умеет (тогда вызывающий откатывается на запись напрямую). */
    fun rename(documentId: String, newName: String): Boolean

    fun delete(documentId: String)

    /** Имена и идентификаторы документов папки. */
    fun list(): List<Pair<String, String>>
}

/**
 * A1.13: запись бэкапа без «полуфайлов» с финальным именем. Пишем в `<имя>.part`, после успеха
 * переименовываем в [finalName]; при исключении удаляем временный файл. Файл с финальным именем никогда не
 * бывает нулевым или обрезанным, а сбой не занимает слот ротации. Старые `.part` (процесс убит посреди
 * записи) чистятся при следующей записи.
 */
suspend fun writeBackupAtomically(
    store: BackupDocumentStore,
    finalName: String,
    prefix: String,
    write: suspend (OutputStream) -> Unit,
) {
    store.list().filter { (_, name) -> name.startsWith(prefix) && name.endsWith(BACKUP_PART_SUFFIX) }
        .forEach { (id, _) -> runCatching { store.delete(id) } }

    val tempId = store.create(finalName + BACKUP_PART_SUFFIX)
    try {
        store.openOutput(tempId).use { write(it) }
    } catch (e: Throwable) {
        runCatching { store.delete(tempId) }
        throw e
    }
    if (!store.rename(tempId, finalName)) {
        // Провайдер не поддерживает переименование: пишем напрямую, но всё равно не оставляем обрезок.
        runCatching { store.delete(tempId) }
        val directId = store.create(finalName)
        try {
            store.openOutput(directId).use { write(it) }
        } catch (e: Throwable) {
            runCatching { store.delete(directId) }
            throw e
        }
    }
}
