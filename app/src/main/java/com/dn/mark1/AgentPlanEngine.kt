
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
    
    /**
     * Deterministic coordinator for common mixed device requests.
     */
    fun buildKnownMultiStepPlan(raw: String): AgentPlan? {
        val lower = raw.lowercase()

        fun firstMatch(
            text: String,
            terms: List<String>
        ): Pair<Int, String>? {
            return terms.mapNotNull { term ->
                val index = text.indexOf(term)
                if (index >= 0) index to term else null
            }.minByOrNull { it.first }
        }

        val batteryMatch = firstMatch(
            lower,
            listOf(
                "battery status",
                "battery level",
                "battery percentage",
                "battery",
                "बैटरी स्टेटस",
                "बैटरी",
                "चार्ज कितना",
                "चार्ज कितनी"
            )
        ) ?: return null

        val youtubeMatch = firstMatch(
            lower,
            listOf("youtube", "यूट्यूब")
        ) ?: return null

        val searchTerms = listOf(
            "search", "सर्च", "खोज", "ढूंढ", "find"
        )

        val afterYoutubeStart =
            youtubeMatch.first + youtubeMatch.second.length

        val afterYoutube = lower.substring(afterYoutubeStart)
        val searchAfterYoutube = firstMatch(
            afterYoutube,
            searchTerms
        )

        var query = if (searchAfterYoutube != null) {
            raw.substring(
                afterYoutubeStart,
                afterYoutubeStart + searchAfterYoutube.first
            )
        } else {
            val searchBeforeYoutube = firstMatch(
                lower.substring(0, youtubeMatch.first),
                searchTerms
            ) ?: return null

            val queryStart =
                searchBeforeYoutube.first + searchBeforeYoutube.second.length

            raw.substring(queryStart, youtubeMatch.first)
                .replace(
                    Regex("(?i)\\s+(on|पर|में|pe|par)\\s*$"),
                    ""
                )
        }

        // "YouTube search Iron Man technology" भी समझे।
        if (query.isBlank() && searchAfterYoutube != null) {
            val afterSearchStart =
                afterYoutubeStart +
                searchAfterYoutube.first +
                searchAfterYoutube.second.length

            val remainder = raw.substring(afterSearchStart)
            val lowerRemainder = lower.substring(afterSearchStart)

            val stopWords = listOf(
                " और ", " फिर ", " and ", " then ",
                " अंत में ", " उसके बाद "
            )

            val stopIndex = stopWords
                .map { lowerRemainder.indexOf(it) }
                .filter { it >= 0 }
                .minOrNull() ?: remainder.length

            query = remainder.substring(0, stopIndex)
                .replace(
                    Regex("(?i)\\s+(please|karo|kar do|do|करो|करें)$"),
                    ""
                )
        }

        query = query
            .replace(
                Regex("(?i)^\\s*(पर|में|pe|par|me|on|for)\\s+"),
                ""
            )
            .trim()
            .trim(',', '.', '?', '!', '।')

        if (query.isBlank()) return null

        val batteryStep = AgentStep(
            action = "battery_status",
            target = "",
            description = "फोन की Battery Status बताना"
        )

        val youtubeStep = AgentStep(
            action = "youtube_search",
            target = query,
            description = "YouTube पर $query खोजना"
        )

        val orderedSteps = if (
            batteryMatch.first < youtubeMatch.first
        ) {
            listOf(batteryStep, youtubeStep)
        } else {
            listOf(youtubeStep, batteryStep)
        }

        return normalize(
            goal = raw.trim(),
            inputSteps = orderedSteps,
            confirmationRequired = true,
            confirmationReason = "दो काम क्रम से चलेंगे।"
        )
    }
    
}
