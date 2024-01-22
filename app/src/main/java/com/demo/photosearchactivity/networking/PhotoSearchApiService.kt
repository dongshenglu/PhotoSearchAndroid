package com.demo.photosearchactivity.networking

import com.demo.photosearchactivity.model.GetLocationResponse
import com.demo.photosearchactivity.model.PhotosSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

const val FLICKR_API_KEY = "fake-api-key"

interface PhotoSearchApiService {
    @GET("?method=flickr.photos.search&format=json&nojsoncallback=1&api_key=$FLICKR_API_KEY")
    suspend fun fetchImages(
        @Query(value = "text") searchTerm: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
        @Query("has_geo") hasGeo: Int = 1
    ): PhotosSearchResponse

    @GET("?method=flickr.photos.geo.getLocation&format=json&nojsoncallback=1&api_key=$FLICKR_API_KEY")
    suspend fun fetchLocation(@Query(value = "photo_id") photoId: String): GetLocationResponse?
}