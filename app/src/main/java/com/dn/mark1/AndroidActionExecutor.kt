
package com.dn.mark1

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager

object AndroidActionExecutor {

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

    fun shareText(context: Context, message: String): Boolean {
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

    fun getBatteryStatus(context: Context): String {
        return try {
            val batteryManager = context.getSystemService(
                Context.BATTERY_SERVICE
            ) as BatteryManager

            val level = batteryManager.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            )

            if (level in 0..100) {
                "आपके फोन की बैटरी $level प्रतिशत है।"
            } else {
                "बैटरी की जानकारी उपलब्ध नहीं है।"
            }
        } catch (_: Exception) {
            "बैटरी की जानकारी नहीं मिल पाई।"
        }
    }
}
