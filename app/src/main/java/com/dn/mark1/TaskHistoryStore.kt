
package com.dn.mark1

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TaskHistoryEntry(
    val goal: String,
    val status: String,
    val completedSteps: Int,
    val totalSteps: Int,
    val result: String,
    val timestamp: Long
)

data class RetryTaskData(
    val goal: String,
    val actions: List<AgentStep>
)

object TaskHistoryStore {

    private const val PREFS = "dn_mark1_task_history"
    private const val KEY = "task_records"
    private const val MAX_RECORDS = 50

    @Synchronized
    fun save(context: Context, task: AgentTaskSnapshot) {
        val records = getAll(context).toMutableList()

        records.add(
            0,
            TaskHistoryEntry(
                goal = task.goal,
                status = task.status,
                completedSteps = task.completedSteps,
                totalSteps = task.totalSteps,
                result = task.result.take(2000),
                timestamp = System.currentTimeMillis()
            )
        )

        val array = JSONArray()

        records.take(MAX_RECORDS).forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("goal", entry.goal)
                    put("status", entry.status)
                    put("completedSteps", entry.completedSteps)
                    put("totalSteps", entry.totalSteps)
                    put("result", entry.result)
                    put("timestamp", entry.timestamp)
                }
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    fun getAll(context: Context): List<TaskHistoryEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"

        return try {
            val array = JSONArray(raw)

            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null

                TaskHistoryEntry(
                    goal = item.optString("goal"),
                    status = item.optString("status"),
                    completedSteps = item.optInt("completedSteps"),
                    totalSteps = item.optInt("totalSteps"),
                    result = item.optString("result"),
                    timestamp = item.optLong("timestamp")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
    @Synchronized
    fun saveRetry(
        context: Context,
        goal: String,
        actions: List<AgentStep>
    ) {
        val array = JSONArray()

        actions.forEach { step ->
            array.put(
                JSONObject().apply {
                    put("action", step.action)
                    put("target", step.target)
                    put("description", step.description)
                }
            )
        }

        val data = JSONObject().apply {
            put("goal", goal)
            put("actions", array)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("retry_task_data", data.toString())
            .apply()
    }

    fun loadRetry(context: Context): RetryTaskData? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("retry_task_data", null) ?: return null

        return try {
            val data = JSONObject(raw)
            val array = data.optJSONArray("actions") ?: JSONArray()
            val actions = mutableListOf<AgentStep>()

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue

                actions.add(
                    AgentStep(
                        action = item.optString("action"),
                        target = item.optString("target"),
                        description = item.optString("description")
                    )
                )
            }

            if (actions.isEmpty()) null
            else RetryTaskData(
                goal = data.optString("goal"),
                actions = actions
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearRetry(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("retry_task_data")
            .apply()
    }
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }
}
