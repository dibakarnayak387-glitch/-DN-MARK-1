
package com.dn.mark1

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

object ModelDownloader {

    private const val MODEL_NAME = "vosk-model-small-hi-0.22"

    private const val MODEL_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"

    fun download(
        context: Context,
        onProgress: (Int) -> Unit,
        onComplete: (Result<File>) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())

        Thread {
            try {
                val modelsDir = File(context.filesDir, "models")
                modelsDir.mkdirs()

                val modelDir = File(modelsDir, MODEL_NAME)
                val modelFile = File(modelDir, "am/final.mdl")

                if (modelFile.exists()) {
                    mainHandler.post {
                        onComplete(Result.success(modelDir))
                    }
                    return@Thread
                }

                val zipFile = File(context.cacheDir, "hindi-model.zip")

                val connection = URL(MODEL_URL).openConnection()
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.connect()

                val total = connection.contentLength

                BufferedInputStream(connection.getInputStream()).use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0
                        var count: Int

                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                            downloaded += count

                            if (total > 0) {
                                val progress = downloaded * 100 / total
                                mainHandler.post {
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }

                ZipInputStream(
                    BufferedInputStream(zipFile.inputStream())
                ).use { zip ->

                    val buffer = ByteArray(8192)
                    var entry = zip.nextEntry

                    while (entry != null) {
                        val outputFile = File(modelsDir, entry.name)

                        if (!outputFile.canonicalPath.startsWith(
                                modelsDir.canonicalPath + File.separator
                            )
                        ) {
                            throw Exception("Invalid model archive")
                        }

                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()

                            BufferedOutputStream(
                                FileOutputStream(outputFile)
                            ).use { output ->
                                var count: Int
                                while (zip.read(buffer).also {
                                        count = it
                                    } != -1
                                ) {
                                    output.write(buffer, 0, count)
                                }
                            }
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                zipFile.delete()

                if (!modelFile.exists()) {
                    throw Exception("Hindi model extraction failed")
                }

                mainHandler.post {
                    onComplete(Result.success(modelDir))
                }

            } catch (e: Exception) {
                mainHandler.post {
                    onComplete(Result.failure(e))
                }
            }
        }.start()
    }
}
