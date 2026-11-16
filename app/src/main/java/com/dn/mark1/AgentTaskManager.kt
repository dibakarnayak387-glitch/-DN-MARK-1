
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
            totalSteps = totalSteps,
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
            completedSteps = index,
            currentStep = description
        )
    }

    @Synchronized
    fun complete(id: String, result: String) {
        val old = task ?: return
        if (old.id != id) return

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
        if (old.id != id) return

        task = old.copy(
            status = "FAILED",
            result = reason
        )
    }

    @Synchronized
    fun snapshot(): AgentTaskSnapshot? = task
}
