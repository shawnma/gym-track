package com.gymtrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

// 进度持久化 (gymtrack.json in filesDir)。只存进度，课表由 Schedule.kt 的 Gzclp 决定。
object Store {
    private fun file(c: Context) = File(c.filesDir, "gymtrack.json")

    fun load(c: Context): State {
        val today = LocalDate.now()
        val f = file(c)
        if (!f.exists()) return Schedule.defaultState(today)
        return try {
            val o = JSONObject(f.readText())
            val wd = o.optJSONArray("weekdays")
                ?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }
                ?.takeIf { it.isNotEmpty() } ?: Gzclp.DEFAULT_WEEKDAYS
            var idx = o.optInt("programIndex", 0)
            if (idx < 0 || idx >= Gzclp.program.size) idx = 0
            val next = o.optString("nextDate").takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: Schedule.firstSlotOnOrAfter(today, wd)
            val hist = o.optJSONArray("history")?.let { a ->
                (0 until a.length()).map {
                    val h = a.getJSONObject(it)
                    HistoryEntry(h.getString("date"), h.getString("dayName"))
                }
            } ?: emptyList()
            State(wd, idx, next, hist)
        } catch (e: Exception) {
            Schedule.defaultState(today)
        }
    }

    fun save(c: Context, s: State) {
        val o = JSONObject()
        o.put("weekdays", JSONArray(s.weekdays.sorted()))
        o.put("programIndex", s.programIndex)
        o.put("nextDate", s.nextDate.toString())
        val ha = JSONArray()
        s.history.forEach { ha.put(JSONObject().put("date", it.date).put("dayName", it.dayName)) }
        o.put("history", ha)
        file(c).writeText(o.toString(2))
    }

    fun update(c: Context, transform: (State) -> State): State {
        val s = transform(load(c))
        save(c, s)
        return s
    }
}
