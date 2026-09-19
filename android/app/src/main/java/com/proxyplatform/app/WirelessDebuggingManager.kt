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
    private const val PREFS_NAME = "wireless_debugging_state"
    private const val KEY_PAIRED = "is_paired"
    private const val KEY_CONNECTED = "is_connected"
    private const val KEY_CONNECTION_PORT = "connection_port"

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

        // Check if device is paired (persisted flag set only after a successful pairing)
        if (!isDevicePaired(context)) {
            return DebuggingState.NOT_PAIRED
        }

        // Check if connected (persisted flag set only after a successful adb connect)
        if (!isWirelessAdbConnected(context)) {
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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Checks if the device has been paired via wireless debugging.
     * The paired flag is persisted in SharedPreferences and is only set
     * after a successful `adb pair` command completes.
     */
    private fun isDevicePaired(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PAIRED, false)
    }

    /**
     * Check if wireless ADB is currently connected.
     * The connected flag is persisted and only set after a successful
     * `adb connect` command completes.
     */
    private fun isWirelessAdbConnected(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_CONNECTED, false)
    }

    /** The port used for the active ADB connection (saved after connect). */
    fun getConnectionPort(context: Context): Int =
        prefs(context).getInt(KEY_CONNECTION_PORT, 0)

    /**
     * Request pairing for wireless debugging using the dynamic pairing port
     * shown in Developer Options → Wireless Debugging → Pair device with code.
     *
     * @param pairingPort  the port displayed on the "Pair device with code" screen
     * @param pairingCode  the 6-digit code shown on that same screen
     * @return true if pairing succeeded
     */
    fun requestPairing(context: Context, pairingPort: Int, pairingCode: String): Boolean {
        return try {
            val command = "adb pair 127.0.0.1:$pairingPort $pairingCode"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = reader.readText().trim()
            val errorOutput = errorReader.readText().trim()
            val exitCode = process.waitFor()

            Log.d(TAG, "Pairing output: $output | error: $errorOutput (exit $exitCode)")

            if (exitCode == 0 && (output.contains("paired", ignoreCase = true) ||
                    output.contains("success", ignoreCase = true))) {
                prefs(context).edit().putBoolean(KEY_PAIRED, true).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting pairing: ${e.message}")
            false
        }
    }

    /**
     * Connect to wireless ADB using the port shown on the main Wireless
     * Debugging screen (IP address & port line).
     */
    fun connect(host: String = "127.0.0.1", port: Int): Boolean {
        return try {
            val command = "adb connect $host:$port"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            val exitCode = process.waitFor()

            Log.d(TAG, "Connection attempt: $output (exit code: $exitCode)")
            exitCode == 0 || output.contains("connected", ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to wireless ADB: ${e.message}")
            false
        }
    }

    /** Connect and persist the connection state so [checkState] reflects success. */
    fun connect(context: Context, port: Int): Boolean {
        val ok = connect("127.0.0.1", port)
        if (ok) {
            prefs(context).edit()
                .putBoolean(KEY_CONNECTED, true)
                .putInt(KEY_CONNECTION_PORT, port)
                .apply()
        }
        return ok
    }

    /** Compatibility entry point used by the Compose pairing flow. */
    fun pair(context: Context, pairingPort: Int, pairingCode: String): Boolean {
        return requestPairing(context, pairingPort, pairingCode)
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

    /** Reset all pairing/connection state (used when stopping advanced mode). */
    fun resetState(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_PAIRED, false)
            .putBoolean(KEY_CONNECTED, false)
            .putInt(KEY_CONNECTION_PORT, 0)
            .apply()
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
