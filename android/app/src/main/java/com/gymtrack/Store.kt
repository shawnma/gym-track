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
            // 课表：存在就读，否则回退出厂课表 (兼容旧数据 / iOS 版没有该字段)
            val prog = o.optJSONArray("program")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    runCatching {
                        val dj = a.getJSONObject(i)
                        val exA = dj.optJSONArray("exercises")
                        val ex = if (exA == null) emptyList() else (0 until exA.length()).map { k ->
                            val ej = exA.getJSONObject(k)
                            Exercise(ej.optString("tier"), ej.optString("lift"), ej.optString("scheme"))
                        }
                        ProgramDay(dj.optString("id"), dj.optString("name"), ex)
                    }.getOrNull()
                }
            }?.takeIf { it.isNotEmpty() } ?: Gzclp.program
            if (idx >= prog.size) idx = 0
            State(wd, idx, next, hist, prog)
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
        val pa = JSONArray()
        s.program.forEach { day ->
            val ea = JSONArray()
            day.exercises.forEach { ex ->
                ea.put(JSONObject().put("tier", ex.tier).put("lift", ex.lift).put("scheme", ex.scheme))
            }
            pa.put(JSONObject().put("id", day.id).put("name", day.name).put("exercises", ea))
        }
        o.put("program", pa)
        file(c).writeText(o.toString(2))
    }

    fun update(c: Context, transform: (State) -> State): State {
        val s = transform(load(c))
        save(c, s)
        return s
    }
}
