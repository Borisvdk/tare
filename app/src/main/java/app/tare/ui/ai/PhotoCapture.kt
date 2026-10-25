package app.tare.ui.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** Launchers for taking a photo, picking one from the gallery, and dictating text. */
class MediaInput(
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit,
    val dictate: () -> Unit,
)

@Composable
fun rememberMediaInput(onPhoto: (ByteArray) -> Unit, onSpeech: (String) -> Unit = {}): MediaInput {
    val context = LocalContext.current
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    val currentOnSpeech by rememberUpdatedState(onSpeech)
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) readBytes(context, uri)?.let(currentOnPhoto)
        uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        pendingUri = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) readBytes(context, uri)?.let(currentOnPhoto)
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(currentOnSpeech)
    }

    return remember {
        MediaInput(
            takePhoto = {
                val uri = newPhotoUri(context)
                pendingUri = uri
                camera.launch(uri)
            },
            pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            dictate = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "What did you eat?")
                runCatching { speech.launch(intent) }
            },
        )
    }
}

private fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File.createTempFile("photo_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
}

private fun readBytes(context: Context, uri: Uri): ByteArray? =
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
