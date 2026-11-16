
package com.dn.mark1

object AgentPlanEngine {

    private val allowedActions = setOf(
        "answer_only",
        "open_app",
        "youtube_search",
        "web_search",
        "draft_message",
        "share_text",
        "battery_status",
        "weather",
        "unsupported"
    )

    private val targetRequiredActions = setOf(
        "open_app",
        "youtube_search",
        "web_search",
        "draft_message",
        "share_text",
        "weather"
    )

    fun normalize(
        goal: String,
        inputSteps: List<AgentStep>,
        confirmationRequired: Boolean,
        confirmationReason: String
    ): AgentPlan {

        val cleanSteps = mutableListOf<AgentStep>()
        val seen = mutableSetOf<String>()

        inputSteps.take(5).forEach { original ->

            val action = original.action
                .trim()
                .lowercase()

            val safeAction = if (
                action in allowedActions
            ) action else "unsupported"

            val target = original.target
                .trim()
                .take(300)

            val description = original.description
                .trim()
                .take(200)
                .ifBlank { safeAction }

            val finalAction = if (
                safeAction in targetRequiredActions &&
                target.isBlank()
            ) {
                "unsupported"
            } else {
                safeAction
            }

            val finalTarget = if (
                finalAction == "unsupported"
            ) "" else target

            val uniqueKey =
                "$finalAction:${finalTarget.lowercase()}"

            if (seen.add(uniqueKey)) {
                cleanSteps.add(
                    AgentStep(
                        action = finalAction,
                        target = finalTarget,
                        description = description
                    )
                )
            }
        }

        if (cleanSteps.isEmpty()) {
            cleanSteps.add(
                AgentStep(
                    action = "answer_only",
                    target = "",
                    description = "सामान्य जवाब"
                )
            )
        }

        val executableCount = cleanSteps.count {
            it.action != "answer_only" &&
            it.action != "unsupported"
        }

        val sensitiveAction = cleanSteps.any {
            it.action == "draft_message" ||
            it.action == "share_text"
        }

        val needsConfirmation =
            confirmationRequired ||
            executableCount > 1 ||
            sensitiveAction

        return AgentPlan(
            goal = goal.trim().take(300),
            steps = cleanSteps,
            requiresConfirmation = needsConfirmation,
            confirmationReason = if (needsConfirmation) {
                confirmationReason.trim()
                    .take(300)
                    .ifBlank {
                        "काम शुरू करने से पहले आपकी पुष्टि जरूरी है।"
                    }
            } else {
                ""
            }
        )
    }
}
