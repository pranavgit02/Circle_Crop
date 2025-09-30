package com.example.circle2capture

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.circle2capture.overlay.CircleOverlay
import com.example.circle2capture.utils.computeFitBounds
import com.example.circle2capture.utils.cropCircleFromBitmap
import kotlin.math.min

// Imports for Gemma 3n / LLM Inference
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSessionOptions
import com.google.mediapipe.tasks.core.GraphOptions
import com.google.mediapipe.framework.image.BitmapImageBuilder

/**
 * Main entry activity for the Circle Crop demo.
 *
 * This activity demonstrates how to select an image, overlay a resizable
 * circle over it and crop that region to produce a new bitmap.  In addition
 * we load an on‑device Gemma 3n model using the MediaPipe LLM Inference API
 * and generate a description of the cropped region.  The model file must be
 * present on the device (see build.gradle for dependency and docs for download).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

// Simple navigation for three screens used in this demo
private enum class Screen { Chat, Editor, Result }

@Composable
private fun App() {
    // Keep track of which screen is shown
    var screen by remember { mutableStateOf(Screen.Chat) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Hold the LLM inference engine and its response
    val context = LocalContext.current
    var llmInference by remember { mutableStateOf<LlmInference?>(null) }
    var llmResponse by remember { mutableStateOf<String?>(null) }
    var llmLoading by remember { mutableStateOf(false) }
    // Coroutine scope for asynchronous model loading and inference
    val scope = rememberCoroutineScope()

    // Initialise the Gemma 3n model once when this composable is first shown
    LaunchedEffect(Unit) {
        // Path on the device where the .task file is stored.  During development
        // you can push the model with adb as described in the docs:
        // adb push output_path /data/local/tmp/llm/gemma-3n-e2b-it.task
        val modelPath = "/data/local/tmp/llm/gemma-3n-e2b-it.task"
        try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTopK(40)
                .setTemperature(0.4f)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            // If model loading fails, keep llmInference null.  UI will ignore it.
            e.printStackTrace()
        }
    }

    Surface {
        when (screen) {
            Screen.Chat -> ChatLikePicker { uri ->
                pickedUri = uri
                screen = Screen.Editor
            }
            Screen.Editor -> EditorScreen(
                imageUri = pickedUri,
                onBack = {
                    screen = Screen.Chat
                    llmResponse = null
                },
                onCropped = { bmp ->
                    croppedBitmap = bmp
                    screen = Screen.Result
                    // Trigger LLM inference asynchronously on the cropped bitmap
                    val inference = llmInference
                    if (inference != null && bmp != null) {
                        llmLoading = true
                        scope.launch {
                            try {
                                // Convert the cropped bitmap into an MPImage
                                val mpImage = BitmapImageBuilder(bmp).build()
                                // Configure a session that enables vision modality
                                val sessionOptions = LlmInferenceSessionOptions.builder()
                                    .setTopK(10)
                                    .setTemperature(0.4f)
                                    .setGraphOptions(
                                        GraphOptions.builder().setEnableVisionModality(true).build()
                                    )
                                    .build()
                                // Create a new session and send the image and prompt
                                LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                                    session.addQueryChunk("Describe the circled region in the image.")
                                    session.addImage(mpImage)
                                    val result = session.generateResponse()
                                    llmResponse = result
                                }
                            } catch (e: Exception) {
                                llmResponse = "Error running model: ${e.message}"
                            } finally {
                                llmLoading = false
                            }
                        }
                    }
                }
            )
            Screen.Result -> ResultScreen(
                bitmap = croppedBitmap,
                llmResponse = llmResponse,
                isLoading = llmLoading,
                onRestart = {
                    pickedUri = null
                    croppedBitmap = null
                    llmResponse = null
                    llmLoading = false
                    screen = Screen.Chat
                }
            )
        }
    }
}

/* -----------------------------
 * Screen 1: Chat-like picker
 * ----------------------------- */
@Composable
private fun ChatLikePicker(onPicked: (Uri) -> Unit) {
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onPicked) }
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Chat", style = MaterialTheme.typography.titleLarge)
            AssistantBubble("Send me an image and I’ll let you circle-to-search ✨")
            UserBubble("Okay, selecting an image…")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                pickMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        ) { Text("Attach image from gallery") }
    }
}

@Composable
private fun AssistantBubble(text: String) =
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large
            )
            .padding(12.dp)
    ) { Text(text) }

@Composable
private fun UserBubble(text: String) =
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large
                )
                .padding(12.dp)
        ) { Text(text) }
    }

/* -----------------------------
 * Screen 2: Editor with circle
 * ----------------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    imageUri: Uri?,
    onBack: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val ctx = LocalContext.current
    // Decode as SOFTWARE to avoid "Software rendering doesn't support hardware bitmaps"
    val srcBitmap by remember(imageUri) {
        mutableStateOf(
            imageUri?.let { uri ->
                runCatching {
                    if (Build.VERSION.SDK_INT >= 28) {
                        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = false
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(ctx.contentResolver, uri)
                    }
                }.getOrNull()
            }
        )
    }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imageBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var center by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var radius by remember { mutableStateOf(120f) }
    // Only allow crop when circle is valid and fully inside the image area
    val canCrop = imageBounds.width > 0f &&
            imageBounds.height > 0f &&
            radius >= 8f &&
            center.x - radius >= imageBounds.left &&
            center.x + radius <= imageBounds.right &&
            center.y - radius >= imageBounds.top &&
            center.y + radius <= imageBounds.bottom
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Circle selector") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
        )
        if (srcBitmap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No image loaded")
            }
            return
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { sz ->
                    boxSize = sz
                    imageBounds = computeFitBounds(sz, srcBitmap!!.width, srcBitmap!!.height)
                    if (center == androidx.compose.ui.geometry.Offset.Zero) {
                        center = imageBounds.center
                        radius = min(imageBounds.width, imageBounds.height) * 0.25f
                    }
                }
        ) {
            Image(
                bitmap = srcBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize() // replaced matchParentSize()
            )
            CircleOverlay(
                boxSize = boxSize,
                imageBounds = imageBounds,
                center = center,
                radius = radius,
                onChange = { c, r -> center = c; radius = r }
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                enabled = canCrop,
                onClick = {
                    val out = cropCircleFromBitmap(
                        source = srcBitmap!!,
                        imageBoundsInView = imageBounds,
                        circleCenterInView = center,
                        circleRadiusInView = radius
                    )
                    onCropped(out)
                },
                modifier = Modifier.weight(2f)
            ) { Text(if (canCrop) "Crop selected area" else "Adjust circle") }
        }
    }
}

/* -----------------------------
 * Screen 3: Result
 * ----------------------------- */
@Composable
private fun ResultScreen(
    bitmap: Bitmap?,
    llmResponse: String?,
    isLoading: Boolean,
    onRestart: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Result",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        if (bitmap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No crop produced")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .sizeIn(maxWidth = 480.dp, maxHeight = 480.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
                // Show loading indicator or response text
                when {
                    isLoading -> {
                        CircularProgressIndicator()
                        Text("Generating description…", textAlign = TextAlign.Center)
                    }
                    llmResponse != null -> {
                        Text(
                            llmResponse,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    else -> {
                        Text("No AI description available.", textAlign = TextAlign.Center)
                    }
                }
                Text(
                    "PNG contains only the circled content (transparent elsewhere).",
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRestart) { Text("Start over") }
            }
        }
    }
}
