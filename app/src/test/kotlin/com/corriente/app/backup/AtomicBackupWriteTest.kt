package com.corriente.app.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** A1.13: финальное имя появляется только после успешной записи. */
class AtomicBackupWriteTest {

    private class FakeStore(private val renameSupported: Boolean = true) : BackupDocumentStore {
        val names = linkedMapOf<String, String>()
        val contents = mutableMapOf<String, ByteArrayOutputStream>()
        private var next = 0

        override fun create(displayName: String): String {
            val id = "d${next++}"
            names[id] = displayName
            contents[id] = ByteArrayOutputStream()
            return id
        }

        override fun openOutput(documentId: String): OutputStream = contents.getValue(documentId)

        override fun rename(documentId: String, newName: String): Boolean {
            if (!renameSupported) return false
            names[documentId] = newName
            return true
        }

        override fun delete(documentId: String) {
            names.remove(documentId)
            contents.remove(documentId)
        }

        override fun list() = names.map { (id, n) -> id to n }
    }

    private val prefix = "corriente-backup-"
    private val finalName = "corriente-backup-20260929-120000.json"

    @Test
    fun `a successful write leaves only the final file`() = runTest {
        val store = FakeStore()
        writeBackupAtomically(store, finalName, prefix) { it.write("{}".toByteArray()) }

        assertEquals(listOf(finalName), store.names.values.toList())
        assertEquals("{}", store.contents.getValue(store.names.keys.single()).toString())
    }

    @Test
    fun `an exception during write leaves neither a final file nor a part file`() = runTest {
        val store = FakeStore()
        val failure = runCatching {
            writeBackupAtomically(store, finalName, prefix) { it.write("{".toByteArray()); error("disk full") }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(store.names.isEmpty())
    }

    @Test
    fun `stale part files from a killed process are removed on the next write`() = runTest {
        val store = FakeStore()
        store.create(finalName + BACKUP_PART_SUFFIX)
        store.create("unrelated.txt.part")

        writeBackupAtomically(store, finalName, prefix) { it.write("{}".toByteArray()) }

        assertEquals(setOf(finalName, "unrelated.txt.part"), store.names.values.toSet())
    }

    @Test
    fun `a provider without rename support still never keeps a broken final file`() = runTest {
        val store = FakeStore(renameSupported = false)
        writeBackupAtomically(store, finalName, prefix) { it.write("{}".toByteArray()) }
        assertEquals(listOf(finalName), store.names.values.toList())

        val failing = FakeStore(renameSupported = false)
        runCatching {
            var first = true
            writeBackupAtomically(failing, finalName, prefix) {
                // первая запись (во временный файл) проходит, прямая запись — падает
                if (first) first = false else error("boom")
                it.write("{}".toByteArray())
            }
        }
        assertFalse(failing.names.values.any { it == finalName })
    }
}
