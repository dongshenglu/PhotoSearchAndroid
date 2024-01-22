package com.demo.photosearchactivity.model

import android.graphics.Bitmap
import androidx.annotation.DrawableRes

/**
 * A data class for photo image and location.
 */
data class PhotoData(val id: String, val title: String, val latitude: Double,
                     val longitude: Double, val bitmap: Bitmap)
