
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

            val safeAction = if (action in allowedActions) {
                action
            } else {
                "unsupported"
            }

            val target = original.target.trim().take(300)

            val description = original.description
                .trim()
                .take(200)
                .ifBlank { safeAction }

            val missingTarget =
                safeAction in targetRequiredActions &&
                target.isBlank()

            val finalAction = if (missingTarget) {
                "unsupported"
            } else {
                safeAction
            }

            val finalTarget = if (
                finalAction == "unsupported"
            ) "" else target

            val key = "$finalAction:${finalTarget.lowercase()}"

            if (seen.add(key)) {
                cleanSteps.add(
                    AgentStep(
                        action = finalAction,
                        target = finalTarget,
                        description = description
                    )
                )
            }
        }

        // Share/message action हमेशा सबसे आखिर में।
        val shareSteps = cleanSteps.filter {
            it.action == "share_text" ||
            it.action == "draft_message"
        }

        cleanSteps.removeAll {
            it.action == "share_text" ||
            it.action == "draft_message"
        }

        if (shareSteps.isNotEmpty()) {
            cleanSteps.add(shareSteps.first())

            // एक plan में कई share dialogs न खोलें।
            shareSteps.drop(1).forEach {
                cleanSteps.add(
                    AgentStep(
                        action = "unsupported",
                        target = "",
                        description =
                            "एक plan में अतिरिक्त message/share action समर्थित नहीं है।"
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
