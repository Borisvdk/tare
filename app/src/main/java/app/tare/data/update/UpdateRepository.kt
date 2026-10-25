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
    data object NoToken : UpdateCheck
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
private data class GhAsset(val name: String, val size: Long, val url: String)

/**
 * Finds and downloads new releases of Tare from its (private) GitHub repository, with a
 * read-only token the owner pastes in once. CI tags releases `v0.1.<commit count>`, and the
 * commit count is also the version code, so the tag says which build is newer.
 */
class UpdateRepository(
    private val http: HttpClient,
    private val token: suspend () -> String?,
    private val currentVersionCode: Int,
    private val repo: String = "Borisvdk/tare",
    private val api: String = "https://api.github.com",
) {
    // Shares the engine with [http]; never closed, closing it would close the shared engine.
    private val noRedirects by lazy { http.config { followRedirects = false } }

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext UpdateCheck.NoToken
        val response = try {
            http.get("$api/repos/$repo/releases?per_page=20") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
        } catch (e: Exception) {
            return@withContext UpdateCheck.Failed("Couldn't reach GitHub. Check your connection.")
        }
        when (response.status) {
            HttpStatusCode.Unauthorized -> return@withContext UpdateCheck.Failed("GitHub rejected the token. Paste a new one.")
            HttpStatusCode.Forbidden, HttpStatusCode.NotFound ->
                return@withContext UpdateCheck.Failed("This token can't read $repo. Give it read access to the repository's contents.")
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
                assetUrl = apk.url,
                size = apk.size,
                changes = newer.flatMap { (_, r) -> changesIn(r.body) }.distinct(),
            ),
        )
    }

    /**
     * Downloads the APK to [target]. The asset URL answers with a redirect to a signed storage
     * URL, which must be fetched *without* the token (storage rejects a second credential, and
     * the token has no business going there).
     */
    suspend fun download(update: AppUpdate, target: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val token = token() ?: error("No GitHub token")
        val first = noRedirects.get(update.assetUrl) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/octet-stream")
        }
        val location = first.headers[HttpHeaders.Location]
        val url = when {
            first.status.value in 300..399 && location != null -> location
            first.status.isSuccess() -> null
            else -> error("GitHub error (${first.status.value})")
        }
        target.parentFile?.mkdirs()
        val request = if (url != null) noRedirects.prepareGet(url) { timeout { requestTimeoutMillis = DOWNLOAD_TIMEOUT } } else null
        val save: suspend (io.ktor.client.statement.HttpResponse) -> Unit = { response ->
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
        if (request != null) {
            request.execute { save(it) }
        } else {
            // Served directly (no redirect): re-request as a stream with the token.
            noRedirects.prepareGet(update.assetUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/octet-stream")
                timeout { requestTimeoutMillis = DOWNLOAD_TIMEOUT }
            }.execute { save(it) }
        }
        target
    }

    companion object {
        private const val DOWNLOAD_TIMEOUT = 5 * 60_000L

        /** `v0.1.27` → 27. */
        fun versionCodeOf(tag: String): Int? = Regex("""(\d+)$""").find(tag.trim())?.value?.toIntOrNull()

        /** PR titles from GitHub's generated notes: `* feat: x by @someone in https://…/pull/24`. */
        fun changesIn(body: String?): List<String> = body.orEmpty().lines()
            .map { it.trim() }
            .filter { it.startsWith("* ") }
            .map { it.removePrefix("* ").substringBefore(" by @").substringBefore(" in https://").trim() }
            .filter { it.isNotEmpty() }
    }
}
