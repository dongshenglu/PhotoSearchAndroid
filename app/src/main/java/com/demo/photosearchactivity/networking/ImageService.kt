package com.demo.photosearchactivity.networking

import retrofit2.Response
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

interface ImageService {
    @GET
    suspend fun fetchImage(@Url imageUrl: String): Response<ResponseBody>
}
