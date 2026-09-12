package com.proxyplatform.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import engine.Engine
import engine.Key
import java.util.concurrent.Executors

/** Routes device traffic through the configured HTTP or SOCKS5 upstream proxy. */
class ProxyVpnService : VpnService() {
    private val executor = Executors.newSingleThreadExecutor()
    private var tun: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("Proxy tunnel starting"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            return START_NOT_STICKY
        }
        if (tun != null) return START_STICKY

        val host = intent?.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        val port = intent?.getIntExtra(EXTRA_PORT, 0) ?: 0
        val protocol = intent?.getStringExtra(EXTRA_PROTOCOL).orEmpty().lowercase()
        val username = intent?.getStringExtra(EXTRA_USERNAME)?.trim().orEmpty()
        val password = intent?.getStringExtra(EXTRA_PASSWORD).orEmpty()
        if (host.isBlank() || port !in 1..65535 || protocol !in setOf("http", "socks5")) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            tun = Builder()
                .setSession("Proxy Platform")
                .setMtu(TUN_MTU)
                .addAddress(TUN_ADDRESS, 24)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("2606:4700:4700::1111")
                .addDisallowedApplication(packageName)
                .establish()

            val descriptor = tun ?: error("Could not establish the VPN interface")
            val proxy = buildProxyUrl(protocol, host, port, username, password)
            val key = Key().apply {
                mtu = TUN_MTU.toLong()
                mark = 0
                device = "fd://${descriptor.fd}"
                `interface` = ""
                logLevel = "error"
                this.proxy = proxy
                restAPI = ""
                tcpSendBufferSize = ""
                tcpReceiveBufferSize = ""
                tcpModerateReceiveBuffer = false
            }
            Engine.insert(key)
            executor.submit { Engine.start() }
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification("Proxy tunnel is active"))
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, true).apply()
        } catch (error: Exception) {
            stopTunnel()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification("Proxy tunnel failed: ${error.message ?: "unknown error"}"))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTunnel()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    private fun stopTunnel() {
        try { Engine.stop() } catch (_: Throwable) { }
        tun?.close()
        tun = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildProxyUrl(protocol: String, host: String, port: Int, username: String, password: String): String {
        val credentials = if (username.isNotBlank()) "${encode(username)}:${encode(password)}@" else ""
        return "$protocol://$credentials$host:$port"
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Proxy tunnel", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("Proxy Platform")
        .setContentText(text)
        .setOngoing(true)
        .build()

    companion object {
        const val ACTION_START = "com.proxyplatform.app.action.START_PROXY"
        const val ACTION_STOP = "com.proxyplatform.app.action.STOP_PROXY"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val PREFS = "proxy_vpn"
        const val KEY_RUNNING = "running"
        private const val CHANNEL_ID = "proxy_tunnel"
        private const val NOTIFICATION_ID = 7001
        private const val TUN_MTU = 1500
        private const val TUN_ADDRESS = "10.0.0.2"

        fun isRunning(context: android.content.Context): Boolean =
            context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_RUNNING, false)
    }
}
