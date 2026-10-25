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

    private fun repo(token: String? = "github_pat_test", current: Int = 28, handler: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData): UpdateRepository {
        val client = HttpClient(MockEngine { request ->
            requests += request
            handler(request)
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        return UpdateRepository(client, token = { token }, currentVersionCode = current)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private val releases = """
        [
          {"tag_name":"v0.1.30","body":"## What's Changed\n* feat: in-app updates by @Borisvdk in https://github.com/Borisvdk/tare/pull/26\n* fix: scanner by @Borisvdk in https://github.com/Borisvdk/tare/pull/25\n","draft":false,"prerelease":false,
           "assets":[{"name":"tare-0.1.30.apk","size":2048,"url":"https://api.github.com/repos/Borisvdk/tare/releases/assets/9"}]},
          {"tag_name":"v0.1.29","body":"* feat: Health Connect by @Borisvdk in https://github.com/Borisvdk/tare/pull/24","assets":[{"name":"tare-0.1.29.apk","size":1,"url":"x"}]},
          {"tag_name":"v0.1.28","body":"* feat: new logo by @Borisvdk in https://github.com/Borisvdk/tare/pull/23","assets":[]}
        ]
    """.trimIndent()

    @Test
    fun offersTheNewestReleaseWithEverythingSinceThisOne() = runTest {
        val result = repo { json(releases) }.check() as UpdateCheck.Available
        assertThat(result.update.versionName).isEqualTo("0.1.30")
        assertThat(result.update.versionCode).isEqualTo(30)
        assertThat(result.update.assetUrl).endsWith("/releases/assets/9")
        // 0.1.28 is what's installed, so its notes don't count.
        assertThat(result.update.changes).containsExactly("feat: in-app updates", "fix: scanner", "feat: Health Connect").inOrder()
        assertThat(requests.single().headers[HttpHeaders.Authorization]).isEqualTo("Bearer github_pat_test")
        assertThat(requests.single().url.encodedPath).isEqualTo("/repos/Borisvdk/tare/releases")
    }

    @Test
    fun upToDateWhenNothingIsNewer() = runTest {
        assertThat(repo(current = 30) { json(releases) }.check()).isEqualTo(UpdateCheck.UpToDate)
    }

    @Test
    fun noTokenMeansNoRequest() = runTest {
        assertThat(repo(token = null) { json(releases) }.check()).isEqualTo(UpdateCheck.NoToken)
        assertThat(requests).isEmpty()
    }

    @Test
    fun aRejectedTokenSaysSo() = runTest {
        val result = repo { json("""{"message":"Bad credentials"}""", HttpStatusCode.Unauthorized) }.check() as UpdateCheck.Failed
        assertThat(result.message).contains("rejected")
        val noAccess = repo { json("""{"message":"Not Found"}""", HttpStatusCode.NotFound) }.check() as UpdateCheck.Failed
        assertThat(noAccess.message).contains("can't read")
    }

    @Test
    fun downloadFollowsTheRedirectWithoutTheToken() = runTest {
        val apkBytes = ByteArray(2048) { it.toByte() }
        val repository = repo { request ->
            when (request.url.host) {
                "api.github.com" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://objects.githubusercontent.com/apk?sig=abc"))
                else -> respond(apkBytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, apkBytes.size.toString()))
            }
        }
        val update = AppUpdate("0.1.30", 30, "https://api.github.com/repos/Borisvdk/tare/releases/assets/9", 2048, emptyList())
        val target = folder.root.resolve("updates/tare.apk")
        var progress = 0f
        repository.download(update, target) { progress = it }

        assertThat(target.readBytes()).isEqualTo(apkBytes)
        assertThat(progress).isEqualTo(1f)
        val (asset, storage) = requests
        assertThat(asset.headers[HttpHeaders.Authorization]).isEqualTo("Bearer github_pat_test")
        assertThat(asset.headers[HttpHeaders.Accept]).isEqualTo("application/octet-stream")
        assertThat(storage.url.host).isEqualTo("objects.githubusercontent.com")
        assertThat(storage.headers[HttpHeaders.Authorization]).isNull()
    }

    @Test
    fun versionCodesComeFromTags() {
        assertThat(UpdateRepository.versionCodeOf("v0.1.27")).isEqualTo(27)
        assertThat(UpdateRepository.versionCodeOf("nightly")).isNull()
    }
}
