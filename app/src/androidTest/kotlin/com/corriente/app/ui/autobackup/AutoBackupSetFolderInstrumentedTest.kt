package com.corriente.app.ui.autobackup

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.app.CorrienteApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A3.13: папка, на которую не удалось получить постоянное разрешение, не сохраняется и автобэкап не включается. */
@RunWith(AndroidJUnit4::class)
class AutoBackupSetFolderInstrumentedTest {

    @Test
    fun aFolderWithoutAPersistablePermissionIsNotSaved() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as CorrienteApplication
        val settings = app.container.autoBackupSettings
        runBlocking { settings.setEnabled(false) }
        val before = runBlocking { settings.current().treeUri }

        lateinit var vm: AutoBackupViewModel
        instrumentation.runOnMainSync { vm = AutoBackupViewModel(app as Application, settings) }
        instrumentation.runOnMainSync { vm.setFolder(Uri.parse("content://com.corriente.nonexistent/tree/none")) }
        instrumentation.waitForIdleSync()

        assertTrue(vm.folderError.value)
        val after = runBlocking { settings.current() }
        assertEquals(before, after.treeUri)
        assertEquals(false, after.enabled)
    }
}
