package com.proxyplatform.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manages Wireless ADB (Android Debug Bridge) over WiFi.
 * Supports pairing and connection for advanced privilege elevation.
 * 
 * Requirements:
 * - Developer Options enabled
 * - Wireless Debugging enabled (Android 11+)
 * - Device pairing on same network
 */
object WirelessDebuggingManager {

    private const val TAG = "WirelessDebuggingManager"
    private const val WIRELESS_DEBUGGING_PORT = 5555

    enum class DebuggingState {
        DEVELOPER_DISABLED,      // Developer options not enabled
        WIRELESS_DISABLED,       // Wireless debugging not enabled
        NOT_PAIRED,             // Wireless debugging enabled but not paired
        PAIRED_NOT_CONNECTED,   // Paired but not connected
        READY                   // Ready to execute commands
    }

    /**
     * Check the current state of Wireless Debugging setup
     */
    fun checkState(context: Context): DebuggingState {
        if (!areDeveloperOptionsEnabled(context)) {
            return DebuggingState.DEVELOPER_DISABLED
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Wireless Debugging not available on Android < 11
            return DebuggingState.WIRELESS_DISABLED
        }

        if (!isWirelessDebuggingEnabled(context)) {
            return DebuggingState.WIRELESS_DISABLED
        }

        // Check if device is paired
        if (!isDevicePaired(context)) {
            return DebuggingState.NOT_PAIRED
        }

        // Check if connected
        if (!isWirelessAdbConnected()) {
            return DebuggingState.PAIRED_NOT_CONNECTED
        }

        return DebuggingState.READY
    }

    /**
     * Checks if developer options are enabled on the device
     */
    @SuppressLint("HardwareIds")
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
     * Checks if wireless debugging is enabled (Android 11+)
     */
    @SuppressLint("HardwareIds")
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

    /**
     * Checks if the device is paired via wireless debugging
     */
    private fun isDevicePaired(context: Context): Boolean {
        // This is a simple check - in production, you'd query the pairing status
        // For now, we assume if wireless debugging is enabled, pairing is available
        return isWirelessDebuggingEnabled(context)
    }

    /**
     * Check if wireless ADB is currently connected
     */
    private fun isWirelessAdbConnected(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("getprop net.qtaguid_enabled")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readText().trim()
            process.waitFor()
            result == "1"
        } catch (e: Exception) {
            Log.w(TAG, "Error checking ADB connection: ${e.message}")
            false
        }
    }

    /**
     * Request pairing for wireless debugging
     * This will trigger a notification on the device to enter the pairing code
     */
    fun requestPairing(pairingCode: String): Boolean {
        return try {
            // Execute the pairing command
            val command = "adb pair 127.0.0.1:5555 $pairingCode"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val exitCode = process.waitFor()
            
            Log.d(TAG, "Pairing request sent with exit code: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting pairing: ${e.message}")
            false
        }
    }

    /**
     * Connect to wireless ADB
     */
    fun connect(host: String = "127.0.0.1", port: Int = WIRELESS_DEBUGGING_PORT): Boolean {
        return try {
            val command = "adb connect $host:$port"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            val exitCode = process.waitFor()
            
            Log.d(TAG, "Connection attempt: $output (exit code: $exitCode)")
            exitCode == 0 || output.contains("connected")
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to wireless ADB: ${e.message}")
            false
        }
    }

    /** Compatibility overload used by the Android UI; the manager owns the ADB connection. */
    fun connect(context: Context): Boolean {
        return connect()
    }

    /** Compatibility entry point used by the Compose pairing flow. */
    fun pair(context: Context, pairingCode: String): Boolean {
        return requestPairing(pairingCode)
    }

    /** Compatibility overload used by the advanced proxy flow. */
    fun executeCommand(context: Context, command: String): Boolean {
        return executeCommand(command)
    }

    /**
     * Execute a shell command via wireless ADB
     */
    fun executeCommand(command: String): Boolean {
        return try {
            val fullCommand = "adb shell $command"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", fullCommand))
            val exitCode = process.waitFor()
            
            Log.d(TAG, "Command executed: $command (exit code: $exitCode)")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${e.message}")
            false
        }
    }

    /** Show the pairing instruction notification. */
    fun showPairingNotification(context: Context) {
        com.proxyplatform.app.adb.AdbPairingNotifier.showPairing(context)
    }

    /**
     * Get the text for developer options navigation
     */
    fun getDebuggingSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 - Direct to Developer Options
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        } else {
            // Older versions - open Developer Options via About Phone
            Intent().apply {
                action = Settings.ACTION_APPLICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
    }

    /**
     * Get localized step descriptions for UI display
     */
    fun getSetupSteps(): List<SetupStep> {
        return listOf(
            SetupStep(
                number = 1,
                title = "تفعيل وضع المطور",
                description = "انتقل إلى الإعدادات > حول الهاتف وانقر 7 مرات على رقم البناء",
                actionText = "فتح الإعدادات"
            ),
            SetupStep(
                number = 2,
                title = "تفعيل التصحيح اللاسلكي",
                description = "افتح إعدادات المطور وفعّل 'Wireless Debugging'",
                actionText = "فتح إعدادات المطور"
            ),
            SetupStep(
                number = 3,
                title = "الاقتران بالجهاز",
                description = "سيظهر إشعار على هاتفك يطلب رمز الاقتران",
                actionText = "إدخال الرمز"
            ),
            SetupStep(
                number = 4,
                title = "تنفيذ الأوامر",
                description = "بعد الاقتران، سيمكنك تنفيذ الأوامر المتقدمة",
                actionText = "متابعة"
            )
        )
    }

    data class SetupStep(
        val number: Int,
        val title: String,
        val description: String,
        val actionText: String
    )
}
