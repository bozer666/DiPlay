package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton

/**
 * Legacy media-button broadcast receiver for head units (e.g. Lynk OSN) whose
 * firmware routes steering-wheel keys as ACTION_MEDIA_BUTTON broadcasts
 * instead of (or in addition to) the standard MediaSession dispatch.
 *
 * Forwards recognized keys to [CarPlayMediaKeys.performAction].
 */
class SteeringMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        @Suppress("DEPRECATION")
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return
        Log.i(TAG, "receiver got keyCode=${event.keyCode} name=${KeyEvent.keyCodeToString(event.keyCode)}")
        // Make sure our custom mapping is loaded even without an active CarPlay session.
        CarPlayMediaKeys.refreshKeyMap(context)
        val action = CarPlayMediaButton.forKeyCode(event.keyCode) ?: return
        CarPlayMediaKeys.performAction(action, "receiver:${KeyEvent.keyCodeToString(event.keyCode)}")
    }

    companion object {
        private const val TAG = "DiPlay-SteerRecv"
    }
}
