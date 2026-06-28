package com.gymtrack

import java.time.LocalDate

// 纯逻辑自检 (用 Android Studio 自带 kotlinc 编译运行)。
// 校验移植后的 Kotlin 调度逻辑与 iOS 版 GymTrack.js 一致。

private var pass = 0
private fun eq(actual: Any?, expected: Any?, msg: String) {
    if (actual != expected) {
        System.err.println("  ✗ $msg\n    expected: $expected\n    actual:   $actual")
        throw AssertionError(msg)
    }
    pass++
    println("  ✓ $msg")
}

private fun d(s: String) = LocalDate.parse(s)
private fun curName(s: State) = Gzclp.program[s.programIndex].name

fun main() {
    // 锚点：2026-06-29 周一
    println("\n[日期工具]")
    eq(Schedule.weekdayIndex(d("2026-06-29")), 1, "2026-06-29 是周一")
    eq(Schedule.weekdayIndex(d("2026-07-01")), 3, "2026-07-01 是周三")
    eq(Schedule.weekdayIndex(d("2026-07-03")), 5, "2026-07-03 是周五")
    eq(Schedule.nextSlotAfter(d("2026-06-29"), setOf(1,3,5)).toString(), "2026-07-01", "周一的下一槽=周三")
    eq(Schedule.nextSlotAfter(d("2026-07-03"), setOf(1,3,5)).toString(), "2026-07-06", "周五的下一槽=下周一")

    fun fresh(date: String, idx: Int = 0) =
        State(setOf(1,3,5), idx, d(date), emptyList())

    println("\n[完成滚动 A1→B1→A2→B2→A1]")
    run {
        var s = fresh("2026-06-29")
        s = Schedule.complete(s, d("2026-06-29"))
        eq(listOf(curName(s), s.nextDate.toString()), listOf("B1 (卧推日)", "2026-07-01"), "完成A1 → 周三B1")
        s = Schedule.complete(s, d("2026-07-01"))
        eq(listOf(curName(s), s.nextDate.toString()), listOf("A2 (深蹲日)", "2026-07-03"), "完成B1 → 周五A2")
        s = Schedule.complete(s, d("2026-07-03"))
        eq(listOf(curName(s), s.nextDate.toString()), listOf("B2 (硬拉日)", "2026-07-06"), "完成A2 → 下周一B2")
        s = Schedule.complete(s, d("2026-07-06"))
        eq(listOf(curName(s), s.nextDate.toString()), listOf("A1 (推举日)", "2026-07-08"), "完成B2 → 回到A1，周三")
        eq(s.history.map { it.dayName }, listOf("A1 (推举日)","B1 (卧推日)","A2 (深蹲日)","B2 (硬拉日)"), "history 记录 A1 B1 A2 B2")
    }

    println("\n[单次顺延：停在空档日，周三/五不变]")
    run {
        var s = fresh("2026-06-29")
        s = Schedule.defer(s)
        eq(s.nextDate.toString(), "2026-06-30", "周一(A1)顺延 → 周二")
        eq(s.programIndex, 0, "顺延不改 programIndex")
        s = Schedule.complete(s, d("2026-06-30"))
        eq(listOf(curName(s), s.nextDate.toString()), listOf("B1 (卧推日)", "2026-07-01"), "周二补练A1后，周三仍B1")
    }

    println("\n[连续顺延：吸附回网格并取代下一次]")
    run {
        var s = fresh("2026-06-29")
        s = Schedule.defer(s)
        eq(s.nextDate.toString(), "2026-06-30", "第一次顺延 → 周二")
        s = Schedule.defer(s)
        eq(s.nextDate.toString(), "2026-07-01", "第二次顺延 → 吸附到周三")
        eq(curName(s), "A1 (推举日)", "周三仍练A1 (取代B1)")
        s = Schedule.complete(s, d("2026-07-01"))
        eq(listOf(curName(s), s.nextDate.toString()), listOf("B1 (卧推日)", "2026-07-03"), "完成A1后 B1 落到周五")
    }

    println("\n[顺延后未来排程仍落在一三五]")
    run {
        var s = fresh("2026-06-29")
        s = Schedule.defer(s); s = Schedule.defer(s)
        val up = Schedule.computeUpcoming(s, 4).map { "${it.date}/${it.day.id}" }
        eq(up, listOf("2026-07-01/A1","2026-07-03/B1","2026-07-06/A2","2026-07-08/B2"), "未来4次都在一三五，程序日后移一格")
    }

    println("\n[todayStatus 分支]")
    run {
        val s = fresh("2026-07-01", 1)
        eq(Schedule.todayStatus(s, d("2026-07-01")).kind, Schedule.Kind.TODAY, "当天 → TODAY")
        eq(Schedule.todayStatus(s, d("2026-06-30")).kind, Schedule.Kind.TOMORROW, "前一天 → TOMORROW")
        eq(Schedule.todayStatus(s, d("2026-06-29")).kind, Schedule.Kind.REST, "更早 → REST")
        eq(Schedule.todayStatus(s, d("2026-07-02")).kind, Schedule.Kind.OVERDUE, "已过期 → OVERDUE")
        eq(Schedule.todayStatus(s, d("2026-07-01")).day.exercises.size, 4, "B1 带 4 个动作")
    }

    println("\n[reset 回到第一天]")
    run {
        var s = fresh("2026-07-03", 2)
        s = Schedule.complete(s, d("2026-07-03"))
        s = Schedule.reset(s, d("2026-06-29"))
        eq(listOf(s.programIndex, s.history.size, s.nextDate.toString()), listOf(0, 0, "2026-06-29"), "reset → idx0/无历史/最近训练日")
    }

    println("\n[跨周滚动 12 次对齐]")
    run {
        var s = fresh("2026-06-29")
        val seen = ArrayList<String>()
        repeat(12) {
            seen.add("${s.nextDate}/${Gzclp.program[s.programIndex].id}")
            s = Schedule.complete(s, s.nextDate)
        }
        eq(seen, listOf(
            "2026-06-29/A1","2026-07-01/B1","2026-07-03/A2",
            "2026-07-06/B2","2026-07-08/A1","2026-07-10/B1",
            "2026-07-13/A2","2026-07-15/B2","2026-07-17/A1",
            "2026-07-20/B1","2026-07-22/A2","2026-07-24/B2",
        ), "12次连练：日期始终一三五，program 连续滚动")
    }

    println("\n✅ 全部通过 ($pass 断言)\n")
}
