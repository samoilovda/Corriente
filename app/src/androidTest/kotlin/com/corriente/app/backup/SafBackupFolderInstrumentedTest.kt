package com.corriente.app.backup

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Date

/**
 * [БД] R1.3: перечисление и чтение файлов из настоящего SAF-дерева.
 *
 * SAF выдаёт доступ к дереву только через интерактивный системный пикер
 * (`ACTION_OPEN_DOCUMENT_TREE`) — это нельзя ни сконструировать в коде, ни автоматизировать
 * без `UiAutomator` (а тащить его ради одного теста не оправдано, BUILD_PLAN.md §1.3: список
 * зависимостей закрытый). Поэтому тест использует уже выданное дереву разрешение — на реальном
 * устройстве нужно один раз выбрать любую папку через экран «Автобэкап» приложения (или через
 * `AutoBackupViewModel.setFolder`) до прогона; `persistedUriPermissions` тогда не пуст.
 * Без такого разрешения тест **пропускается** (`assumeTrue`), а не падает.
 */
class SafBackupFolderInstrumentedTest {

    @Test
    fun listsAndReadsBackupFilesFromGrantedTree() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val granted = context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.isWritePermission }
        assumeTrue(
            "нет заранее выданного SAF-дерева — выберите папку в приложении перед прогоном теста",
            granted != null,
        )
        val treeUri = granted!!.uri
        val folder = SafBackupFolder(context, treeUri)

        val before = folder.list().map { it.documentId }.toSet()
        val content = """{"schemaVersion":1,"marker":"safbackupfoldertest"}"""
        folder.writeNewBackup(Date()) { it.write(content.toByteArray(Charsets.UTF_8)) }

        val after = folder.list()
        val created = after.firstOrNull { it.documentId !in before }
        assertTrue("после записи должен появиться новый файл", created != null)
        assertEquals(content.length.toLong(), created!!.size)

        val readBack = folder.openInputStream(created.documentId).use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals(content, readBack)
    }
}
