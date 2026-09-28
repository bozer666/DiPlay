package com.shilapi.xcertplay.orchestration

/** Rules an existing (car) hotspot must meet before CarPlay can hand its credentials to the iPhone. */
object ManualHotspotValidation {
    /**
     * Why a name and password cannot be used. The codes carry no copy: the UI layer owns the wording
     * and localizes it, while `this` module stays free of resources.
     */
    enum class Problem {
        EMPTY_SSID,
        SSID_TOO_LONG,
        INVALID_CHARACTER,
        PASSWORD_LENGTH,
    }

    /** Security implied by the password: the car hotspot UI only offers open or WPA2 networks. */
    fun securityFor(passphrase: String): ManualHotspotSecurity =
        if (passphrase.isEmpty()) ManualHotspotSecurity.OPEN else ManualHotspotSecurity.WPA2

    /** The reason the name and password cannot be used, or null when they can. */
    fun validate(ssid: String, passphrase: String): Problem? = when {
        ssid.isBlank() -> Problem.EMPTY_SSID
        ssid.encodeToByteArray().size > 32 -> Problem.SSID_TOO_LONG
        '\u0000' in ssid || '\u0000' in passphrase -> Problem.INVALID_CHARACTER
        passphrase.isNotEmpty() && passphrase.length !in 8..63 -> Problem.PASSWORD_LENGTH
        else -> null
    }
}
