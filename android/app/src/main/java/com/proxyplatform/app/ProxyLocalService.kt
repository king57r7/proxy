package com.proxyplatform.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus

/**
 * Local proxy service that runs sing-box with a mixed (SOCKS + HTTP) inbound
 * on 127.0.0.1 instead of a TUN-based VPN. This avoids the system VPN dialog
 * and the persistent key icon entirely.
 *
 * The system-wide HTTP proxy is set via Shizuku shell permissions
 * (`settings put global http_proxy`) from the UI layer.
 */
class ProxyLocalService : Service(), CommandServerHandler {
    private var commandServer: CommandServer? = null
    private var setupReady = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        runCatching {
            startForegroundCompat("Preparing local proxy")
            setupLibbox()
            setupReady = true
        }.onFailure { error ->
            recordError("The native proxy engine could not start: ${error.message ?: "unknown error"}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel(clearError = true)
            return START_NOT_STICKY
        }
        if (!setupReady) {
            recordError("The native proxy engine is unavailable on this device.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (commandServer != null) return START_STICKY

        val host = intent?.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        val port = intent?.getIntExtra(EXTRA_PORT, 0) ?: 0
        val protocol = intent?.getStringExtra(EXTRA_PROTOCOL).orEmpty().lowercase()
        val username = intent?.getStringExtra(EXTRA_USERNAME)?.trim().orEmpty()
        val password = intent?.getStringExtra(EXTRA_PASSWORD).orEmpty()
        val localPort = intent?.getIntExtra(EXTRA_LOCAL_PORT, DEFAULT_LOCAL_PORT) ?: DEFAULT_LOCAL_PORT

        if (host.isBlank() || port !in 1..65535 || protocol !in setOf("http", "socks", "socks5")) {
            recordError("Enter a valid proxy host, port, and protocol.")
            stopSelf()
            return START_NOT_STICKY
        }

        return try {
            val config = SingBoxConfig.write(this, protocol, host, port, username, password, localPort)
            val server = CommandServer(this, SingBoxPlatformInterface())
            server.start()
            server.startOrReloadService(config.readText(), OverrideOptions().apply { autoRedirect = false })
            commandServer = server
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_RUNNING, true)
                .remove(KEY_ERROR)
                .apply()
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification("Local proxy is active"))
            START_STICKY
        } catch (error: Exception) {
            recordError("The local proxy could not start: ${error.message ?: "check your proxy details"}")
            stopTunnel()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupLibbox() {
        val working = getExternalFilesDir(null) ?: filesDir
        Libbox.setup(SetupOptions().apply {
            basePath = filesDir.path
            workingPath = working.path
            tempPath = cacheDir.path
            fixAndroidStack = true
            logMaxLines = 300
            appVersion = BuildConfig.VERSION_CODE.toString()
            appMarketingVersion = BuildConfig.VERSION_NAME
            debug = BuildConfig.DEBUG
        })
    }

    @Suppress("DEPRECATION")
    private fun startForegroundCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification(text))
        }
    }

    private fun stopTunnel(clearError: Boolean = false) {
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        val editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false)
        if (clearError) editor.remove(KEY_ERROR)
        editor.apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun recordError(message: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, false)
            .putString(KEY_ERROR, message)
            .apply()
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification(message))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local proxy",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status for the active local proxy"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("Proxy Platform")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun connectSSHAgent(): Int = -1
    override fun getSystemProxyStatus(): SystemProxyStatus =
        SystemProxyStatus().apply { available = false; enabled = false }
    override fun serviceReload() = Unit
    override fun serviceStop() = stopTunnel()
    override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit
    override fun triggerNativeCrash() = Unit
    override fun writeDebugMessage(message: String) = Unit

    companion object {
        const val ACTION_START = "com.proxyplatform.app.action.START_PROXY"
        const val ACTION_STOP = "com.proxyplatform.app.action.STOP_PROXY"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_LOCAL_PORT = "local_port"
        const val DEFAULT_LOCAL_PORT = 10808
        const val PREFS = "proxy_vpn"
        const val KEY_RUNNING = "running"
        private const val KEY_ERROR = "last_error"
        const val CHANNEL_ID = "proxy_local_service"
        private const val NOTIFICATION_ID = 7001

        fun isRunning(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUNNING, false)

        fun lastError(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ERROR, null)

        fun clearLastError(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ERROR).apply()
        }
    }
}
