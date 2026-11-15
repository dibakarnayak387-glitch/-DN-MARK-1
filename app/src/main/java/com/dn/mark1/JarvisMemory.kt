
package com.dn.mark1

import android.content.Context
import org.json.JSONArray

object JarvisMemory {

    private const val PREFS = "dn_mark1_memory"
    private const val KEY = "saved_memories"

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, memory: String): Boolean {
        val clean = memory.trim()

        if (clean.isBlank() || clean.length > 300) return false

        val lower = clean.lowercase()

        val sensitiveWords = listOf(
            "password", "पासवर्ड", "otp",
            "api key", "secret key",
            "bank account", "cvv"
        )

        if (sensitiveWords.any { lower.contains(it) }) {
            return false
        }

        val prefs = preferences(context)
        val memories = getAll(context).toMutableList()

        if (memories.any { it.equals(clean, ignoreCase = true) }) {
            return true
        }

        if (memories.size >= 100) return false

        memories.add(clean)

        val array = JSONArray()
        memories.forEach { array.put(it) }

        return prefs.edit()
            .putString(KEY, array.toString())
            .commit()
    }

    fun getAll(context: Context): List<String> {
        val raw = preferences(context).getString(KEY, "[]")
            ?: "[]"

        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun forgetAll(context: Context): Boolean {
        return preferences(context).edit()
            .remove(KEY)
            .commit()
    }
}
