package com.corriente.app.ui.settings

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.app.CorrienteApplication
import com.corriente.app.MainActivity
import com.corriente.app.R
import com.corriente.app.backup.SafBackupFolder
import com.corriente.app.clearMutableTables
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * R5.1: восстановление из бэкапа требует подтверждения (AlertDialog «Заменить все данные?»)
 * до того, как [BackupViewModel.restore] реально вызывается. Падает, если кто-то уберёт диалог
 * подтверждения и позовёт `restore` прямо по тапу на «Восстановить» — тогда узел диалога не
 * появится вовсе, а `assertExists()` ниже уронит тест.
 *
 * `[БД]`: как и [com.corriente.app.backup.SafBackupFolderInstrumentedTest], использует уже
 * выданное SAF-дерево (интерактивный пикер `ACTION_OPEN_DOCUMENT_TREE` нельзя сконструировать
 * в коде без `UiAutomator`, которого нет в списке разрешённых зависимостей) — на реальном
 * устройстве нужно один раз выбрать папку через экран «Автобэкап» до прогона. Без такого
 * разрешения тест пропускается, а не падает.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreConfirmationInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val granted = context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.isWritePermission }
        assumeTrue(
            "нет заранее выданного SAF-дерева — выберите папку в приложении перед прогоном теста",
            granted != null,
        )
        val treeUri = granted!!.uri
        val container = (composeRule.activity.applicationContext as CorrienteApplication).container
        runBlocking {
            container.database.clearMutableTables()
            container.autoBackupSettings.setTreeUri(treeUri.toString())
            // Валидный бэкап текущей (только что зачищенной) БД — достаточно для восстановления.
            SafBackupFolder(context, treeUri).writeNewBackup(Date()) { out -> container.backupRepository.export(out) }
        }
        composeRule.waitForIdle()
    }

    private fun str(resId: Int) = composeRule.activity.getString(resId)

    @Test
    fun restoringABackupShowsAConfirmationBeforeReplacingData() {
        composeRule.onNodeWithText(str(R.string.nav_settings)).performClick()
        composeRule.onNodeWithText(str(R.string.autobackup_title)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.backup_restore_action)).performClick()
        // Подтверждение обязано появиться прежде, чем данные будут заменены.
        composeRule.onNodeWithText(str(R.string.backup_import_confirm_title)).assertExists()

        composeRule.onNodeWithText(str(R.string.backup_import_confirm_button)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.backup_result_imported)).assertExists()
    }
}
