package com.tabletreminder.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** A medicine the person takes after each meal. */
class Medicine(val id: Long, var name: String, var count: Int)

/** Everything on the Settings screen. */
class AppSettings(
    var firstWait: Int = 15,
    var interval: Int = 5,
    var maxReminders: Int = 6,
    var soundUri: String? = null,
    var soundName: String = "Default alarm",
    var vibrate: Boolean = true
)

/** One medicine inside a running (or finished) meal session. */
class SessionItem(
    val medId: Long,
    val name: String,
    val total: Int,
    val taken: MutableList<Long> = mutableListOf()
) {
    val left: Int get() = (total - taken.size).coerceAtLeast(0)
}

/** The reminders that start when the person taps "I ate food". */
class Session(
    val ateAt: Long,
    var fired: Int,
    var nextAt: Long,
    val items: MutableList<SessionItem>
) {
    val totalLeft: Int get() = items.sumOf { it.left }
    val totalTablets: Int get() = items.sumOf { it.total }
}

class HistoryEntry(
    val ateAt: Long,
    val endedAt: Long,
    val status: String,
    val items: List<SessionItem>
)

object Store {
    const val TAKEN = "taken"
    const val MISSED = "missed"
    const val CANCELLED = "cancelled"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("tablet_reminder", Context.MODE_PRIVATE)

    // ---------- medicines ----------
    fun medicines(ctx: Context): MutableList<Medicine> {
        val raw = prefs(ctx).getString("meds", null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        val out = mutableListOf<Medicine>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Medicine(o.getLong("id"), o.getString("name"), o.getInt("count")))
        }
        return out
    }

    fun saveMedicines(ctx: Context, list: List<Medicine>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("count", it.count)) }
        prefs(ctx).edit().putString("meds", arr.toString()).apply()
    }

    // ---------- settings ----------
    fun settings(ctx: Context): AppSettings {
        val p = prefs(ctx)
        return AppSettings(
            firstWait = p.getInt("firstWait", 15),
            interval = p.getInt("interval", 5),
            maxReminders = p.getInt("maxReminders", 6),
            soundUri = p.getString("soundUri", null),
            soundName = p.getString("soundName", "Default alarm") ?: "Default alarm",
            vibrate = p.getBoolean("vibrate", true)
        )
    }

    fun saveSettings(ctx: Context, s: AppSettings) {
        prefs(ctx).edit()
            .putInt("firstWait", s.firstWait)
            .putInt("interval", s.interval)
            .putInt("maxReminders", s.maxReminders)
            .putString("soundUri", s.soundUri)
            .putString("soundName", s.soundName)
            .putBoolean("vibrate", s.vibrate)
            .apply()
    }

    // ---------- running session ----------
    fun session(ctx: Context): Session? {
        val raw = prefs(ctx).getString("session", null) ?: return null
        return try {
            val o = JSONObject(raw)
            Session(o.getLong("ateAt"), o.getInt("fired"), o.getLong("nextAt"), itemsFromJson(o.getJSONArray("items")))
        } catch (e: Exception) {
            null
        }
    }

    fun saveSession(ctx: Context, s: Session?) {
        val e = prefs(ctx).edit()
        if (s == null) {
            e.remove("session")
        } else {
            val o = JSONObject()
                .put("ateAt", s.ateAt)
                .put("fired", s.fired)
                .put("nextAt", s.nextAt)
                .put("items", itemsToJson(s.items))
            e.putString("session", o.toString())
        }
        e.commit()
    }

    // ---------- history ----------
    fun history(ctx: Context): MutableList<HistoryEntry> {
        val raw = prefs(ctx).getString("history", null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        val out = mutableListOf<HistoryEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(HistoryEntry(o.getLong("ateAt"), o.getLong("endedAt"), o.getString("status"), itemsFromJson(o.getJSONArray("items"))))
        }
        return out
    }

    fun addHistory(ctx: Context, entry: HistoryEntry) {
        val list = history(ctx)
        list.add(0, entry)
        while (list.size > 300) list.removeAt(list.size - 1)
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("ateAt", it.ateAt)
                    .put("endedAt", it.endedAt)
                    .put("status", it.status)
                    .put("items", itemsToJson(it.items))
            )
        }
        prefs(ctx).edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory(ctx: Context) {
        prefs(ctx).edit().remove("history").apply()
    }

    private fun itemsToJson(items: List<SessionItem>): JSONArray {
        val arr = JSONArray()
        items.forEach { item ->
            val taken = JSONArray()
            item.taken.forEach { taken.put(it) }
            arr.put(
                JSONObject()
                    .put("medId", item.medId)
                    .put("name", item.name)
                    .put("total", item.total)
                    .put("taken", taken)
            )
        }
        return arr
    }

    private fun itemsFromJson(arr: JSONArray): MutableList<SessionItem> {
        val out = mutableListOf<SessionItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.getJSONArray("taken")
            val taken = mutableListOf<Long>()
            for (j in 0 until t.length()) taken.add(t.getLong(j))
            out.add(SessionItem(o.getLong("medId"), o.getString("name"), o.getInt("total"), taken))
        }
        return out
    }
}
