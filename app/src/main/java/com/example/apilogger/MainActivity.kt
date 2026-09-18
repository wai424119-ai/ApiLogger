package com.example.apilogger

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.format.Formatter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var etPort: EditText
    private lateinit var etEndpoint: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvLogs: TextView

    private var loggerService: ApiLoggerService? = null
    private var isBound = false

    private val connection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?
            ) {

                val binder =
                    service as ApiLoggerService.LocalBinder

                loggerService =
                    binder.getService()

                isBound = true

                updateUiState()
            }

            override fun onServiceDisconnected(
                name: ComponentName?
            ) {

                isBound = false
                loggerService = null

                updateUiState()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        tvStatus =
            findViewById(R.id.tvStatus)

        tvIpAddress =
            findViewById(R.id.tvIpAddress)

        etPort =
            findViewById(R.id.etPort)

        etEndpoint =
            findViewById(R.id.etEndpoint)

        btnStart =
            findViewById(R.id.btnStart)

        btnStop =
            findViewById(R.id.btnStop)

        tvLogs =
            findViewById(R.id.tvLogs)

        requestNotificationPermission()

        val config =
            LogManager.getConfig(this)

        etPort.setText(
            config.port.toString()
        )

        etEndpoint.setText(
            config.endpoint
        )

        displayIpAddress()
        updateUiState()

        btnStart.setOnClickListener {

            val port =
                etPort.text
                    .toString()
                    .toIntOrNull()
                    ?: 5000

            val endpoint =
                etEndpoint.text
                    .toString()
                    .trim()
                    .let {
                        if (it.isBlank()) {
                            "/api/sales"
                        } else if (it.startsWith("/")) {
                            it
                        } else {
                            "/$it"
                        }
                    }

            if (port !in 1024..65535) {

                etPort.error =
                    "Port must be 1024-65535"

                return@setOnClickListener
            }

            val newConfig =
                AppConfig(
                    port = port,
                    endpoint = endpoint
                )

            LogManager.saveConfig(
                this,
                newConfig
            )

            val serviceIntent =
                Intent(
                    this,
                    ApiLoggerService::class.java
                )

            ContextCompat.startForegroundService(
                this,
                serviceIntent
            )

            bindService(
                serviceIntent,
                connection,
                Context.BIND_AUTO_CREATE
            )

            updateUiState()
        }

        btnStop.setOnClickListener {

            if (isBound) {

                try {
                    unbindService(connection)
                } catch (_: Exception) {
                }

                isBound = false
            }

            stopService(
                Intent(
                    this,
                    ApiLoggerService::class.java
                )
            )

            loggerService = null

            updateUiState()
        }
    }

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    1001
                )
            }
        }
    }

    private fun updateUiState() {

        val running =
            loggerService?.isRunning == true

        tvStatus.text =
            if (running) {
                "Status: RUNNING"
            } else {
                "Status: STOPPED"
            }

        tvStatus.setTextColor(
            if (running) {
                0xFF28A745.toInt()
            } else {
                0xFFDC3545.toInt()
            }
        )

        btnStart.isEnabled = !running
        btnStop.isEnabled = running

        refreshLogs()
    }

    private fun refreshLogs() {

        val logFile =
            File(
                LogManager.getAppStorageDir(this),
                "system_logs.log"
            )

        if (logFile.exists()) {

            try {

                val lines =
                    logFile
                        .readLines()
                        .takeLast(15)

                tvLogs.text =
                    lines.joinToString("\n")

            } catch (_: Exception) {
            }
        }
    }

    private fun displayIpAddress() {

        try {

            val wifiManager =
                applicationContext
                    .getSystemService(
                        Context.WIFI_SERVICE
                    ) as WifiManager

            val ip =
                Formatter.formatIpAddress(
                    wifiManager.connectionInfo.ipAddress
                )

            tvIpAddress.text =
                "Device Local IP: $ip"

        } catch (_: Exception) {

            tvIpAddress.text =
                "Device Local IP: unavailable"
        }
    }

    override fun onDestroy() {

        if (isBound) {

            try {
                unbindService(connection)
            } catch (_: Exception) {
            }

            isBound = false
        }

        super.onDestroy()
    }
}
