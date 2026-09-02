package com.corriente.app.ui.settings

import com.corriente.data.backup.BackupIo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class GatedBackup : BackupIo {
        val gate = CompletableDeferred<Unit>()
        override suspend fun export(output: OutputStream) {
            gate.await()
        }
        override suspend fun restore(input: InputStream, beforeReplace: suspend () -> Unit) {
            gate.await()
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    // F2.7 — экран должен видеть занятость: busy false → true (во время экспорта) → false.
    @Test
    fun `busy toggles around an export`() = runTest(dispatcher) {
        val backup = GatedBackup()
        val vm = BackupViewModel(backup, io = dispatcher)
        backgroundScope.launch { vm.busy.collect {} }
        backgroundScope.launch { vm.result.collect {} }

        assertFalse(vm.busy.value)
        vm.export(ByteArrayOutputStream())
        advanceUntilIdle()
        assertTrue("во время экспорта busy=true", vm.busy.value)

        backup.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse("после экспорта busy=false", vm.busy.value)
        assertEquals(BackupResult.Exported, vm.result.value)
    }
}
