package app.tare.data.ai

import android.app.Application
import app.tare.data.settings.AiModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks the request we send to Claude and how we read the answer, against a local mock server. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class FoodAiTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun ai(model: AiModel = AiModel.OPUS, key: String? = "sk-ant-test") =
        FoodAi(apiKey = { key }, model = { model }, baseUrl = server.url("/").toString().trimEnd('/'))

    private fun messageResponse(json: String, stopReason: String = "end_turn") = MockResponse.Builder()
        .code(200)
        .addHeader("content-type", "application/json")
        .body(
            """
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
             "content":[{"type":"text","text":${Json.encodeToString(String.serializer(), json)}}],
             "stop_reason":"$stopReason","stop_sequence":null,
             "usage":{"input_tokens":10,"output_tokens":10}}
            """.trimIndent(),
        )
        .build()

    @Test
    fun estimateMealSendsStructuredRequestAndParsesItems() = runTest {
        server.enqueue(
            messageResponse(
                """{"title":"Breakfast","note":"","items":[{"name":"Egg","grams":100,"isLiquid":false,"kcalPer100g":143,
                "proteinPer100g":12.6,"carbsPer100g":0.7,"fatPer100g":9.5,"fiberPer100g":0,"confidence":"high"}]}""",
            ),
        )
        val result = ai().estimateMeal("2 eggs", photo = null)

        val estimate = (result as AiResult.Success).value
        assertThat(estimate.title).isEqualTo("Breakfast")
        assertThat(estimate.items.single().kcalPer100g).isEqualTo(143.0)

        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/v1/messages")
        // SDK 3.x sends the key as a bearer token.
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer sk-ant-test")
        assertThat(request.headers["anthropic-beta"]).contains("server-side-fallback-2026-07-01")
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("claude-opus-5-5")
        assertThat(body["fallbacks"]!!.jsonPrimitive.content).isEqualTo("default")
        val format = body["output_config"]!!.jsonObject["format"]!!.jsonObject
        assertThat(format["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
        val schema = format["schema"] as JsonObject
        assertThat(schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactly("title", "items", "note")
        assertThat(body["messages"]!!.jsonArray.single().jsonObject["role"]!!.jsonPrimitive.content).isEqualTo("user")
    }

    @Test
    fun haikuRequestsSkipServerFallback() = runTest {
        server.enqueue(messageResponse("""{"title":"x","note":"","items":[]}"""))
        ai(AiModel.HAIKU).estimateMeal("tea", null)
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertThat(body.containsKey("fallbacks")).isFalse()
    }

    @Test
    fun missingKeyNeverCallsTheApi() = runTest {
        assertThat(ai(key = null).estimateMeal("x", null)).isEqualTo(AiResult.NoApiKey)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun refusalBecomesFriendlyFailure() = runTest {
        server.enqueue(messageResponse("", stopReason = "refusal"))
        assertThat(ai().estimateMeal("x", null)).isInstanceOf(AiResult.Failed::class.java)
    }

    @Test
    fun unauthorizedKeyIsReported() = runTest {
        server.enqueue(
            MockResponse.Builder().code(401).addHeader("content-type", "application/json")
                .body("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""").build(),
        )
        val result = ai().estimateMeal("x", null) as AiResult.Failed
        assertThat(result.message).contains("rejected")
    }
}
