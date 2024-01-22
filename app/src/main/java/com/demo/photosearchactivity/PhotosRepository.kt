package com.demo.photosearchactivity

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import com.demo.photosearchactivity.model.LocationResponse
import com.demo.photosearchactivity.model.PhotoResponse
import com.demo.photosearchactivity.networking.WebClient
import com.demo.photosearchactivity.networking.WebClient.imageRetrofit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

private const val TAG = "photoMap-Repo"
private const val DEFAULT_SIZE_SUFFIX: String = "b"
private const val PHOTO_BASE_URL = "https://live.staticflickr.com"

/**
 * Repository class manages data operation from network and cache.
 */
class PhotosRepository(private val context: Context) {

    // Maximum memory in KB.
    private val maxMemory = Runtime.getRuntime().maxMemory() / 1024

    // Image cache.
    private val imageCacheSizePercentage = 0.2
    private val imageCacheSize = (maxMemory * imageCacheSizePercentage).toInt()
    private val imageCache: LruCache<String, Bitmap> = LruCache(imageCacheSize)
    private val imageCacheMutex = Mutex()

    // Location data cache.
    private val locationCacheSizePercentage = 0.1
    private val locationCacheSize = (maxMemory * locationCacheSizePercentage).toInt()
    private val locationCache: LruCache<String, LocationResponse> = LruCache(locationCacheSize)
    private val locationCacheMutex = Mutex()

    /**
     * Fetch location data from server.
     *
     * @param photoId Photo ID.
     */
    suspend fun fetchLocation(photoId: String): LocationResponse? {
        // First check from memory cache.
        getCachedLocation(photoId)?.let { return it }

        // Update memory cache with network response.
        val location = WebClient.client.fetchLocation(photoId)?.photo?.location
        location?.let {
            putCachedLocation(photoId, location)
        }
        return location
    }

    /**
     * Fetch image from server.
     *
     * @param photo PhoneResponse.
     */
    suspend fun fetchImage(photo: PhotoResponse): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val url =
                "$PHOTO_BASE_URL/${photo.server}/${photo.id}_${photo.secret}_$DEFAULT_SIZE_SUFFIX.jpg"

            // Check cache first
            getCachedImage(url)?.let { return@withContext it }

            // Fetch image using Retrofit
            val response = imageRetrofit.fetchImage(url)
            if (!response.isSuccessful) return@withContext null

            response.body()?.use { responseBody ->
                val bytes = responseBody.bytes()

                withContext(Dispatchers.Default) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { bitmap ->
                        putCachedImage(url, bitmap)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Fetch image exception: ${e.message}")
            null
        }
    }

    /**
     * Save image in external storage.
     *
     * @param bitmap Bitmap reference.
     * @param fileName File name string.
     */
    fun saveImage(bitmap: Bitmap, fileName: String): Uri? {
        val filename = "$fileName.jpg"
        val fos: OutputStream?
        val imageUri: Uri?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val resolver = context.contentResolver
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = imageUri?.let { resolver.openOutputStream(it) }
        } else {
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            imageUri = Uri.fromFile(image)
            fos = FileOutputStream(image)
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }

        return imageUri
    }

    /**
     * Clear cached data.
     */
    fun clearPhotoCache() {
        // Clear memory cache.
        locationCache.evictAll()
        imageCache.evictAll()

        // Clear OkHttpClient disk cache.
        WebClient.clearCache()
    }

    private suspend fun getCachedImage(url: String): Bitmap? {
        return imageCacheMutex.withLock {
            imageCache.get(url)
        }
    }

    private suspend fun putCachedImage(url: String, bitmap: Bitmap) {
        imageCacheMutex.withLock {
            imageCache.put(url, bitmap)
        }
    }

    private suspend fun getCachedLocation(photoId: String): LocationResponse? {
        return locationCacheMutex.withLock {
            locationCache.get(photoId)
        }
    }

    private suspend fun putCachedLocation(photoId: String, locationResponse: LocationResponse) {
        locationCacheMutex.withLock {
            locationCache.put(photoId, locationResponse)
        }
    }
}