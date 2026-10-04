package com.shilapi.xcertplay.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import java.io.Closeable

/**
 * A2DP-sink music handoff for one active CarPlay peer.
 *
 * While wireless CarPlay is active the iPhone may keep its Bluetooth music (A2DP)
 * channel connected to the head unit, so iOS keeps treating the head unit as a
 * Bluetooth audio output and fights CarPlay for the audio route (music auto-pauses
 * on play). This guard watches the A2DP sink profile and disconnects the peer's
 * music channel whenever it connects, so CarPlay over Wi-Fi stays the only audio
 * route. Bonding and the phone-call (HFP) channel are left untouched.
 *
 * Kicking alone is racy: iOS switches its audio route the moment A2DP connects,
 * before the kick lands. So the guard also sets the peer's A2DP auto-connect
 * priority to PRIORITY_OFF while it runs (the programmatic equivalent of turning
 * off "media audio" in Bluetooth settings) and restores the original priority
 * when it closes.
 */
@SuppressLint("MissingPermission")
internal class BluetoothMusicGuard(
    context: Context,
    private val address: String,
    private val report: (String) -> Unit,
) : Closeable {
    private val app = context.applicationContext
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    private var proxy: BluetoothProfile? = null
    private var registered = false
    private var closed = false
    private var lastDisconnectAt = 0L
    private var unavailable = false
    private var originalPriority: Int? = null
    private var priorityBlocked = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_CONNECTION) return
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (!device.address.equals(address, true)) return
            if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) in 1..2) {
                disconnectPeer()
                blockAutoConnect()
            }
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= 31 && app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            report("Bluetooth music handoff unavailable: connection permission")
            return
        }
        try {
            val filter = IntentFilter(ACTION_CONNECTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                app.registerReceiver(receiver, filter)
            }
            registered = true
            val requested = adapter?.getProfileProxy(app, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, connected: BluetoothProfile) = synchronized(this@BluetoothMusicGuard) {
                    if (closed) { adapter?.closeProfileProxy(profile, connected); return@synchronized }
                    proxy = connected
                    disconnectPeer()
                    blockAutoConnect()
                }
                override fun onServiceDisconnected(profile: Int) = synchronized(this@BluetoothMusicGuard) { proxy = null }
            }, A2DP_SINK) == true
            if (!requested) { report("Bluetooth music handoff unavailable: A2DP sink"); close() }
        } catch (error: Exception) {
            report("Bluetooth music handoff unavailable: ${error.javaClass.simpleName}")
            close()
        }
    }

    @Synchronized
    private fun disconnectPeer() {
        if (closed || unavailable) return
        val profile = proxy ?: return
        try {
            val device = profile.connectedDevices.firstOrNull { it.address.equals(address, true) } ?: return
            val now = SystemClock.elapsedRealtime()
            if (lastDisconnectAt != 0L && now - lastDisconnectAt < 500L) return
            lastDisconnectAt = now
            val disconnected = profile.javaClass.getMethod("disconnect", BluetoothDevice::class.java).invoke(profile, device)
            report("Bluetooth music handoff accepted=${disconnected == true}")
        } catch (error: Exception) {
            unavailable = true
            report("Bluetooth music handoff unavailable: ${error.javaClass.simpleName}")
        }
    }

    /**
     * Prevents the peer's A2DP channel from (re)connecting while CarPlay is active.
     * Best-effort: a SecurityException (BLUETOOTH_PRIVILEGED on strict stacks) only
     * disables the prevention, the reactive [disconnectPeer] keeps working.
     */
    @Synchronized
    private fun blockAutoConnect() {
        if (closed) return
        val profile = proxy ?: return
        try {
            val device = adapter?.getRemoteDevice(address) ?: return
            val current = profile.javaClass
                .getMethod("getPriority", BluetoothDevice::class.java)
                .invoke(profile, device) as? Int ?: return
            if (originalPriority == null) originalPriority = current
            if (current == PRIORITY_OFF) return // already blocked in settings
            val blocked = profile.javaClass
                .getMethod("setPriority", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
                .invoke(profile, device, PRIORITY_OFF)
            if (blocked == true) {
                priorityBlocked = true
                report("Bluetooth music auto-connect blocked (was priority=$current)")
            } else {
                report("Bluetooth music auto-connect block declined")
            }
        } catch (error: Exception) {
            report("Bluetooth music auto-connect block unavailable: ${error.javaClass.simpleName}")
        }
    }

    @Synchronized
    private fun restorePriority() {
        if (!priorityBlocked) {
            originalPriority = null
            return
        }
        val original = originalPriority
        priorityBlocked = false
        originalPriority = null
        if (original == null) return
        try {
            val device = adapter?.getRemoteDevice(address) ?: return
            proxy?.javaClass
                ?.getMethod("setPriority", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
                ?.invoke(proxy, device, original)
            report("Bluetooth music auto-connect restored priority=$original")
        } catch (error: Exception) {
            report("Bluetooth music auto-connect restore unavailable: ${error.javaClass.simpleName}")
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        if (registered) runCatching { app.unregisterReceiver(receiver) }
        registered = false
        restorePriority()
        proxy?.let { runCatching { adapter?.closeProfileProxy(A2DP_SINK, it) } }
        proxy = null
    }

    companion object {
        private const val A2DP_SINK = 11
        private const val ACTION_CONNECTION = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED"
        /** A2DP auto-connect priority "off"; matches BluetoothA2dpSink.PRIORITY_OFF on all API levels. */
        private const val PRIORITY_OFF = 0
    }
}
