
package com.dn.mark1

import android.content.Context
import android.content.Intent
import android.net.Uri

object AndroidActionExecutor {

    // YouTube search
    fun searchYouTube(context: Context, query: String): Boolean {
        if (query.isBlank()) return false

        return try {
            val uri = Uri.parse(
                "https://www.youtube.com/results"
            ).buildUpon()
                .appendQueryParameter("search_query", query)
                .build()

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // Open Android share screen with text
    fun shareText(context: Context, message: String): Boolean {
        "share_text" -> {
    val success = AndroidActionExecutor.shareText(
        this,
        action.target
    )

    speakAndResume(
        if (success) {
            "शेयर करने का विकल्प खुल गया है। अब ऐप और प्राप्तकर्ता चुनें।"
        } else {
            "शेयर नहीं हो पाया।"
        }
    )
    return
}
        if (message.isBlank()) return false

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(
                Intent.createChooser(intent, "Share using").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
