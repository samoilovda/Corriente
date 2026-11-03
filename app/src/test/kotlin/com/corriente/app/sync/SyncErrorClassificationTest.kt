package com.corriente.app.sync

import com.corriente.data.sync.SyncFolderUnavailableException
import com.corriente.data.sync.SyncIntegrityException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** W10: «папка недоступна» — только ошибки доступа к папке, а не любые `require` внутри синхронизации. */
class SyncErrorClassificationTest {

    @Test
    fun `folder access problems are folder unavailable`() {
        assertEquals(SyncError.FolderUnavailable, classifySyncError(SyncFolderUnavailableException(IllegalArgumentException("bad uri"))))
        assertEquals(SyncError.FolderUnavailable, classifySyncError(SecurityException("no grant")))
        assertEquals(SyncError.FolderUnavailable, classifySyncError(java.io.FileNotFoundException("gone")))
    }

    @Test
    fun `engine checks are not reported as an unavailable folder`() {
        val e = classifySyncError(IllegalArgumentException("Txn x: EXPENSE cannot use a INCOME category"))
        assertTrue(e is SyncError.Other)
    }

    @Test
    fun `integrity failure keeps its own error`() {
        assertEquals(SyncError.Integrity, classifySyncError(SyncIntegrityException(emptyList())))
    }
}
