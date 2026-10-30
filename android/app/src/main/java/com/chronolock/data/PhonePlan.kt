package com.chronolock.data

import com.chronolock.domain.PhonePolicy
import com.chronolock.domain.ScheduleDraft
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the person set for the whole phone, as stored: [windows] when only always-allowed apps open, a [budgetSeconds]
 * for every other app together, and the [exempt] apps (always allowed). [policy] turns it into the limits the monitor
 * applies, once the launchable apps are known.
 */
data class PhonePlan(
    val windows: List<ScheduleDraft> = emptyList(),
    val budgetSeconds: Long? = null,
    val exempt: Set<String> = emptySet(),
) {
    val active: Boolean get() = windows.isNotEmpty() || budgetSeconds != null

    /** The limits over [launchable] apps (safety apps and ChronoLock already left out by the caller). */
    fun policy(launchable: Set<String>): PhonePolicy =
        PhonePolicy(windows.mapNotNull { runCatching { it.build() }.getOrNull() }, budgetSeconds, launchable - exempt)

    /** Whether [next] lets more through: a window dropped, the budget eased or removed, or another app always allowed. */
    fun loosenedBy(next: PhonePlan): Boolean =
        policy(emptySet()).loosenedBy(next.policy(emptySet())) || (next.exempt - exempt).isNotEmpty()

    fun encode(): String = JSONObject()
        .put("windows", JSONArray(windows.map { JSONObject().put("start", it.start).put("end", it.end).put("days", it.daysMask) }))
        .putOpt("budget", budgetSeconds)
        .put("exempt", JSONArray(exempt.sorted()))
        .toString()

    companion object {
        fun decode(text: String?): PhonePlan = runCatching {
            val json = JSONObject(text ?: return PhonePlan())
            val windows = json.optJSONArray("windows")?.let { array ->
                (0 until array.length()).map { array.getJSONObject(it) }
                    .map { ScheduleDraft(it.getString("start"), it.getString("end"), it.getInt("days")) }
            }.orEmpty()
            val exempt = json.optJSONArray("exempt")?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }.orEmpty()
            PhonePlan(windows, if (json.has("budget")) json.getLong("budget") else null, exempt)
        }.getOrDefault(PhonePlan())
    }
}
