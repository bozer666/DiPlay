// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The in-app language override.
 *
 * DiPlay ships English in `values/` and Simplified Chinese in `values-zh/`. With no stored choice
 * the activity keeps the head unit's own configuration, so a Chinese head unit (any `zh` variant)
 * shows Chinese and every other language falls back to English.
 *
 * Android 13's per-app language API is not used: `minSdk` is 28 and the switch has to behave the
 * same on every head unit, so an explicit choice is applied by wrapping the base context. The head
 * unit language is deliberately never mirrored into a preference — an untouched install keeps
 * following the head unit, while an explicit choice stays until the app data is cleared.
 */
internal object AppLanguage {
    /** Stored value that forces English. */
    const val ENGLISH = "en"

    /** Stored value that forces Simplified Chinese. */
    const val CHINESE = "zh"

    /** The locale to force, or null when the head unit's own locale decides. */
    fun resolve(stored: String?): Locale? = when (stored) {
        ENGLISH -> Locale.ENGLISH
        CHINESE -> Locale.SIMPLIFIED_CHINESE
        else -> null
    }

    /**
     * The language the user is reading right now: the stored choice, or the head unit language when
     * nothing is stored. Settings preselects the matching row with it.
     */
    fun effective(context: Context, stored: String?): String =
        resolve(stored)?.language ?: systemLanguage(context)

    fun systemLanguage(context: Context): String {
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault().language else locales[0].language
    }

    /** Wraps [base] so resources resolve to the stored choice; returns [base] when nothing is stored. */
    fun wrap(base: Context): Context {
        val locale = resolve(DiPlayPreferences.language(base)) ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }
}
