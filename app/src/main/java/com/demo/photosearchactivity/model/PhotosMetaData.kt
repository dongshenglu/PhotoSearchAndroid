package com.demo.photosearchactivity.model

data class PhotosMetaData(
    val page: Int,
    val pages: Int,
    val perpage: Int,
    val total: String,
    val photo: List<PhotoResponse>
)
