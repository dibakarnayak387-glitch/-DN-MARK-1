
package com.dn.mark1

import android.content.Context
import org.json.JSONArray

object JarvisMemory {

    private const val PREFS = "dn_mark1_memory"
    private const val KEY = "saved_memories"
    private const val MAX_MEMORIES = 100
    private const val MAX_LENGTH = 300

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun isSensitive(value: String): Boolean {
        val lower = value.lowercase()

        val blockedTerms = listOf(
            "password", "पासवर्ड",
            "otp",
            "api key", "secret key",
            "bank account", "bank details",
            "cvv", "credit card number",
            "debit card number",
            "private key",
            "पासवर्ड नंबर"
        )

        return blockedTerms.any { lower.contains(it) }
    }

    fun save(context: Context, memory: String): Boolean {
        val clean = memory.trim()

        if (clean.isBlank() || clean.length > MAX_LENGTH) {
            return false
        }

        if (isSensitive(clean)) return false

        val memories = getAll(context).toMutableList()

        if (memories.any { it.equals(clean, ignoreCase = true) }) {
            return true
        }

        if (memories.size >= MAX_MEMORIES) return false

        memories.add(clean)
        return writeAll(context, memories)
    }

    fun getAll(context: Context): List<String> {
        val raw = preferences(context)
            .getString(KEY, "[]") ?: "[]"

        return try {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { index ->
                    array.optString(index)
                        .takeIf { it.isNotBlank() }
                }
                .take(MAX_MEMORIES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun forgetMatching(
        context: Context,
        searchText: String
    ): Boolean {
        val query = searchText.trim()

        if (query.isBlank()) return false

        val memories = getAll(context).toMutableList()
        val oldSize = memories.size

        memories.removeAll {
            it.contains(query, ignoreCase = true)
        }

        if (memories.size == oldSize) return false

        return writeAll(context, memories)
    }

    fun forgetAll(context: Context): Boolean {
        return preferences(context).edit()
            .remove(KEY)
            .commit()
    }

    private fun writeAll(
        context: Context,
        memories: List<String>
    ): Boolean {
        val array = JSONArray()

        memories.take(MAX_MEMORIES).forEach {
            array.put(it)
        }

        return preferences(context).edit()
            .putString(KEY, array.toString())
            .commit()
    }
}
