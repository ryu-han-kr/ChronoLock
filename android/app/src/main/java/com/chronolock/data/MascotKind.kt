package com.chronolock.data

import androidx.annotation.DrawableRes
import com.chronolock.R

/** The companion character drawn across the app; [title] is its translatable Korean name. */
enum class MascotKind(val title: String) {
    OTTER("수달"),
    RACCOON("흰너구리"),
    HAMSTER("햄스터"),
    RABBIT("토끼"),
    FOX("북극여우"),
    ;

    /** The artwork of this character in [pose]. */
    @DrawableRes fun art(pose: Pose): Int = ART.getValue(this)[pose.ordinal]

    companion object {
        val DEFAULT = HAMSTER
        /** The one character of the free version; the others come with Pro. */
        val FREE = HAMSTER
        fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** What the character is doing; every [MascotKind] has art for each pose (res/drawable-nodpi/mascot_<kind>_<pose>). */
enum class Pose { DEFAULT, LOCK, UNLOCK, FOCUS, TIMER, LIMIT, REST, SLEEP, WARNING, SUCCESS }

// Listed by reference rather than looked up by name, so resource shrinking keeps every pose.
private val ART: Map<MascotKind, IntArray> = mapOf(
    MascotKind.OTTER to intArrayOf(R.drawable.mascot_otter_default, R.drawable.mascot_otter_lock, R.drawable.mascot_otter_unlock, R.drawable.mascot_otter_focus, R.drawable.mascot_otter_timer, R.drawable.mascot_otter_limit, R.drawable.mascot_otter_rest, R.drawable.mascot_otter_sleep, R.drawable.mascot_otter_warning, R.drawable.mascot_otter_success),
    MascotKind.RACCOON to intArrayOf(R.drawable.mascot_raccoon_default, R.drawable.mascot_raccoon_lock, R.drawable.mascot_raccoon_unlock, R.drawable.mascot_raccoon_focus, R.drawable.mascot_raccoon_timer, R.drawable.mascot_raccoon_limit, R.drawable.mascot_raccoon_rest, R.drawable.mascot_raccoon_sleep, R.drawable.mascot_raccoon_warning, R.drawable.mascot_raccoon_success),
    MascotKind.HAMSTER to intArrayOf(R.drawable.mascot_hamster_default, R.drawable.mascot_hamster_lock, R.drawable.mascot_hamster_unlock, R.drawable.mascot_hamster_focus, R.drawable.mascot_hamster_timer, R.drawable.mascot_hamster_limit, R.drawable.mascot_hamster_rest, R.drawable.mascot_hamster_sleep, R.drawable.mascot_hamster_warning, R.drawable.mascot_hamster_success),
    MascotKind.RABBIT to intArrayOf(R.drawable.mascot_rabbit_default, R.drawable.mascot_rabbit_lock, R.drawable.mascot_rabbit_unlock, R.drawable.mascot_rabbit_focus, R.drawable.mascot_rabbit_timer, R.drawable.mascot_rabbit_limit, R.drawable.mascot_rabbit_rest, R.drawable.mascot_rabbit_sleep, R.drawable.mascot_rabbit_warning, R.drawable.mascot_rabbit_success),
    MascotKind.FOX to intArrayOf(R.drawable.mascot_fox_default, R.drawable.mascot_fox_lock, R.drawable.mascot_fox_unlock, R.drawable.mascot_fox_focus, R.drawable.mascot_fox_timer, R.drawable.mascot_fox_limit, R.drawable.mascot_fox_rest, R.drawable.mascot_fox_sleep, R.drawable.mascot_fox_warning, R.drawable.mascot_fox_success),
)
