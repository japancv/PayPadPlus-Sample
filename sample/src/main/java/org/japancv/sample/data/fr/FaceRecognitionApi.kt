package org.japancv.sample.data.fr

import android.graphics.Bitmap

interface FaceRecognitionApi {
    suspend fun create(bitmap: Bitmap, name: String? = null): Result<String>
    suspend fun search(bitmap: Bitmap): Result<List<String>>
}