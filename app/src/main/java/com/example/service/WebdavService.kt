package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.server.WebdavServer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WebdavService : Service() {
    private val TAG = "WebdavService"
    private var webdavServer: WebdavServer? = null

    companion object {
        private val _isServerRunning = MutableStateFlow(false)
        val isServerRunning = _isServerRunning.asStateFlow()

        private val _serverIp = MutableStateFlow("")
        val serverIp = _serverIp.asStateFlow()

        private val _serverPort = MutableStateFlow(8080)
        val serverPort = _serverPort.asStateFlow()

        private val _serverUsername = MutableStateFlow("admin")
        val serverUsername = _serverUsername.asStateFlow()

        private val _serverToken = MutableStateFlow("")
        val serverToken = _serverToken.asStateFlow()

        const val ACTION_START = "com.example.service.START"
        const val ACTION_STOP = "com.example.service.STOP"

        const val EXTRA_PORT = "extra_port"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_TOKEN = "extra_token"
        const val EXTRA_BIND_IP = "extra_bind_ip"

        const val CHANNEL_ID = "usb_direct_share_channel"
        const val NOTIFICATION_ID = 2026
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_STOP
        Log.i(TAG, "onStartCommand with action: $action")

        if (action == ACTION_START) {
            val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
            val username = intent?.getStringExtra(EXTRA_USERNAME) ?: "admin"
            val token = intent?.getStringExtra(EXTRA_TOKEN) ?: "123456"
            val bindIp = intent?.getStringExtra(EXTRA_BIND_IP) ?: ""

            // Update companion state flows
            _serverPort.value = port
            _serverUsername.value = username
            _serverToken.value = token
            _serverIp.value = bindIp

            startServerInForeground(bindIp, port, username, token)
        } else {
            stopServer()
        }

        return START_NOT_STICKY
    }

    private fun startServerInForeground(bindIp: String, port: Int, username: String, token: String) {
        // Resolve storage root
        val root = android.os.Environment.getExternalStorageDirectory()
        val safeRoot = if (root != null && root.exists()) root else getExternalFilesDir(null) ?: filesDir

        webdavServer = WebdavServer(
            rootDir = safeRoot,
            port = port,
            username = username,
            token = token,
            bindIp = bindIp.ifEmpty { null }
        )
        webdavServer?.start()
        _isServerRunning.value = true

        val ipDisplay = if (bindIp.isEmpty()) "0.0.0.0" else bindIp
        val contentText = "Server running on http://$ipDisplay:$port | User: $username"

        val notification = createNotification(contentText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopServer() {
        webdavServer?.stop()
        webdavServer = null
        _isServerRunning.value = false
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USB Direct Share Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload) // standard system icon for utility
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "USB Direct Share Server Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows service notification when file sharing is running over USB."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webdavServer?.stop()
        _isServerRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
