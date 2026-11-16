
package com.dn.mark1

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AndroidActionExecutor {

    fun searchYouTube(context: Context, query: String): Boolean {
        if (query.isBlank()) return false

        return try {
            val uri = Uri.parse("https://www.youtube.com/results")
                .buildUpon()
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
    fun searchWeb(context: Context, query: String): Boolean {
    if (query.isBlank()) return false

    return try {
        val uri = Uri.parse("https://www.google.com/search")
            .buildUpon()
            .appendQueryParameter("q", query)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
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
            val manager = context.getSystemService(
                Context.BATTERY_SERVICE
            ) as BatteryManager

            val level = manager.getIntProperty(
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

    fun getWeather(city: String, callback: (String) -> Unit) {
        Thread {
            val result = try {
                if (city.isBlank()) {
                    throw IllegalArgumentException("शहर का नाम नहीं मिला।")
                }

                val encodedCity = URLEncoder.encode(city, "UTF-8")
                val geoUrl = URL(
                    "https://geocoding-api.open-meteo.com/v1/search" +
                    "?name=$encodedCity&count=1&language=hi&format=json"
                )

                val geo = readJson(geoUrl)
                val places = geo.optJSONArray("results")
                    ?: throw Exception("शहर नहीं मिला।")

                if (places.length() == 0) {
                    throw Exception("इस शहर की जानकारी नहीं मिली।")
                }

                val place = places.getJSONObject(0)
                val latitude = place.getDouble("latitude")
                val longitude = place.getDouble("longitude")
                val placeName = place.optString("name", city)
                val country = place.optString("country", "")

                val weatherUrl = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,relative_humidity_2m," +
                    "apparent_temperature,weather_code,wind_speed_10m" +
                    "&timezone=auto"
                )

                val weather = readJson(weatherUrl)
                val current = weather.getJSONObject("current")

                val temperature = current.optDouble("temperature_2m")
                val feelsLike = current.optDouble("apparent_temperature")
                val humidity = current.optInt("relative_humidity_2m")
                val wind = current.optDouble("wind_speed_10m")
                val code = current.optInt("weather_code")

                val condition = weatherDescription(code)

                "मौसम रिपोर्ट $placeName, $country। " +
                "तापमान ${temperature} डिग्री सेल्सियस है। " +
                "$condition। " +
                "महसूस होने वाला तापमान ${feelsLike} डिग्री है। " +
                "नमी $humidity प्रतिशत और हवा की गति $wind किलोमीटर प्रति घंटा है।"

            } catch (e: Exception) {
                "मौसम की जानकारी नहीं मिल पाई। ${e.message ?: ""}"
            }

            callback(result)
        }.start()
    }

    private fun readJson(url: URL): JSONObject {
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode !in 200..299) {
                throw Exception("Weather server error: ${connection.responseCode}")
            }

            return JSONObject(
                connection.inputStream.bufferedReader().use {
                    it.readText()
                }
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun weatherDescription(code: Int): String {
        return when (code) {
            0 -> "आसमान साफ है"
            1, 2 -> "आंशिक बादल हैं"
            3 -> "आसमान में बादल हैं"
            45, 48 -> "कोहरा है"
            51, 53, 55 -> "हल्की बूंदाबांदी है"
            61, 63, 65 -> "बारिश हो रही है"
            71, 73, 75 -> "बर्फबारी हो रही है"
            80, 81, 82 -> "बारिश की बौछारें हैं"
            95, 96, 99 -> "गरज के साथ तूफान है"
            else -> "मौसम की स्थिति उपलब्ध है"
        }
    }
}
