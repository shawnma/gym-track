package com.gymtrack

import java.time.LocalDate

// ============================================================================
// GZCLP 课表 + 调度纯函数 (与 iOS Scriptable 版 GymTrack.js 同逻辑)
//  - 4 天循环 A1→B1→A2→B2，每周 3 次按固定星期 (默认一三五) 滚动
//  - 不依赖 Android，可独立用 kotlinc 跑测试
// ============================================================================

data class Exercise(val tier: String, val lift: String, val scheme: String)
data class ProgramDay(val id: String, val name: String, val exercises: List<Exercise>)
data class HistoryEntry(val date: String, val dayName: String)

// 进度状态 (持久化的部分)。课表 program 由代码定义，不存盘。
data class State(
    val weekdays: Set<Int>,        // 0=周日 1=周一 ... 6=周六 (默认一三五)
    val programIndex: Int,         // 下一次练 program 里的第几个 (0..N-1)
    val nextDate: LocalDate,       // 下一次待办日期
    val history: List<HistoryEntry>
)

// ── GZCLP 课表 (改这里即生效；与 iOS 版 DEFAULTS.program 保持一致) ───────────
object Gzclp {
    const val UNITS = "lb"
    val DEFAULT_WEEKDAYS = setOf(1, 3, 5) // 一三五

    val program: List<ProgramDay> = listOf(
        ProgramDay("A1", "A1 (推举日)", listOf(
            Exercise("T1", "杠铃推举 OHP", "3×5+"),
            Exercise("T2", "杠铃深蹲 Squat", "3×10"),
            Exercise("T3", "哑铃单臂划船 DB One-Arm Row", "3×15+"),
            Exercise("T3", "哑铃侧平举 Lateral Raise", "3×15+"),
        )),
        ProgramDay("B1", "B1 (卧推日)", listOf(
            Exercise("T1", "杠铃卧推 Bench", "3×5+"),
            Exercise("T2", "杠铃硬拉 Deadlift", "3×10"),
            Exercise("T3", "杠铃俯身划船 Barbell Row", "3×15+"),
            Exercise("T3", "哑铃/杠铃弯举 Bicep Curl", "3×15+"),
        )),
        ProgramDay("A2", "A2 (深蹲日)", listOf(
            Exercise("T1", "杠铃深蹲 Squat", "3×5+"),
            Exercise("T2", "杠铃推举 OHP", "3×10"),
            Exercise("T3", "引体向上 Pull-up", "3×15+"),
            Exercise("T3", "杠铃健腹轮 Barbell Rollout", "3×15+"),
        )),
        ProgramDay("B2", "B2 (硬拉日)", listOf(
            Exercise("T1", "杠铃硬拉 Deadlift", "3×5+"),
            Exercise("T2", "杠铃卧推 Bench", "3×10"),
            Exercise("T3", "哑铃俯身飞鸟 Rear Delt Fly", "3×15+"),
            Exercise("T3", "长凳俯卧挺身 Bench Hyperextension", "3×15+"),
        )),
    )
}

object Schedule {
    private val N get() = Gzclp.program.size

    // java.time: Mon=1..Sun=7 → 取模 7 得到 0=周日..6=周六 (与 JS getDay 一致)
    fun weekdayIndex(d: LocalDate): Int = d.dayOfWeek.value % 7

    // 严格大于 from、且星期 ∈ weekdays 的最早日期
    fun nextSlotAfter(from: LocalDate, weekdays: Set<Int>): LocalDate {
        var d = from.plusDays(1)
        repeat(14) {
            if (weekdayIndex(d) in weekdays) return d
            d = d.plusDays(1)
        }
        return d
    }

    // 大于等于 from、且星期 ∈ weekdays 的最早日期 (用于初始化 nextDate)
    fun firstSlotOnOrAfter(from: LocalDate, weekdays: Set<Int>): LocalDate {
        var d = from
        repeat(14) {
            if (weekdayIndex(d) in weekdays) return d
            d = d.plusDays(1)
        }
        return d
    }

    // 点「完成」：记录历史 → 指针前进 → 回到固定网格的下一个槽
    fun complete(s: State, today: LocalDate): State {
        val day = Gzclp.program[s.programIndex]
        return s.copy(
            history = s.history + HistoryEntry(today.toString(), day.name),
            programIndex = (s.programIndex + 1) % N,
            nextDate = nextSlotAfter(today, s.weekdays),
        )
    }

    // 点「顺延一天」：往后挪一天；若挪到/越过下一个固定槽则吸附回网格 (取代下一次)
    fun defer(s: State): State {
        val candidate = s.nextDate.plusDays(1)
        val slot = nextSlotAfter(s.nextDate, s.weekdays)
        val next = if (!candidate.isBefore(slot)) slot else candidate // candidate >= slot → slot
        return s.copy(nextDate = next)
    }

    // 重置进度：回到第一天、清空历史、下次设为最近训练日 (课表不变)
    fun reset(s: State, today: LocalDate): State = s.copy(
        programIndex = 0,
        history = emptyList(),
        nextDate = firstSlotOnOrAfter(today, s.weekdays),
    )

    data class Upcoming(val date: LocalDate, val day: ProgramDay)

    // 从 nextDate / programIndex 向前推算未来 count 次训练
    fun computeUpcoming(s: State, count: Int): List<Upcoming> {
        val out = ArrayList<Upcoming>(count)
        var d = s.nextDate
        var idx = s.programIndex
        repeat(count) {
            out.add(Upcoming(d, Gzclp.program[idx]))
            idx = (idx + 1) % N
            d = nextSlotAfter(d, s.weekdays)
        }
        return out
    }

    enum class Kind { TODAY, OVERDUE, TOMORROW, REST }
    data class Status(val kind: Kind, val day: ProgramDay, val date: LocalDate)

    fun todayStatus(s: State, today: LocalDate): Status {
        val day = Gzclp.program[s.programIndex]
        val next = s.nextDate
        return when {
            next.isBefore(today) -> Status(Kind.OVERDUE, day, next)
            next.isEqual(today) -> Status(Kind.TODAY, day, next)
            next.isEqual(today.plusDays(1)) -> Status(Kind.TOMORROW, day, next)
            else -> Status(Kind.REST, day, next)
        }
    }

    // 某日历日对应的 program 日 (没有则 null)
    fun sessionOn(date: LocalDate, upcoming: List<Upcoming>): ProgramDay? =
        upcoming.firstOrNull { it.date.isEqual(date) }?.day

    fun defaultState(today: LocalDate): State = State(
        weekdays = Gzclp.DEFAULT_WEEKDAYS,
        programIndex = 0,
        nextDate = firstSlotOnOrAfter(today, Gzclp.DEFAULT_WEEKDAYS),
        history = emptyList(),
    )
}
