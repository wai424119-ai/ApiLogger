package com.example.apilogger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.Locale

class ApiLoggerService : Service() {

    private val binder = LocalBinder()

    private var server: EmbeddedServer? = null

    var isRunning = false
        private set

    inner class LocalBinder : Binder() {
        fun getService(): ApiLoggerService {
            return this@ApiLoggerService
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val config = LogManager.getConfig(this)

        startForegroundServiceWithNotification()

        Thread {
            try {
                server?.stop()

                server = EmbeddedServer(
                    config.port,
                    config.endpoint,
                    applicationContext
                )

                server?.start(
                    NanoHTTPD.SOCKET_READ_TIMEOUT,
                    false
                )

                isRunning = true

                LogManager.appendSystemLog(
                    this,
                    "Server started on Port ${config.port}, Endpoint ${config.endpoint}"
                )

            } catch (e: Exception) {

                isRunning = false

                LogManager.appendSystemLog(
                    this,
                    "Server error: ${e.message}"
                )
            }
        }.start()

        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {

        val channelId = "api_logger_channel"

        val manager =
            getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                channelId,
                "API Logger Service",
                NotificationManager.IMPORTANCE_LOW
            )

            manager.createNotificationChannel(channel)
        }

        val notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("API Logger Active")
                .setContentText(
                    "Listening for incoming API requests..."
                )
                .setSmallIcon(
                    android.R.drawable.stat_notify_sync
                )
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                101,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )

        } else {

            startForeground(
                101,
                notification
            )
        }
    }

    override fun onDestroy() {

        try {
            server?.stop()
        } catch (_: Exception) {
        }

        server = null
        isRunning = false

        LogManager.appendSystemLog(
            this,
            "Server stopped."
        )

        super.onDestroy()
    }
}

class EmbeddedServer(
    port: Int,
    private val targetEndpoint: String,
    private val context: Context
) : NanoHTTPD("0.0.0.0", port) {

    private val fileLock = Any()

    override fun serve(
        session: IHTTPSession
    ): Response {

        val uri =
            session.uri.removeSuffix("/")

        val target =
            targetEndpoint.removeSuffix("/")

        if (
            session.method == Method.POST &&
            uri.equals(target, ignoreCase = true)
        ) {

            return try {

                val files =
                    HashMap<String, String>()

                session.parseBody(files)

                val postData =
                    files["postData"] ?: ""

                val rowValues =
                    mutableListOf<String>()

                val timeStr =
                    SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.getDefault()
                    ).format(Date())

                rowValues.add(timeStr)

                val trimmed =
                    postData.trim()

                if (trimmed.startsWith("{")) {

                    val jsonObj =
                        JSONObject(postData)

                    val keys =
                        jsonObj.keys()

                    while (keys.hasNext()) {

                        val key = keys.next()

                        rowValues.add(
                            jsonObj.get(key).toString()
                        )
                    }

                } else if (trimmed.startsWith("[")) {

                    val jsonArray =
                        JSONArray(postData)

                    for (
                        i in 0 until jsonArray.length()
                    ) {

                        rowValues.add(
                            jsonArray.get(i).toString()
                        )
                    }

                } else {

                    rowValues.add(postData)
                }

                synchronized(fileLock) {

                    val today =
                        SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                        ).format(Date())

                    val dir =
                        LogManager.getAppStorageDir(
                            context
                        )

                    val csvFile =
                        File(
                            dir,
                            "sales_$today.csv"
                        )

                    val formattedRow =
                        rowValues.joinToString(",") { value ->

                            val escaped =
                                value
                                    .replace(
                                        "\"",
                                        "\"\""
                                    )
                                    .replace(
                                        "\n",
                                        " "
                                    )
                                    .replace(
                                        "\r",
                                        ""
                                    )

                            "\"$escaped\""
                        } + "\n"

                    csvFile.appendText(
                        formattedRow,
                        Charsets.UTF_8
                    )
                }

                LogManager.appendSystemLog(
                    context,
                    "Logged request -> $rowValues"
                )

                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    """{"status":"done"}"""
                )

            } catch (e: Exception) {

                LogManager.appendSystemLog(
                    context,
                    "Error processing payload: ${e.message}"
                )

                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "text/plain",
                    e.message ?: "Unknown error"
                )
            }
        }

        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            "text/plain",
            "Endpoint Not Matched"
        )
    }
}
