package com.proxyplatform.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.proxyplatform.app.adb.AdbPairingNotifier

/** Compatibility facade for the UI; all ADB work is performed by EmbeddedAdbManager. */
object WirelessDebuggingManager {
    private const val TAG = "WirelessDebuggingManager"

    enum class DebuggingState {
        DEVELOPER_DISABLED,
        WIRELESS_DISABLED,
        NOT_PAIRED,
        PAIRED_NOT_CONNECTED,
        READY
    }

    fun checkState(context: Context): DebuggingState {
        if (!areDeveloperOptionsEnabled(context)) return DebuggingState.DEVELOPER_DISABLED
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !isWirelessDebuggingEnabled(context)) {
            return DebuggingState.WIRELESS_DISABLED
        }
        return runCatching {
            if (EmbeddedAdbManager.get(context).isConnected()) {
                DebuggingState.READY
            } else {
                DebuggingState.NOT_PAIRED
            }
        }.onFailure {
            // ADB is optional. A broken provider, stale Keystore entry, or a
            // device-side pairing reset must never crash the Compose screen.
            Log.e(TAG, "Wireless ADB state check failed", it)
        }.getOrDefault(DebuggingState.NOT_PAIRED)
    }

    @SuppressLint("HardwareIds")
    fun areDeveloperOptionsEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }.getOrDefault(false)

    @SuppressLint("HardwareIds")
    fun isWirelessDebuggingEnabled(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && runCatching {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) != 0
        }.getOrDefault(false)

    fun pair(context: Context, pairingCode: String): Result<Unit> = runCatching {
        EmbeddedAdbManager.get(context).pair(
            EmbeddedAdbManager.DEFAULT_TIMEOUT_MS,
            pairingCode
        ).getOrThrow()
        EmbeddedAdbManager.get(context).connect(
            EmbeddedAdbManager.DEFAULT_TIMEOUT_MS
        ).getOrThrow()
    }

    /** The connection port is discovered via mDNS; the old integer is ignored. */
    fun connect(context: Context, ignoredPort: Int): Boolean = runCatching {
        EmbeddedAdbManager.get(context).connect(EmbeddedAdbManager.DEFAULT_TIMEOUT_MS).getOrThrow()
    }.onFailure { Log.w(TAG, "ADB auto-connect failed", it) }.isSuccess

    fun executeCommandResult(context: Context, command: String): Result<String> {
        val adb = EmbeddedAdbManager.get(context)
        val first = runCatching {
            if (!adb.isConnected()) {
                adb.connect(EmbeddedAdbManager.DEFAULT_TIMEOUT_MS).getOrThrow()
            }
            adb.execute(command).getOrThrow()
        }
        if (first.isSuccess) return first

        // Wireless ADB can be dropped by Android after pairing or after the
        // app has been backgrounded. Reconnect once before reporting failure.
        return runCatching {
            Log.w(TAG, "ADB command failed; reconnecting once", first.exceptionOrNull())
            adb.close()
            adb.connect(EmbeddedAdbManager.DEFAULT_TIMEOUT_MS).getOrThrow()
            adb.execute(command).getOrThrow()
        }.onFailure { Log.e(TAG, "ADB shell command failed: $command", it) }
    }

    fun executeCommand(context: Context, command: String): Boolean =
        executeCommandResult(context, command).isSuccess

    fun resetState(context: Context) {
        EmbeddedAdbManager.get(context).close()
    }

    fun showPairingNotification(context: Context) {
        AdbPairingNotifier.showPairing(context)
    }

    fun getDebuggingSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("package:${context.packageName}"))
        }
}
