package com.corriente.app.applock

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/**
 * A2.9: при включённом замке содержимое не должно попадать в миниатюру «Недавних» (её снимают до `onStop`,
 * когда контент ещё на экране). API 33+ — `setRecentsScreenshotEnabled(false)`: пользовательские скриншоты
 * остаются возможными. API 26–32 такого переключателя нет — `FLAG_SECURE` (запрещает и обычные скриншоты; это
 * осознанный компромисс на старых версиях, решение пользователя).
 */
object RecentsProtection {

    fun apply(activity: Activity, locked: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!locked)
        } else if (locked) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
