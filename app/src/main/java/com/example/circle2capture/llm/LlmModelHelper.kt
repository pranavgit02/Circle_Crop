package com.example.circle2capture.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession

data class LlmModelInstance(val engine: LlmInference, var session: LlmInferenceSession)

typealias ResultListener = (partialResult: String, done: Boolean) -> Unit
typealias CleanUpListener = () -> Unit

object LlmModelHelper {
    private const val TAG = "LlmModelHelper"

    fun initialize(
        context: Context,
        modelPath: String,
        maxTokens: Int = 512,
        enableVision: Boolean = true,
    ): Result<LlmModelInstance> = try {
        val engineOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .setMaxNumImages(if (enableVision) 1 else 0)
            .build()
        val engine = LlmInference.createFromOptions(context, engineOptions)

        val session = LlmInferenceSession.createFromOptions(
            engine,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(enableVision)
                        .build()
                )
                .build()
        )
        Result.success(LlmModelInstance(engine, session))
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize LLM", e)
        Result.failure(e)
    }

    fun resetSession(instance: LlmModelInstance, enableVision: Boolean = true) {
        try {
            instance.session.close()
        } catch (_: Exception) {}
        instance.session = LlmInferenceSession.createFromOptions(
            instance.engine,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(enableVision)
                        .build()
                )
                .build()
        )
    }

    fun runInference(
        instance: LlmModelInstance,
        input: String,
        images: List<Bitmap> = emptyList(),
        resultListener: ResultListener,
        cleanUpListener: CleanUpListener,
    ) {
        val session = instance.session
        if (input.isNotBlank()) session.addQueryChunk(input)
        for (bmp in images) session.addImage(BitmapImageBuilder(bmp).build())
        session.generateResponseAsync { partial, done ->
            try {
                resultListener(partial, done)
            } finally {
                if (done) cleanUpListener()
            }
        }
    }

    fun cleanUp(instance: LlmModelInstance?) {
        if (instance == null) return
        try { instance.session.close() } catch (_: Exception) {}
        try { instance.engine.close() } catch (_: Exception) {}
    }
}
