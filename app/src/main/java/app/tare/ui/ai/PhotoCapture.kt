package app.tare.ui.ai

import android.content.Context
import android.net.Uri
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

/** Launchers for taking a photo and picking one from the gallery. Dictation is [Dictation]. */
class MediaInput(
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit,
)

@Composable
fun rememberMediaInput(onPhoto: (ByteArray) -> Unit): MediaInput {
    val context = LocalContext.current
    val currentOnPhoto by rememberUpdatedState(onPhoto)
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

    return remember {
        MediaInput(
            takePhoto = {
                val uri = newPhotoUri(context)
                pendingUri = uri
                camera.launch(uri)
            },
            pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
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
