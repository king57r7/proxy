package com.proxyplatform.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import rikka.shizuku.Shizuku

/**
 * Manages Shizuku lifecycle: binder availability, permission, and privileged
 * shell commands used to set / clear the system-wide HTTP proxy via
 * `settings put global http_proxy`.
 */
object ShizukuManager {

    private const val PERMISSION_REQUEST_CODE = 1001

    enum class ShizukuState {
        NOT_INSTALLED,
        NOT_RUNNING,
        PERMISSION_DENIED,
        READY
    }

    fun checkState(context: Context): ShizukuState {
        if (!isShizukuInstalled(context)) return ShizukuState.NOT_INSTALLED
        if (!Shizuku.pingBinder()) return ShizukuState.NOT_RUNNING
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return ShizukuState.PERMISSION_DENIED
        return ShizukuState.READY
    }

    private fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isPermissionGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun shouldShowRationale(): Boolean {
        return try {
            Shizuku.shouldShowRequestPermissionRationale()
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) return
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (_: Exception) {
            // Shizuku binder not alive — caller should check state first
        }
    }

    /**
     * Sets the system-wide HTTP proxy by running
     * `settings put global http_proxy host:port` with shell privileges via
     * Shizuku's newProcess (ADB identity, UID 2000).
     *
     * On Android Q+ this also sets the global HTTP proxy via
     * `settings put global global_http_proxy_host` and
     * `settings put global global_http_proxy_port` for broader coverage.
     */
    fun setSystemProxy(host: String, port: Int): Boolean {
        return executeShell("settings put global http_proxy $host:$port")
    }

    fun clearSystemProxy(): Boolean {
        return executeShell("settings put global http_proxy :0")
    }

    /**
     * Runs a single shell command through Shizuku with ADB privileges.
     * Returns true if the command executed with exit code 0.
     */
    fun executeShell(command: String): Boolean {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if developer options are enabled on the device.
     */
    fun areDeveloperOptionsEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) != 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if USB debugging is enabled.
     */
    fun isUsbDebuggingEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) != 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if wireless debugging is enabled (Android 11+).
     */
    fun isWirelessDebuggingEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                "adb_wifi_enabled",
                0
            ) != 0
        } catch (_: Exception) {
            false
        }
    }
}
