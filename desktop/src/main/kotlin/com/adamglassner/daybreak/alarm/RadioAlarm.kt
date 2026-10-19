package com.adamglassner.daybreak.alarm

import com.adamglassner.daybreak.data.Station

/**
 * Wake to the radio: an alarm whose sound is a radio station. The station is kept in the
 * alarm's sound field as "radio:<stream url>", with the station name as the sound name,
 * so backups and older versions still read it (they just play the regular alarm sound).
 */
object RadioAlarm {
    const val PREFIX = "radio:"

    /** If the station hasn't started playing by then, the regular alarm sound plays instead. */
    const val START_TIMEOUT_MS = 20_000L

    fun soundUri(st: Station) = PREFIX + st.url
    fun soundName(st: Station) = "Radio: " + st.name
    fun url(soundUri: String?): String? = soundUri?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.ifBlank { null }
    fun isRadio(soundUri: String?) = url(soundUri) != null
}
