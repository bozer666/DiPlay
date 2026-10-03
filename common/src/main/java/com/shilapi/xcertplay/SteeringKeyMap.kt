package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.CarPlayMediaButton

/**
 * User-configurable steering-wheel key mapping, persisted in SharedPreferences.
 *
 * Maps an action id (previous/next/...) to the Android keyCode the car's
 * steering wheel sends. The reverse map (keyCode -> [CarPlayMediaButton] action)
 * is installed into [CarPlayMediaButton.customKeyMap] and takes precedence
 * over the built-in key handling.
 */
object SteeringKeyMap {
    const val ACTION_PREVIOUS = "previous"
    const val ACTION_NEXT = "next"
    const val ACTION_PLAY_PAUSE = "play_pause"
    const val ACTION_VOLUME_UP = "volume_up"
    const val ACTION_VOLUME_DOWN = "volume_down"
    const val ACTION_VOICE = "voice"

    val ACTIONS = listOf(
        ACTION_PREVIOUS,
        ACTION_NEXT,
        ACTION_PLAY_PAUSE,
        ACTION_VOLUME_UP,
        ACTION_VOLUME_DOWN,
        ACTION_VOICE,
    )

    private const val PREFS = "steering_key_map"

    fun buttonAction(actionId: String): Int = when (actionId) {
        ACTION_PREVIOUS -> CarPlayMediaButton.PREVIOUS
        ACTION_NEXT -> CarPlayMediaButton.NEXT
        ACTION_PLAY_PAUSE -> CarPlayMediaButton.PLAY_PAUSE
        ACTION_VOLUME_UP -> CarPlayMediaButton.VOLUME_UP
        ACTION_VOLUME_DOWN -> CarPlayMediaButton.VOLUME_DOWN
        ACTION_VOICE -> CarPlayMediaButton.VOICE
        else -> 0
    }

    /** actionId -> keyCode for all bound actions. */
    fun loadBindings(context: Context): Map<String, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ACTIONS.mapNotNull { id ->
            val code = prefs.getInt(id, -1)
            if (code >= 0) id to code else null
        }.toMap()
    }

    /** keyCode -> CarPlayMediaButton action, for [CarPlayMediaButton.customKeyMap]. */
    fun loadReverseMap(context: Context): Map<Int, Int> =
        loadBindings(context).mapNotNull { (id, code) ->
            val action = buttonAction(id)
            if (action != 0) code to action else null
        }.toMap()

    fun save(context: Context, actionId: String, keyCode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(actionId, keyCode).apply()
    }

    fun clear(context: Context, actionId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(actionId).apply()
    }
}
