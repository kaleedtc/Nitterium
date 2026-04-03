package com.kaleedtc.nitterium

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebSettings
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.kaleedtc.nitterium.data.ConnectivityMonitor
import com.kaleedtc.nitterium.data.ConnectivityMonitorImpl
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import java.net.URL

class NitteriumApplication : Application(), SingletonImageLoader.Factory {

    lateinit var subscriptionRepository: SubscriptionRepository
    lateinit var userPreferencesRepository: UserPreferencesRepository
    lateinit var connectivityMonitor: ConnectivityMonitor

    override fun onCreate() {
        super.onCreate()
        subscriptionRepository = SubscriptionRepository(this)
        userPreferencesRepository = UserPreferencesRepository(this)
        connectivityMonitor = ConnectivityMonitorImpl(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cookieManager = CookieManager.getInstance()
        val defaultUserAgent = WebSettings.getDefaultUserAgent(context)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val urlString = originalRequest.url.toString()
                val cookies = cookieManager.getCookie(urlString)
                
                val host = try {
                    val url = URL(urlString)
                    "${url.protocol}://${url.host}/"
                } catch (_: Exception) {
                    ""
                }
                
                val requestBuilder = originalRequest.newBuilder()
                    .header("User-Agent", defaultUserAgent)
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    
                if (host.isNotEmpty()) {
                    requestBuilder.header("Referer", host)
                }
                    
                if (!cookies.isNullOrEmpty()) {
                    requestBuilder.header("Cookie", cookies)
                }
                
                chain.proceed(requestBuilder.build())
            }
            .build()

        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizePercent(0.02)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .build()
    }
}
