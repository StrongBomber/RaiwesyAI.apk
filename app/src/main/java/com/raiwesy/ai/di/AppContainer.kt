package com.raiwesy.ai.di

import android.content.Context
import android.util.Log
import com.raiwesy.ai.BuildConfig
import com.raiwesy.ai.core.network.NvidiaApi
import com.raiwesy.ai.core.network.NetworkMonitor
import com.raiwesy.ai.core.util.ApiKeyManager
import com.raiwesy.ai.core.util.MessageStore
import com.raiwesy.ai.data.ChatRepository
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Minimal manual dependency container (constructor injection).
 * Created once in [com.raiwesy.ai.RaiwesyApp] and injected into
 * [com.raiwesy.ai.ui.ChatViewModel] - the heart of the MVVM wiring.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val keyManager = ApiKeyManager(appContext)
    val messageStore = MessageStore(appContext)
    val networkMonitor = NetworkMonitor(appContext)

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message ->
            if (Log.isLoggable("RaiwesyHttp", Log.VERBOSE)) {
                Log.v("RaiwesyHttp", message)
            }
        }
        // REDACTED: Authorization header (API key) is never logged.
        // NONE in release builds for zero overhead and zero leakage.
        logging.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.REDACTED
        } else {
            HttpLoggingInterceptor.Level.NONE
        }

        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            // Connection pool: keep-alive reuse for fast repeat requests
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            // API key injection: the key (user-provided or built-in) is read at
            // request time and sent ONLY as the Authorization header.
            .addInterceptor { chain ->
                val original = chain.request()
                val authorized = original.newBuilder()
                    .header("Authorization", "Bearer ${keyManager.effectiveKey().orEmpty()}")
                    .build()
                chain.proceed(authorized)
            }
            .addInterceptor(logging)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL) // must end with '/'
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val nvidiaApi: NvidiaApi by lazy {
        retrofit.create(NvidiaApi::class.java)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(nvidiaApi, keyManager)
    }
}
