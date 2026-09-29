package com.corriente.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

/** A1.3: потеря доступа к папке — состояние экрана, а не исключение. */
class BackupFolderSourceTest {

    private class FakeSource(private val onList: () -> List<SafBackupFile>) : BackupFolderSource {
        override fun list() = onList()
        override fun openInputStream(documentId: String): InputStream = error("not used")
    }

    @Test
    fun `a listing that throws becomes FolderUnavailable`() {
        listOf<() -> List<SafBackupFile>>(
            { throw SecurityException("permission revoked") },
            { throw IllegalArgumentException("Failed to determine if X is child of Y") },
            { throw java.io.FileNotFoundException("gone") },
        ).forEach { failure ->
            assertEquals(BackupFilesResult.FolderUnavailable, loadBackupFiles(FakeSource(failure)))
        }
    }

    @Test
    fun `a working folder returns its files`() {
        val file = SafBackupFile("id", "corriente-backup-1.json", 1L, 10L)
        val result = loadBackupFiles(FakeSource { listOf(file) })
        assertTrue(result is BackupFilesResult.Files)
        assertEquals(listOf(file), (result as BackupFilesResult.Files).files)
    }
}
