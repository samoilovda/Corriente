package com.corriente.app

import android.app.Application
import com.corriente.app.widget.WidgetUpdater

class CorrienteApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WidgetUpdater(this, container).start()
    }
}
