package app.tare.data.update

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val requests = mutableListOf<HttpRequestData>()

    private fun repo(current: Int = 28, handler: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData): UpdateRepository {
        val client = HttpClient(MockEngine { request ->
            requests += request
            handler(request)
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        return UpdateRepository(client, currentVersionCode = current)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private val releases = """
        [
          {"tag_name":"v0.1.30","body":"## What's Changed\n* feat: in-app updates by @Borisvdk in https://github.com/Borisvdk/tare/pull/26\n* fix: scanner by @Borisvdk in https://github.com/Borisvdk/tare/pull/25\n","draft":false,"prerelease":false,
           "assets":[{"name":"tare-0.1.30.apk","size":2048,"url":"https://api.github.com/repos/Borisvdk/tare/releases/assets/9","browser_download_url":"https://github.com/Borisvdk/tare/releases/download/v0.1.30/tare-0.1.30.apk"}]},
          {"tag_name":"v0.1.29","body":"* feat: Health Connect by @Borisvdk in https://github.com/Borisvdk/tare/pull/24","assets":[{"name":"tare-0.1.29.apk","size":1,"url":"x","browser_download_url":"y"}]},
          {"tag_name":"v0.1.28","body":"* feat: new logo by @Borisvdk in https://github.com/Borisvdk/tare/pull/23","assets":[]}
        ]
    """.trimIndent()

    @Test
    fun offersTheNewestReleaseWithEverythingSinceThisOne() = runTest {
        val result = repo { json(releases) }.check() as UpdateCheck.Available
        assertThat(result.update.versionName).isEqualTo("0.1.30")
        assertThat(result.update.versionCode).isEqualTo(30)
        // The public download link, not the API asset URL (that one serves JSON to JSON clients).
        assertThat(result.update.assetUrl).isEqualTo("https://github.com/Borisvdk/tare/releases/download/v0.1.30/tare-0.1.30.apk")
        // 0.1.28 is what's installed, so its notes don't count.
        assertThat(result.update.changes).containsExactly("In-app updates", "Scanner", "Health Connect").inOrder()
        // The repository is public: no account, no token.
        assertThat(requests.single().headers[HttpHeaders.Authorization]).isNull()
        assertThat(requests.single().url.encodedPath).isEqualTo("/repos/Borisvdk/tare/releases")
    }

    @Test
    fun upToDateWhenNothingIsNewer() = runTest {
        assertThat(repo(current = 30) { json(releases) }.check()).isEqualTo(UpdateCheck.UpToDate)
    }

    @Test
    fun rateLimitsAndMissingReleasesSaySo() = runTest {
        val limited = repo { json("""{"message":"API rate limit exceeded"}""", HttpStatusCode.Forbidden) }.check() as UpdateCheck.Failed
        assertThat(limited.message).contains("limiting")
        val missing = repo { json("""{"message":"Not Found"}""", HttpStatusCode.NotFound) }.check() as UpdateCheck.Failed
        assertThat(missing.message).contains("Couldn't find")
    }

    @Test
    fun downloadFollowsTheRedirectToStorage() = runTest {
        val apkBytes = ByteArray(2048) { it.toByte() }
        val repository = repo { request ->
            when (request.url.host) {
                "github.com" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://release-assets.githubusercontent.com/apk?sig=abc"))
                else -> respond(apkBytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, apkBytes.size.toString()))
            }
        }
        val update = AppUpdate("0.1.30", 30, "https://github.com/Borisvdk/tare/releases/download/v0.1.30/tare-0.1.30.apk", 2048, emptyList())
        val target = folder.root.resolve("updates/tare.apk")
        var progress = 0f
        repository.download(update, target) { progress = it }

        assertThat(target.readBytes()).isEqualTo(apkBytes)
        assertThat(progress).isEqualTo(1f)
        assertThat(requests.map { it.url.host }).containsExactly("github.com", "release-assets.githubusercontent.com").inOrder()
        assertThat(requests.map { it.headers[HttpHeaders.Authorization] }).containsExactly(null, null)
    }

    @Test
    fun changeListLeavesOutPlumbing() {
        val notes = """
            * feat(log): favourites by @B in https://x/pull/1
            * docs: readme by @B in https://x/pull/2
            * ci: faster by @B in https://x/pull/3
            * fix!: crash by @B in https://x/pull/4
        """.trimIndent()
        assertThat(UpdateRepository.changesIn(notes)).containsExactly("Favourites", "Crash").inOrder()
    }

    @Test
    fun versionCodesComeFromTags() {
        assertThat(UpdateRepository.versionCodeOf("v0.1.27")).isEqualTo(27)
        assertThat(UpdateRepository.versionCodeOf("nightly")).isNull()
    }
}
