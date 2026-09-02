package com.corriente.app.backup

import android.content.Context
import java.io.File

/**
 * Временная папка для «Отправить бэкап» (R1.2): `cacheDir/share/`. Файлы сюда пишет
 * [com.corriente.app.ui.settings.SettingsScreen] перед вызовом `ACTION_SEND`, наружу их
 * отдаёт `FileProvider` (см. `res/xml/file_paths.xml`) — путь ограничен ровно этой директорией.
 *
 * Файлы старше [MAX_AGE_MS] чистятся при следующем запуске приложения ([cleanOldFiles] зовётся
 * из `CorrienteApplication.onCreate`), а не сразу после отправки: пользователь мог ещё не
 * успел передать файл принимающему приложению (шеринг асинхронный, каждое приложение читает
 * URI в своё время).
 */
object ShareBackupCache {
    private const val DIR_NAME = "share"
    const val MAX_AGE_MS: Long = 24 * 60 * 60 * 1000L

    fun dir(context: Context): File = File(context.cacheDir, DIR_NAME)

    /**
     * Чистая функция (тестируется без Context/Android): удаляет из [dir] файлы, чей
     * `lastModified` старше [maxAgeMs] относительно [now]. Не падает, если [dir] не существует
     * или это не директория — до первого экспорта её просто нет.
     */
    fun cleanOldFiles(dir: File, now: Long = System.currentTimeMillis(), maxAgeMs: Long = MAX_AGE_MS) {
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) file.delete()
        }
    }
}
