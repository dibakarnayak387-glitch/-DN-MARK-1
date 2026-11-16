package com.dn.mark1

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ConversationStore {

    private const val PREFS = "dn_mark1_conversation"
    private const val KEY = "conversation_history"
    private const val MAX_MESSAGES = 12
    private const val MAX_LENGTH = 3000

    fun save(context: Context, messages: List<GeminiMessage>): Boolean {
        return try {
            val array = JSONArray()

            messages.takeLast(MAX_MESSAGES).forEach { message ->
                if (
                    message.role in listOf("user", "assistant") &&
                    message.content.isNotBlank()
                ) {
                    array.put(
                        JSONObject()
                            .put("role", message.role)
                            .put("content", message.content.take(MAX_LENGTH))
                    )
                }
            }

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, array.toString())
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    fun load(context: Context): List<GeminiMessage> {
        return try {
            val raw = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            ).getString(KEY, "[]") ?: "[]"

            val array = JSONArray(raw)
            val messages = mutableListOf<GeminiMessage>()

            for (i in 0 until minOf(array.length(), MAX_MESSAGES)) {
                val item = array.optJSONObject(i) ?: continue
                val role = item.optString("role")
                val content = item.optString("content")

                if (
                    role in listOf("user", "assistant") &&
                    content.isNotBlank()
                ) {
                    messages.add(
                        GeminiMessage(role, content.take(MAX_LENGTH))
                    )
                }
            }

            messages
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .commit()
    }
}
