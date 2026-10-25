package app.tare.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tare.TareApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Daily backup into a folder the user picked once (e.g. one synced to Google Drive). Tare is
 * local-first, so this file is what survives a lost or reset phone. The file is overwritten
 * each time; the folder's sync history keeps older copies.
 */
object AutoBackup {
    const val FILE_NAME = "tare-backup.json"
    private const val PERIODIC = "auto-backup"
    private const val NOW = "auto-backup-now"

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    /** Writes the backup into [folder]; returns false when the folder is gone or not writable. */
    suspend fun writeTo(context: Context, folder: Uri, backup: BackupManager): Boolean {
        val tree = DocumentFile.fromTreeUri(context, folder) ?: return false
        if (!tree.canWrite()) return false
        val file = tree.findFile(FILE_NAME) ?: tree.createFile("application/json", FILE_NAME) ?: return false
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { backup.export(it) } ?: return false
        return true
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as? TareApp)?.graph ?: return Result.failure()
        val folder = graph.settings.autoBackupFolder.first() ?: return Result.success()
        val ok = runCatching { AutoBackup.writeTo(applicationContext, Uri.parse(folder), graph.backup) }.getOrDefault(false)
        if (!ok) return if (runAttemptCount < 3) Result.retry() else Result.failure()
        graph.settings.setLastAutoBackup(graph.now())
        return Result.success()
    }
}
