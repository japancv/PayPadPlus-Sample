package org.japancv.sample.ui.main

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.japancv.sample.data.fr.FaceRecognitionApi
import org.japancv.sample.data.fr.faceRecognitionApi

class FaceRecognitionViewModel(
    private val repo: FaceRecognitionApi = faceRecognitionApi()
): ViewModel() {

    private val _resultSharedFlow = MutableSharedFlow<Result<*>>()
    val resultSharedFlow = _resultSharedFlow.asSharedFlow()

    /**
     * Register the face on server-side which can be searched with 1:N Face Recognition
     */
    fun registerFace(faceBitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            _resultSharedFlow.emit(repo.create(faceBitmap))
        }
    }


    /**
     * Search the face with 1:N Face Recognition
     */
    fun searchFace(faceBitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repo.search(faceBitmap)
            if (result.isFailure) registerFace(faceBitmap) else _resultSharedFlow.emit(result)
        }
    }
}