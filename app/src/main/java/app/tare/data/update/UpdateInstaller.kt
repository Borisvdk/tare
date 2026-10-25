package app.tare.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import app.tare.TareApp
import java.io.File
import java.security.MessageDigest

/** Checks a downloaded APK before it's installed. */
object ApkCheck {
    /** Null when [apk] is a newer build of this app signed with the same key; otherwise why not. */
    fun problem(context: Context, apk: File): String? {
        val pm = context.packageManager
        val incoming = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return "The download isn't a valid app"
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        return when {
            incoming.packageName != context.packageName -> "The download is a different app (${incoming.packageName})"
            incoming.longVersionCode <= installed.longVersionCode -> "The download isn't newer than this version"
            signers(incoming) != signers(installed) -> "The download isn't signed with Tare's key"
            else -> null
        }
    }

    private fun signers(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs.orEmpty().map { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}

/**
 * Installs an update with the platform PackageInstaller. The first time, Android asks to allow
 * installs from Tare and to confirm; once Tare installed itself, later updates may go through
 * without asking (Android 12+, UPDATE_PACKAGES_WITHOUT_USER_ACTION).
 */
object UpdateInstaller {
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("tare.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callback = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            // Mutable: the installer fills in the status extras.
            val pending = PendingIntent.getBroadcast(context, id, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }
}

/** Hears back from the installer: asks the user to confirm, or reports why it failed. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val updater = (context.applicationContext as? TareApp)?.graph?.updater
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            // On success Android restarts nothing; this process is about to be replaced.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater?.installFailed("Update cancelled")
            else -> updater?.installFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed ($status)")
        }
    }
}
