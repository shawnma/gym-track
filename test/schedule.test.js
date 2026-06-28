// 纯函数自检 (Node)。运行: node test/schedule.test.js
const G = require("../GymTrack.js");
const assert = require("assert");

let pass = 0;
function eq(actual, expected, msg) {
  assert.deepStrictEqual(actual, expected, `${msg}\n  expected: ${JSON.stringify(expected)}\n  actual:   ${JSON.stringify(actual)}`);
  pass++;
  console.log("  ✓ " + msg);
}

// 基准：固定一三五 (weekdays=[1,3,5])，GZCLP 4 天循环 A1 B1 A2 B2
function gzclpProgram() {
  return ["A1", "B1", "A2", "B2"].map((n) => ({
    id: n, name: n,
    exercises: [
      { tier: "T1", lift: "lift1", scheme: "5×3+" },
      { tier: "T2", lift: "lift2", scheme: "3×10" },
      { tier: "T3", lift: "引体 Pull-up", scheme: "3×15+" },
    ],
  }));
}
function fresh(nextDate, programIndex = 0) {
  return {
    units: "lb",
    program: gzclpProgram(),
    weekdays: [1, 3, 5],
    programIndex,
    nextDate,
    history: [],
  };
}
const curName = (s) => s.program[s.programIndex].name;

// 已知锚点：2026-06-29 是周一 (getDay()==1)
console.log("\n[日期工具]");
eq(G.weekdayOf("2026-06-29"), 1, "2026-06-29 是周一");
eq(G.weekdayOf("2026-07-01"), 3, "2026-07-01 是周三");
eq(G.weekdayOf("2026-07-03"), 5, "2026-07-03 是周五");
eq(G.nextSlotAfter("2026-06-29", [1, 3, 5]), "2026-07-01", "周一的下一槽=周三");
eq(G.nextSlotAfter("2026-07-03", [1, 3, 5]), "2026-07-06", "周五的下一槽=下周一");

console.log("\n[完成滚动 A1→B1→A2→B2→A1]");
{
  let s = fresh("2026-06-29", 0); // 周一, 该练 A1
  G.complete(s, "2026-06-29");
  eq([curName(s), s.nextDate], ["B1", "2026-07-01"], "完成A1 → 下次周三练B1");
  G.complete(s, "2026-07-01");
  eq([curName(s), s.nextDate], ["A2", "2026-07-03"], "完成B1 → 下次周五练A2");
  G.complete(s, "2026-07-03");
  eq([curName(s), s.nextDate], ["B2", "2026-07-06"], "完成A2 → 下周一练B2");
  G.complete(s, "2026-07-06");
  eq([curName(s), s.nextDate], ["A1", "2026-07-08"], "完成B2 → 回到A1，周三");
  eq(s.history.map((h) => h.dayName), ["A1", "B1", "A2", "B2"], "history 记录了 A1 B1 A2 B2");
}

console.log("\n[单次顺延：停在空档日，周三/五不变]");
{
  let s = fresh("2026-06-29", 0); // 周一 该练 A1
  G.defer(s);
  eq(s.nextDate, "2026-06-30", "周一(A1)顺延 → 周二 (空档日，未越过周三)");
  eq(s.programIndex, 0, "顺延不改 programIndex (仍是A1)");
  G.complete(s, "2026-06-30");
  eq([curName(s), s.nextDate], ["B1", "2026-07-01"], "周二补练A1后，周三仍练B1");
}

console.log("\n[连续顺延：吸附回网格并取代下一次]");
{
  let s = fresh("2026-06-29", 0); // 周一 该练 A1
  G.defer(s); // → 周二
  eq(s.nextDate, "2026-06-30", "第一次顺延 → 周二");
  G.defer(s); // 周二+1=周三, 下一槽=周三 → 吸附
  eq(s.nextDate, "2026-07-01", "第二次顺延 → 吸附到周三 (取代原本的B1槽)");
  eq(curName(s), "A1", "周三这次仍练A1 (取代了B1)");
  G.complete(s, "2026-07-01");
  eq([curName(s), s.nextDate], ["B1", "2026-07-03"], "完成A1后 B1 落到周五 (日期仍一三五)");
}

console.log("\n[顺延后未来排程仍落在一三五]");
{
  let s = fresh("2026-06-29", 0);
  G.defer(s);
  G.defer(s); // 吸附到周三
  const up = G.computeUpcoming(s, 4).map((x) => `${x.date}/${x.day.name}/周${"日一二三四五六"[G.weekdayOf(x.date)]}`);
  eq(up, [
    "2026-07-01/A1/周三",
    "2026-07-03/B1/周五",
    "2026-07-06/A2/周一",
    "2026-07-08/B2/周三",
  ], "未来 4 次都落在一三五，程序日整体后移一格");
}

console.log("\n[computeUpcoming 返回完整 day 对象 (含动作)]");
{
  let s = fresh("2026-07-01", 1); // 周三 B1
  const first = G.computeUpcoming(s, 1)[0];
  eq(first.day.name, "B1", "首项 day.name=B1");
  eq(first.day.exercises.length, 3, "day 带 3 个动作 (T1/T2/T3)");
}

console.log("\n[todayStatus 文案分支 + 携带 day 对象]");
{
  let s = fresh("2026-07-01", 1); // 周三 该练 B1
  eq(G.todayStatus(s, "2026-07-01").kind, "today", "当天 → today");
  eq(G.todayStatus(s, "2026-07-01").day.name, "B1", "today 携带 day=B1");
  eq(G.todayStatus(s, "2026-06-30").kind, "tomorrow", "前一天 → tomorrow");
  eq(G.todayStatus(s, "2026-06-29").kind, "rest", "更早 → rest");
  eq(G.todayStatus(s, "2026-07-02").kind, "overdue", "已过期 → overdue");
}

console.log("\n[sessionOn 返回 day 对象或 null]");
{
  let s = fresh("2026-07-01", 1);
  const up = G.computeUpcoming(s, 4);
  eq(G.progName(G.sessionOn("2026-07-01", up)), "B1", "周三这天 = B1");
  eq(G.sessionOn("2026-07-02", up), null, "周四不是训练日 = null");
}

console.log("\n[兼容旧字符串 program (progName)]");
{
  eq(G.progName("A"), "A", "字符串元素 → 名字本身");
  eq(G.progName({ name: "A1" }), "A1", "对象元素 → .name");
  eq(G.progName(null), null, "null → null");
}

console.log("\n[跨周滚动正确性：连练12次日期与程序日对齐]");
{
  let s = fresh("2026-06-29", 0);
  const seen = [];
  for (let i = 0; i < 12; i++) {
    seen.push(`${s.nextDate}/${curName(s)}`);
    G.complete(s, s.nextDate);
  }
  eq(seen, [
    "2026-06-29/A1", "2026-07-01/B1", "2026-07-03/A2",
    "2026-07-06/B2", "2026-07-08/A1", "2026-07-10/B1",
    "2026-07-13/A2", "2026-07-15/B2", "2026-07-17/A1",
    "2026-07-20/B1", "2026-07-22/A2", "2026-07-24/B2",
  ], "12 次连练：日期始终一三五，program 连续滚动");
}

console.log(`\n✅ 全部通过 (${pass} 断言)\n`);
