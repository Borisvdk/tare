package app.tare.ui.scan

import android.content.Context
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Remembers whether the in-app camera scanner works on this phone. Some phones' camera stacks
 * crash natively, where no try/catch helps: if the app crashed while the camera was on screen,
 * scans go through Google's scanner from then on (until the owner asks to try the camera again).
 */
object CameraScannerGuard {
    private const val PREFS = "scanner"
    private const val ACTIVE = "camera_active"
    private const val BROKEN = "camera_broken"

    fun isBroken(context: Context): Boolean = prefs(context).getBoolean(BROKEN, false)

    /** Call once per process start, with whether the previous process ended in a crash. */
    fun onAppStart(context: Context, lastExitWasCrash: Boolean) {
        val p = prefs(context)
        if (p.getBoolean(ACTIVE, false) && lastExitWasCrash) p.edit().putBoolean(BROKEN, true).apply()
        p.edit().putBoolean(ACTIVE, false).apply()
    }

    // commit(), not apply(): the process may be about to die.
    fun active(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(ACTIVE, active).commit()
    }

    fun failed(context: Context) {
        prefs(context).edit().putBoolean(ACTIVE, false).putBoolean(BROKEN, true).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** The choice in Settings: Google's scanner, or Tare's own camera. */
    fun useGoogle(context: Context, google: Boolean) {
        if (google) prefs(context).edit().putBoolean(BROKEN, true).apply() else reset(context)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

sealed interface GoogleScan {
    data class Code(val value: String) : GoogleScan
    data object Cancelled : GoogleScan
    data class Failed(val message: String) : GoogleScan
}

/**
 * Makes sure Google's scanner module is on the phone. Play services fetches it on demand, but a
 * sideloaded app has to ask; without this the scanner just reports it's "waiting".
 */
suspend fun prepareGoogleScanner(context: Context): Boolean = suspendCancellableCoroutine { cont ->
    val client = ModuleInstall.getClient(context)
    lateinit var listener: InstallStatusListener
    fun finish(ready: Boolean) {
        client.unregisterListener(listener)
        if (cont.isActive) cont.resume(ready)
    }
    listener = InstallStatusListener { update ->
        when (update.installState) {
            ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> finish(true)
            ModuleInstallStatusUpdate.InstallState.STATE_CANCELED, ModuleInstallStatusUpdate.InstallState.STATE_FAILED -> finish(false)
        }
    }
    val request = ModuleInstallRequest.Builder().addApi(GmsBarcodeScanning.getClient(context)).setListener(listener).build()
    client.installModules(request)
        .addOnSuccessListener { if (it.areModulesAlreadyInstalled()) finish(true) }
        .addOnFailureListener { finish(false) }
    cont.invokeOnCancellation { client.unregisterListener(listener) }
}

/** Google's full-screen scanner (Play services). Needs no camera permission and no camera code of ours. */
suspend fun scanWithGoogle(context: Context): GoogleScan = suspendCancellableCoroutine { cont ->
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .enableAutoZoom()
        .build()
    fun finish(result: GoogleScan) {
        if (cont.isActive) cont.resume(result)
    }
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> finish(barcode.rawValue?.let { GoogleScan.Code(it) } ?: GoogleScan.Cancelled) }
        .addOnCanceledListener { finish(GoogleScan.Cancelled) }
        .addOnFailureListener { finish(GoogleScan.Failed(it.message ?: "Google's scanner isn't available")) }
}
