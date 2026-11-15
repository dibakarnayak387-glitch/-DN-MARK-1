
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
import java.util.concurrent.TimeUnit

data class GeminiMessage(
    val role: String,
    val content: String
)

data class AgentStep(
    val action: String,
    val target: String,
    val description: String
)

data class AgentPlan(
    val goal: String,
    val steps: List<AgentStep>,
    val requiresConfirmation: Boolean,
    val confirmationReason: String
)

data class GeminiResult(
    val reply: String,
    val agentPlan: AgentPlan,
    val provider: String
)

object GeminiClient {

    private const val ENDPOINT =
        "https://dn-mark-1.netlify.app/.netlify/functions/chat"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS)
        .build()

    // पुराने interface को सुरक्षित रखा गया है।
    fun send(
        messages: List<GeminiMessage>,
        onResult: (String?, String?) -> Unit
    ) {
        sendAgent(messages) { result, error ->
            onResult(result?.reply, error)
        }
    }

    // पुराने Agent interface को भी सुरक्षित रखा गया है।
    fun sendAgent(
        messages: List<GeminiMessage>,
        onResult: (GeminiResult?, String?) -> Unit
    ) {
        sendAgent(messages, emptyList(), onResult)
    }

    // नया interface: AI को saved memories भेजने के लिए।
    fun sendAgent(
        messages: List<GeminiMessage>,
        memories: List<String>,
        onResult: (GeminiResult?, String?) -> Unit
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

            val memoryArray = JSONArray()

            memories.take(100).forEach { memory ->
                if (memory.isNotBlank()) {
                    memoryArray.put(memory.take(300))
                }
            }

            val json = JSONObject()
                .put("messages", messageArray)
                .put("memories", memoryArray)
                .toString()

            val body = json.toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    onResult(null, e.message ?: "Network error")
                }

                override fun onResponse(
                    call: Call,
                    response: okhttp3.Response
                ) {
                    response.use {
                        val responseText = it.body?.string().orEmpty()

                        if (!it.isSuccessful) {
                            onResult(
                                null,
                                "Server error ${it.code}: $responseText"
                            )
                            return
                        }

                        try {
                            val jsonResponse = JSONObject(responseText)
                            val reply = jsonResponse.optString("reply")

                            if (reply.isBlank()) {
                                onResult(null, "Empty AI response")
                                return
                            }

                            // Nested और पुराने flat दोनों formats पढ़ें।
                            val planJson =
                                jsonResponse.optJSONObject("agentPlan")
                                    ?: jsonResponse

                            val stepsJson =
                                planJson.optJSONArray("steps")
                                    ?: JSONArray()

                            val steps = mutableListOf<AgentStep>()

                            for (i in 0 until minOf(stepsJson.length(), 5)) {
                                val step = stepsJson.optJSONObject(i)
                                    ?: continue

                                val action = step.optString(
                                    "action",
                                    "unsupported"
                                )

                                val safeAction = if (
    action in listOf(
        "answer_only",
        "open_app",
        "youtube_search",
        "draft_message",
        "share_text",
        "battery_status",
        "unsupported"
    )
) action else "unsupported"
                                steps.add(
                                    AgentStep(
                                        action = safeAction,
                                        target = step.optString("target"),
                                        description = step.optString("description")
                                    )
                                )
                            }

                            val plan = AgentPlan(
                                goal = planJson.optString("goal"),
                                steps = steps,
                                requiresConfirmation =
                                    planJson.optBoolean("requiresConfirmation"),
                                confirmationReason =
                                    planJson.optString("confirmationReason")
                            )

                            onResult(
                                GeminiResult(
                                    reply = reply,
                                    agentPlan = plan,
                                    provider = jsonResponse.optString("provider")
                                ),
                                null
                            )

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
