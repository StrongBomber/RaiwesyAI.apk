package com.raiwesy.ai

import android.app.Application
import com.raiwesy.ai.core.util.CrashHandler
import com.raiwesy.ai.di.AppContainer

/**
 * Application entry point.
 *
 *  - builds the [AppContainer] (dependency container),
 *  - installs the global [CrashHandler] (crash prevention).
 */
class RaiwesyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(this, container.keyManager, previousHandler)
        )
    }
}
