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
            id, goal, totalSteps, 0,
            "काम शुरू हो रहा है", "RUNNING", ""
        )
        return id
    }

    @Synchronized
    fun update(id: String, index: Int, description: String) {
        val old = task ?: return
        if (old.id != id || old.status != "RUNNING") return
        task = old.copy(
            completedSteps = index,
            currentStep = description
        )
    }

    @Synchronized
    fun complete(id: String, result: String) {
        val old = task ?: return
        if (old.id != id || old.status != "RUNNING") return
        task = old.copy(
            completedSteps = old.totalSteps,
            currentStep = "सभी काम पूरे हुए",
            status = "COMPLETED",
            result = result
        )
    }

    @Synchronized
    fun fail(id: String, reason: String) {
        val old = task ?: return
        if (old.id != id || old.status != "RUNNING") return
        task = old.copy(status = "FAILED", result = reason)
    }

    @Synchronized
    fun cancelCurrent(): Boolean {
        val old = task ?: return false
        if (old.status != "RUNNING") return false
        task = old.copy(
            status = "CANCELLED",
            currentStep = "काम रद्द कर दिया गया",
            result = "उपयोगकर्ता ने Task Cancel किया।"
        )
        return true
    }

    @Synchronized
    fun isCancelled(id: String): Boolean {
        val current = task ?: return true
        return current.id != id || current.status == "CANCELLED"
    }

    @Synchronized
    fun snapshot(): AgentTaskSnapshot? = task
}
