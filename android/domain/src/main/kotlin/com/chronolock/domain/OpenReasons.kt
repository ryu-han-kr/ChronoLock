package com.chronolock.domain

import java.time.LocalDate

/** Why the person reached for a blocked app, picked with one tap on the block screen. */
enum class OpenReason(val label: String) {
    BORED("심심해서"),
    HABIT("습관처럼"),
    NOTIFICATION("알림 보고"),
    NEEDED("꼭 필요해서"),
}

/** Taps per day and reason, kept on the device for [KEEP_DAYS] days. Stored as `2026-10-30:HABIT:2;…`. */
object OpenReasons {
    const val KEEP_DAYS = 28L

    fun decode(text: String?): Map<LocalDate, Map<OpenReason, Int>> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = mutableMapOf<LocalDate, MutableMap<OpenReason, Int>>()
        text.split(';').forEach { entry ->
            val parts = entry.split(':')
            if (parts.size != 3) return@forEach
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@forEach
            val reason = OpenReason.entries.firstOrNull { it.name == parts[1] } ?: return@forEach
            val count = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
            out.getOrPut(date) { mutableMapOf() }[reason] = count
        }
        return out
    }

    fun encode(counts: Map<LocalDate, Map<OpenReason, Int>>): String =
        counts.toSortedMap().flatMap { (date, reasons) -> reasons.toSortedMap().map { (reason, count) -> "$date:${reason.name}:$count" } }.joinToString(";")

    /** One more tap of [reason] on [today]; days older than [KEEP_DAYS] fall away. */
    fun add(counts: Map<LocalDate, Map<OpenReason, Int>>, today: LocalDate, reason: OpenReason): Map<LocalDate, Map<OpenReason, Int>> {
        // Only old days go; a clock set back must not wipe the days after it.
        val kept = counts.filterKeys { it > today.minusDays(KEEP_DAYS) }.toMutableMap()
        val day = kept[today].orEmpty()
        kept[today] = day + (reason to (day[reason] ?: 0) + 1)
        return kept
    }

    /** Each reason's share in percent over the 7 days ending [today], most picked first; empty with no taps. */
    fun weekShares(counts: Map<LocalDate, Map<OpenReason, Int>>, today: LocalDate): List<Pair<OpenReason, Int>> {
        val totals = counts.filterKeys { it in today.minusDays(6)..today }.values
            .flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        val all = totals.values.sum()
        if (all == 0) return emptyList()
        return totals.entries.sortedWith(compareByDescending<Map.Entry<OpenReason, Int>> { it.value }.thenBy { it.key.ordinal })
            .map { it.key to Math.round(it.value * 100.0 / all).toInt() }
    }
}
