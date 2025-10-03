package com.example.circle2capture.llm

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LlmViewModel(
    private val appContext: Context,
    private val modelPath: String,
) : ViewModel() {

    var preparing by mutableStateOf(false)
        private set
    var inProgress by mutableStateOf(false)
        private set
    var response by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var instance: LlmModelInstance? = null

    init { initialize() }

    fun initialize() {
        viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) { preparing = true }
            val result = LlmModelHelper.initialize(
                context = appContext,
                modelPath = modelPath,
                enableVision = true,
            )
            result.onSuccess { inst ->
                instance = inst
                viewModelScope.launch(Dispatchers.Main) { error = null }
            }.onFailure { e ->
                viewModelScope.launch(Dispatchers.Main) { error = e.message ?: "Model init failed" }
            }
            withContext(Dispatchers.Main) { preparing = false }
        }
    }

    fun clearResponse() { response = null; error = null }

    fun describeImage(bitmap: Bitmap?, prompt: String = "Describe the circled region in the image.") {
        val inst = instance
        if (inst == null) {
            error = "Model not initialized"
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                inProgress = true
                response = null
                error = null
            }
            try {
                LlmModelHelper.resetSession(inst, enableVision = (bitmap != null))
                val imgs = if (bitmap != null) listOf(bitmap) else emptyList()
                LlmModelHelper.runInference(
                    instance = inst,
                    input = prompt,
                    images = imgs,
                    resultListener = { partial, done ->
                        viewModelScope.launch(Dispatchers.Main) {
                            response = (response ?: "") + partial
                            if (done) inProgress = false
                        }
                    },
                    cleanUpListener = {
                        viewModelScope.launch(Dispatchers.Main) { inProgress = false }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = e.message ?: "Inference error" }
                withContext(Dispatchers.Main) { inProgress = false }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        LlmModelHelper.cleanUp(instance)
        instance = null
    }

    companion object {
        fun provideFactory(
            appContext: Context,
            modelPath: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LlmViewModel(appContext.applicationContext, modelPath) as T
            }
        }
    }
}
