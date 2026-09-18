package com.example.apilogger

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppConfig(
    var port: Int = 5000,
    var endpoint: String = "/api/sales"
)

object LogManager {

    private const val PREFS_NAME = "api_logger_prefs"
    private const val KEY_CONFIG = "config_json"

    fun saveConfig(context: Context, config: AppConfig) {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        prefs.edit()
            .putString(KEY_CONFIG, Gson().toJson(config))
            .apply()
    }

    fun getConfig(context: Context): AppConfig {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val json = prefs.getString(KEY_CONFIG, null)

        return if (json != null) {
            try {
                Gson().fromJson(json, AppConfig::class.java)
            } catch (_: Exception) {
                AppConfig()
            }
        } else {
            AppConfig()
        }
    }

    fun getAppStorageDir(context: Context): File {
        val dir = File(
            context.getExternalFilesDir(null),
            "SalesLogs"
        )

        if (!dir.exists()) {
            dir.mkdirs()
        }

        return dir
    }

    fun appendSystemLog(
        context: Context,
        message: String
    ) {
        val timeStr = SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        val logLine = "[$timeStr] $message\n"

        val logFile = File(
            getAppStorageDir(context),
            "system_logs.log"
        )

        synchronized(this) {
            logFile.appendText(
                logLine,
                Charsets.UTF_8
            )
        }
    }
}
