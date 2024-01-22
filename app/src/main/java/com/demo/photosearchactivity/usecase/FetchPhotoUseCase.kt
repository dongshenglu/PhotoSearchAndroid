package com.demo.photosearchactivity.usecase

import android.util.Log
import com.demo.photosearchactivity.PhotosRepository
import com.demo.photosearchactivity.model.PhotoData
import com.demo.photosearchactivity.model.PhotoResponse
import com.demo.photosearchactivity.model.PhotosMetaData
import com.demo.photosearchactivity.networking.WebClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val TAG = "PhotoUseCase"
private const val PHOTO_PER_PAGE = 100

/**
 * Fetch photo use case class.
 */
class FetchPhotoUseCase(private val photosRepository: PhotosRepository) {

    /**
     * Fetch a list of photos based on input search keyword.
     *
     * @param query A input search keyword.
     * @param page The page number.
     * @return A PhotoMetaData object.
     */
    suspend fun fetchPhotos(query: String, page: Int): PhotosMetaData {
        return WebClient.client.fetchImages(query, PHOTO_PER_PAGE, page).photos
    }

    /**
     * Fetch a photo data(location, and image) based on PhotoResponse.
     *
     * @param photo A object of PhotoResponse.
     * @return An MapPhoto object.
     */
    suspend fun fetchPhoto(photo: PhotoResponse): PhotoData? = coroutineScope {
        val locationDeferred = async { photosRepository.fetchLocation(photo.id) }
        val imageDeferred = async {
            photosRepository.fetchImage(photo)
        }

        try {
            val location = try {
                locationDeferred.await()
            } catch (e: Exception) {
                Log.d(TAG, "====Fetch location exception: ${e.message}")
                imageDeferred.cancel() // Cancel image fetching if location fetching fails
                throw e
            }

            val bitmap = try {
                imageDeferred.await()
            } catch (e: Exception) {
                Log.d(TAG, "====Fetch image exception: ${e.message}")
                locationDeferred.cancel() // Cancel image fetching if location fetching fails
                throw e
            }

            if (location != null && bitmap != null) {
                PhotoData(
                    photo.id,
                    "",
                    location.latitude.toDouble(),
                    location.longitude.toDouble(),
                    bitmap
                )
            } else null

        } catch (e: Exception) {
            Log.d(TAG, e.message.toString())
            null
        }
    }
}