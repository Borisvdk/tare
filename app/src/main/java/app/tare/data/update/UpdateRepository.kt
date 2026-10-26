package app.tare.data.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/** A newer release on GitHub, with the PR titles of every release since this one. */
data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val assetUrl: String,
    val size: Long,
    val changes: List<String>,
)

sealed interface UpdateCheck {
    data class Available(val update: AppUpdate) : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String,
    val size: Long,
    // The public download link always serves the file. (The API's asset URL answers with JSON
    // whenever Accept mentions JSON, which the shared client's content negotiation adds.)
    @SerialName("browser_download_url") val downloadUrl: String,
)

/**
 * Finds and downloads new releases of Tare from its public GitHub repository; no account or
 * token needed. CI tags releases `v0.1.<commit count>`, and the commit count is also the
 * version code, so the tag says which build is newer.
 */
class UpdateRepository(
    private val http: HttpClient,
    private val currentVersionCode: Int,
    private val repo: String = "Borisvdk/tare",
    private val api: String = "https://api.github.com",
) {
    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val response = try {
            http.get("$api/repos/$repo/releases?per_page=20") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
        } catch (e: Exception) {
            return@withContext UpdateCheck.Failed("Couldn't reach GitHub. Check your connection.")
        }
        when (response.status) {
            // Unauthenticated requests get 60 an hour per network; a check every few hours is far below.
            HttpStatusCode.Forbidden, HttpStatusCode.TooManyRequests ->
                return@withContext UpdateCheck.Failed("GitHub is limiting requests right now. Try again later.")
            HttpStatusCode.NotFound -> return@withContext UpdateCheck.Failed("Couldn't find Tare's releases on GitHub")
            else -> if (!response.status.isSuccess()) return@withContext UpdateCheck.Failed("GitHub error (${response.status.value})")
        }
        val releases = runCatching { response.body<List<GhRelease>>() }.getOrElse {
            return@withContext UpdateCheck.Failed("Couldn't read GitHub's answer")
        }
        val newer = releases
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { r -> versionCodeOf(r.tag)?.let { it to r } }
            .filter { (code, _) -> code > currentVersionCode }
            .sortedByDescending { it.first }
        val (code, latest) = newer.firstOrNull() ?: return@withContext UpdateCheck.UpToDate
        val apk = latest.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext UpdateCheck.UpToDate
        UpdateCheck.Available(
            AppUpdate(
                versionName = latest.tag.removePrefix("v"),
                versionCode = code,
                assetUrl = apk.downloadUrl,
                size = apk.size,
                changes = newer.flatMap { (_, r) -> changesIn(r.body) }.distinct(),
            ),
        )
    }

    /** Downloads the APK to [target]; the download link redirects to GitHub's file storage. */
    suspend fun download(update: AppUpdate, target: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        http.prepareGet(update.assetUrl) {
            timeout { requestTimeoutMillis = DOWNLOAD_TIMEOUT }
        }.execute { response ->
            if (!response.status.isSuccess()) error("Download failed (${response.status.value})")
            val total = response.contentLength() ?: update.size
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            var done = 0L
            target.outputStream().use { out ->
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read < 0) break
                    if (read == 0) {
                        if (channel.isClosedForRead) break
                        continue
                    }
                    out.write(buffer, 0, read)
                    done += read
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        target
    }

    companion object {
        private const val DOWNLOAD_TIMEOUT = 5 * 60_000L

        /** `v0.1.27` → 27. */
        fun versionCodeOf(tag: String): Int? = Regex("""(\d+)$""").find(tag.trim())?.value?.toIntOrNull()

        private val KIND = Regex("""^[a-z]+(\([^)]*\))?!?:\s*""")
        private val PLUMBING = Regex("""^(docs|ci|chore|test|build|refactor|style)(\(|!|:)""")

        /**
         * What changed, from GitHub's generated notes (`* feat: x by @someone in https://…/pull/24`):
         * the PR titles without their `feat:`/`fix:` prefix, leaving out docs and tooling.
         */
        fun changesIn(body: String?): List<String> = body.orEmpty().lines()
            .map { it.trim() }
            .filter { it.startsWith("* ") }
            .map { it.removePrefix("* ").substringBefore(" by @").substringBefore(" in https://").trim() }
            .filterNot { PLUMBING.containsMatchIn(it) }
            .map { it.replace(KIND, "").replaceFirstChar(Char::uppercase) }
            .filter { it.isNotEmpty() }
    }
}
