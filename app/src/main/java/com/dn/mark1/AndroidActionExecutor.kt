
package com.dn.mark1

import android.content.Context
import android.content.Intent
import android.net.Uri

object AndroidActionExecutor {

    fun searchYouTube(context: Context, query: String): Boolean {
        if (query.isBlank()) return false

        return try {
            val uri = Uri.parse(
                "https://www.youtube.com/results"
            ).buildUpon()
                .appendQueryParameter("search_query", query)
                .build()

            val youtubeIntent = Intent(
                Intent.ACTION_VIEW,
                uri
            ).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (youtubeIntent.resolveActivity(
                    context.packageManager
                ) != null
            ) {
                context.startActivity(youtubeIntent)
            } else {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    uri
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }

            true
        } catch (_: Exception) {
            false
        }
    }
}
