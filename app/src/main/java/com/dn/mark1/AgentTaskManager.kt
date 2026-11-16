package com.dn.mark1

import java.util.UUID

data class AgentTaskSnapshot(
    val id: String,
    val goal: String,
    val totalSteps: Int,
    val completedSteps: Int,
    val currentStep: String,
    val status: String,
    val result: String
)

object AgentTaskManager {

    private var task: AgentTaskSnapshot? = null

    @Synchronized
    fun start(goal: String, totalSteps: Int): String {
        val id = UUID.randomUUID().toString()

        task = AgentTaskSnapshot(
            id = id,
            goal = goal,
            totalSteps = totalSteps.coerceAtLeast(0),
            completedSteps = 0,
            currentStep = "काम शुरू हो रहा है",
            status = "RUNNING",
            result = ""
        )

        return id
    }

    @Synchronized
    fun update(id: String, index: Int, description: String) {
        val old = task ?: return
        if (old.id != id || old.status != "RUNNING") return

        task = old.copy(
            completedSteps = index.coerceIn(0, old.totalSteps),
            currentStep = description
        )
    }

    @Synchronized
    fun complete(id: String, result: String): Boolean {
        return finish(
            id,
            task?.totalSteps ?: 0,
            result
        )
    }

    @Synchronized
    fun finish(
        id: String,
        completedSteps: Int,
        result: String
    ): Boolean {
        val old = task ?: return false
        if (old.id != id || old.status != "RUNNING") return false

        val completed = completedSteps.coerceIn(0, old.totalSteps)

        val finalStatus = when {
            completed >= old.totalSteps -> "COMPLETED"
            completed > 0 -> "PARTIAL"
            else -> "FAILED"
        }

        task = old.copy(
            completedSteps = completed,
            currentStep = when (finalStatus) {
                "COMPLETED" -> "सभी काम पूरे हुए"
                "PARTIAL" -> "कुछ काम पूरे हुए"
                else -> "काम पूरा नहीं हो पाया"
            },
            status = finalStatus,
            result = result
        )

        return true
    }

    @Synchronized
    fun fail(id: String, reason: String) {
        val old = task ?: return
        if (old.id != id || old.status != "RUNNING") return

        task = old.copy(
            currentStep = "काम असफल हुआ",
            status = "FAILED",
            result = reason
        )
    }

    @Synchronized
    fun cancelCurrent(
        reason: String = "उपयोगकर्ता ने Task Cancel किया।"
    ): Boolean {
        val old = task ?: return false
        if (old.status != "RUNNING") return false

        task = old.copy(
            currentStep = "काम रद्द कर दिया गया",
            status = "CANCELLED",
            result = reason
        )

        return true
    }

    @Synchronized
    fun isCancelled(id: String): Boolean {
        val current = task ?: return true
        return current.id != id || current.status != "RUNNING"
    }

    @Synchronized
    fun snapshot(): AgentTaskSnapshot? = task
}
