package com.raiwesy.ai.core.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast
import com.raiwesy.ai.R

/**
 * Global crash guard (crash prevention).
 *
 * Installed in [com.raiwesy.ai.RaiwesyApp.onCreate]. When an uncaught exception
 * escapes the app:
 *  1. the exception is logged,
 *  2. a crash marker is saved (a friendly dialog is shown on next launch),
 *  3. the user is notified with a toast,
 *  4. the app is restarted with a clean task,
 *  5. the crashing process is terminated.
 *
 * A re-entrancy guard prevents an infinite restart loop.
 */
class CrashHandler(
    private val context: Context,
    private val apiKeys: ApiKeyManager,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    @Volatile
    private var isHandling = false

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (isHandling) {
            // Do not loop: hand over to the previous/default handler.
            fallback(thread, throwable)
            return
        }
        isHandling = true
        try {
            Log.e(TAG, "Yakalanamayan istisna (${thread.name})", throwable)
            apiKeys.markCrashed()

            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                runCatching {
                    Toast.makeText(
                        context.applicationContext,
                        context.getString(R.string.crash_toast),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Give the main thread a chance to render the toast.
            Thread.sleep(800)
            restartApp()
        } catch (t: Throwable) {
            Log.e(TAG, "CrashHandler başarısız oldu", t)
            fallback(thread, throwable)
        }
    }

    private fun restartApp() {
        try {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
            launchIntent?.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                Log.e(TAG, "Launch intent üretilemedi")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Uygulama yeniden başlatılamadı", t)
        } finally {
            // End the crashing process; the new task starts from a clean state.
            Process.killProcess(Process.myPid())
            System.exit(10)
        }
    }

    private fun fallback(thread: Thread, throwable: Throwable) {
        try {
            previous?.uncaughtException(thread, throwable)
        } catch (t: Throwable) {
            Log.e(TAG, "Fallback handler başarısız", t)
        }
        Process.killProcess(Process.myPid())
        System.exit(1)
    }

    private companion object {
        const val TAG = "CrashHandler"
    }
}
