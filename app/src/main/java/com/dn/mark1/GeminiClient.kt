
package com.dn.mark1

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class GeminiMessage(
    val role: String,
    val content: String
)

object GeminiClient {

    private const val ENDPOINT =
        "https://dn-mark-1.netlify.app/.netlify/functions/chat"

    private val client = OkHttpClient()

    fun send(
        messages: List<GeminiMessage>,
        onResult: (String?, String?) -> Unit
    ) {
        try {
            val messageArray = JSONArray()

            messages.takeLast(20).forEach { message ->
                messageArray.put(
                    JSONObject()
                        .put("role", message.role)
                        .put("content", message.content)
                )
            }

            val json = JSONObject()
                .put("messages", messageArray)
                .toString()

            val body = json.toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    onResult(null, e.message ?: "Network error")
                }

                override fun onResponse(
                    call: Call,
                    response: okhttp3.Response
                ) {
                    response.use {
                        val responseText =
                            it.body?.string().orEmpty()

                        if (!it.isSuccessful) {
                            onResult(
                                null,
                                "Server error ${it.code}: $responseText"
                            )
                            return
                        }

                        try {
                            val reply = JSONObject(responseText)
                                .optString("reply")

                            if (reply.isBlank()) {
                                onResult(null, "Empty AI response")
                            } else {
                                onResult(reply, null)
                            }
                        } catch (e: Exception) {
                            onResult(null, "Invalid server response")
                        }
                    }
                }
            })

        } catch (e: Exception) {
            onResult(null, e.message ?: "Request error")
        }
    }
}
