package com.mosquednd.net

import com.mosquednd.util.RedactionUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NetworkLoggingInterceptorTest {

    @Test
    fun `test network logging interceptor redacts secrets in request and response`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkLoggingInterceptor())
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val respBody = "{\"status\":\"success\",\"password\":\"secretPassword123\",\"email\":\"user@example.com\"}"
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(respBody.toResponseBody("application/json".toMediaTypeOrNull()))
                    .sentRequestAtMillis(System.currentTimeMillis())
                    .receivedResponseAtMillis(System.currentTimeMillis())
                    .build()
            })
            .build()

        val requestBody = "{\"password\":\"mySecretPassword123\",\"phone\":\"+1-555-0199\"}".toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.mosquednd.org/v1/login?api_key=apikey12345")
            .header("Authorization", "Bearer secretBearerToken")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)

        val redactedReqBody = RedactionUtils.redact("password=mySecretPassword123")
        assertTrue(redactedReqBody.contains("[REDACTED]"))
        assertFalse(redactedReqBody.contains("mySecretPassword123"))

        val redactedResp = RedactionUtils.redact("email=user@example.com")
        assertTrue(redactedResp.contains("[REDACTED_EMAIL]"))
        assertFalse(redactedResp.contains("user@example.com"))
    }
}
