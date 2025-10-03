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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.circle2capture.llm.LlmViewModel
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

private enum class Screen { Chat, Editor, Result }

@Composable
private fun App() {
    var screen by remember { mutableStateOf(Screen.Chat) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val modelPath = "/data/local/tmp/llm/gemma-3n-E2B-it-int4.task"
    val llmVm: LlmViewModel = viewModel(
        factory = LlmViewModel.provideFactory(
            appContext = context.applicationContext,
            modelPath = modelPath
        )
    )

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
                    llmVm.clearResponse()
                },
                onCropped = { bmp ->
                    croppedBitmap = bmp
                    screen = Screen.Result
                    llmVm.describeImage(bmp)
                }
            )
            Screen.Result -> ResultScreen(
                bitmap = croppedBitmap,
                llmResponse = llmVm.response ?: llmVm.error,
                isLoading = llmVm.inProgress || llmVm.preparing,
                onRestart = {
                    pickedUri = null
                    croppedBitmap = null
                    llmVm.clearResponse()
                    screen = Screen.Chat
                }
            )
        }
    }
}


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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    imageUri: Uri?,
    onBack: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val ctx = LocalContext.current
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
                modifier = Modifier.fillMaxSize()
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
