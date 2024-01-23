package com.demo.photosearchactivity.networking


import android.util.Log
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "photoSearch-WebClient"

private const val BASE_URL = "https://api.flickr.com/services/rest/"
private const val PHOTO_BASE_URL = "https://live.staticflickr.com"

private const val CONNECTION_TIMEOUT: Long = 30
private const val READ_TIMEOUT: Long = 30
private const val WRITE_TIMEOUT: Long = 30

object WebClient {

    // The HTTP response cache set in OkHttpClient, also used in Retrofit client.
    private const val cacheSize = 10 * 1024 * 1024 // 10 MB
    private var cache: Cache? = null

    class ResponseSourceInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url
            val response = chain.proceed(request)
            val source = when {
                response.cacheResponse != null -> "Cache"
                response.networkResponse != null -> "Network"
                else -> "Unknown"
            }
            Log.d(TAG, "Request URL: $url")
            Log.d(TAG, "Response Source: $source")
            return response
        }
    }

    fun init(cacheDirectory: File) {
        cache = Cache(cacheDirectory, cacheSize.toLong())
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(
                CONNECTION_TIMEOUT,
                TimeUnit.SECONDS
            )
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)    // For reading data from the server
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)   // For writing data to the server
            .addInterceptor(ResponseSourceInterceptor()) // Add the custom interceptor
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    val client: PhotoSearchApiService by lazy {
        Retrofit.Builder().baseUrl(BASE_URL)
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder()
                        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                        .create()
                )
            )
            .client(okHttpClient)
            .build()
            .create(PhotoSearchApiService::class.java)
    }

    val imageRetrofit: ImageService by lazy {
        Retrofit.Builder()
            .baseUrl(PHOTO_BASE_URL) // Use the base URL for images
            .client(okHttpClient) // You can use the same OkHttpClient
            .build()
            .create(ImageService::class.java)
    }


    fun clearCache() {
        cache?.evictAll()
    }

}
