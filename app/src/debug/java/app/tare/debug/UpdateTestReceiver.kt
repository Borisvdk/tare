package app.tare.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.tare.data.update.ApkCheck
import app.tare.data.update.UpdateInstaller
import java.io.File

/**
 * Debug builds only: runs a local APK through the in-app update's check and installer, so the
 * install flow can be tried on the emulator without GitHub.
 *
 *   TARE_VERSION_CODE=1001 scripts/dev.sh build
 *   adb shell "run-as app.tare.debug sh -c 'mkdir -p cache/updates && cat > cache/updates/test.apk'" < app-debug.apk
 *   adb shell am broadcast -n app.tare.debug/app.tare.debug.UpdateTestReceiver
 */
class UpdateTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val apk = File(context.cacheDir, "updates/test.apk")
        val problem = if (apk.exists()) ApkCheck.problem(context, apk) else "No APK at ${apk.path}"
        if (problem != null) {
            Log.w("TareUpdate", "Not installing: $problem")
            return
        }
        Log.i("TareUpdate", "Installing ${apk.length()} bytes")
        UpdateInstaller.install(context, apk)
    }
}
