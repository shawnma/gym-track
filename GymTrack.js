// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: deep-blue; icon-glyph: dumbbell;

// ============================================================================
// GymTrack — iPhone 主屏小组件 (Scriptable) · GZCLP 课表
//
// GZCLP 4 天循环 (A1/B1/A2/B2)，每周三次按固定星期 (默认一三五) 滚动练习。
//  - 主屏小组件：显示今天/明天/后天 该不该练、练哪天、练哪些动作
//  - 点开小组件 → 弹菜单：完成 / 顺延一天 / 设置
//  - 状态存 iCloud 文件 gymtrack.json，跨设备同步
//  - 练的过程中随时点开记/改当天各动作重量 + 轻重感受 (偏轻/合适/偏重)，
//    与「完成」互相独立；下次训练直接显示上次记录
//  - 不自动进阶，偏轻↑提示该加重 (进阶规则见 README)
//
// 单文件设计：既能在 Scriptable 跑，也能被 Node require 来跑纯函数自检。
// ============================================================================

// ── 0. 默认配置 (GZCLP；`+` = 末组 AMRAP 尽力做) ────────────────────────────
const DEFAULTS = {
  units: "lb",
  program: [
    {
      "id": "A1",
      "name": "A1 (推举日)",
      "exercises": [
        { "tier": "T1", "lift": "杠铃推举 OHP", "scheme": "3×5+" },
        { "tier": "T2", "lift": "杠铃深蹲 Squat", "scheme": "3×10" },
        { "tier": "T3", "lift": "杠铃健腹轮 Barbell Rollout", "scheme": "3×15+" },
        { "tier": "T3", "lift": "哑铃侧平举 Lateral Raise", "scheme": "3×15+" }
      ]
    },
    {
      "id": "B1",
      "name": "B1 (卧推日)",
      "exercises": [
        { "tier": "T1", "lift": "杠铃卧推 Bench", "scheme": "3×5+" },
        { "tier": "T2", "lift": "杠铃硬拉 Deadlift", "scheme": "3×10" },
        { "tier": "T3", "lift": "杠铃俯身划船 Barbell Row", "scheme": "3×15+" },
        { "tier": "T3", "lift": "哑铃/杠铃弯举 Bicep Curl", "scheme": "3×15+" }
      ]
    },
    {
      "id": "A2",
      "name": "A2 (深蹲日)",
      "exercises": [
        { "tier": "T1", "lift": "杠铃深蹲 Squat", "scheme": "3×5+" },
        { "tier": "T2", "lift": "杠铃推举 OHP", "scheme": "3×10" },
        { "tier": "T3", "lift": "哑铃单臂划船 DB Single Arm Row", "scheme": "3×15+" },
        { "tier": "T3", "lift": "哑铃俯身飞鸟 Rear Delt Fly", "scheme": "3×15+" }
      ]
    },
    {
      "id": "B2",
      "name": "B2 (硬拉日)",
      "exercises": [
        { "tier": "T1", "lift": "杠铃硬拉 Deadlift", "scheme": "3×5+" },
        { "tier": "T2", "lift": "杠铃卧推 Bench", "scheme": "3×10" },
        { "tier": "T3", "lift": "哑铃仰卧臂屈伸 Skullcrusher", "scheme": "3×15+" },
        { "tier": "T3", "lift": "长凳俯卧挺身/RDL Bench Hyperextension", "scheme": "3×15+" }
      ]
    }
  ],
  weekdays: [1, 3, 5],  // 训练星期：0=周日 1=周一 ... 6=周六 (默认一三五)
  programIndex: 0,      // 下一次练 program 里的第几个 (0..N-1)
  nextDate: null,       // 下一次待办日期 "YYYY-MM-DD"，首次运行时填
  history: [],          // { date, dayName }[]
  weights: {},          // 重量记录："T1|杠铃深蹲 Squat" → [{date,w,verdict}]，T1/T2 分开记
};

// ── 1. 日期工具 (纯函数，全部按本地零点处理，避免时区误差) ──────────────────
function parseDate(s) {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d); // 本地零点
}
function fmtDate(dt) {
  const y = dt.getFullYear();
  const m = String(dt.getMonth() + 1).padStart(2, "0");
  const d = String(dt.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}
function addDays(s, n) {
  const dt = parseDate(s);
  dt.setDate(dt.getDate() + n);
  return fmtDate(dt);
}
function weekdayOf(s) {
  return parseDate(s).getDay();
}
// 比较 "YYYY-MM-DD" 字符串：字典序即时间序，可直接 < > ==
function cmp(a, b) {
  return a < b ? -1 : a > b ? 1 : 0;
}

// 严格大于 from、且星期 ∈ weekdays 的最早日期
function nextSlotAfter(from, weekdays) {
  let d = addDays(from, 1);
  for (let i = 0; i < 14; i++) {
    if (weekdays.includes(weekdayOf(d))) return d;
    d = addDays(d, 1);
  }
  return d; // weekdays 为空时的兜底 (理论上不会到这)
}
// 大于等于 from、且星期 ∈ weekdays 的最早日期 (用于初始化 nextDate)
function firstSlotOnOrAfter(from, weekdays) {
  let d = from;
  for (let i = 0; i < 14; i++) {
    if (weekdays.includes(weekdayOf(d))) return d;
    d = addDays(d, 1);
  }
  return d;
}

// ── 2. 调度纯函数 ──────────────────────────────────────────────────────────
const N = (s) => s.program.length;

// program 元素可能是对象 {name,...} 或 (旧数据) 字符串；统一取显示名
function progName(el) {
  return el == null ? null : typeof el === "string" ? el : el.name;
}

// 点「完成」：记录历史 → 指针前进 → 回到固定网格的下一个槽
function complete(state, today) {
  state.history.push({ date: today, dayName: progName(state.program[state.programIndex]) });
  state.programIndex = (state.programIndex + 1) % N(state);
  state.nextDate = nextSlotAfter(today, state.weekdays);
  return state;
}

// 点「顺延一天」：当前这次往后挪一天；若挪到/越过下一个固定槽，则吸附回网格
// (即"取代下一次"，其余程序日因 programIndex 只在 complete 时 +1 而自然顺次后移)
function defer(state) {
  const candidate = addDays(state.nextDate, 1);
  const slot = nextSlotAfter(state.nextDate, state.weekdays);
  state.nextDate = cmp(candidate, slot) >= 0 ? slot : candidate;
  return state;
}

// 从 nextDate / programIndex 向前推算未来 count 次训练 session (day 为整个对象)
function computeUpcoming(state, count) {
  const out = [];
  let d = state.nextDate;
  let idx = state.programIndex;
  for (let i = 0; i < count; i++) {
    out.push({ date: d, day: state.program[idx] });
    idx = (idx + 1) % N(state);
    d = nextSlotAfter(d, state.weekdays);
  }
  return out;
}

// 今日状态：用于小组件主文案。day=下一次待办的 program 日对象
function todayStatus(state, today) {
  const next = state.nextDate;
  const day = state.program[state.programIndex];
  const dayName = progName(day);
  const c = cmp(next, today);
  if (c < 0) return { kind: "overdue", dayName, day, date: next };   // 过期未练
  if (c === 0) return { kind: "today", dayName, day, date: next };   // 今天该练
  if (next === addDays(today, 1)) return { kind: "tomorrow", dayName, day, date: next };
  return { kind: "rest", dayName, day, date: next };                 // 今天休息，下次在 next
}

// 给定一个日历日，返回当天的 program 日对象（没有则 null）。用于 widget 的今天/明天/后天。
function sessionOn(date, upcoming) {
  const hit = upcoming.find((s) => s.date === date);
  return hit ? hit.day : null;
}

// ── 3. 重量记录 (纯函数) ───────────────────────────────────────────────────
// verdict: "light"=偏轻(下次可加重) / "ok"=合适 / "heavy"=偏重(没完成次数)
const VERDICT_LABEL = { light: "偏轻", ok: "合适", heavy: "偏重" };
const VERDICT_ARROW = { light: "↑", ok: "✓", heavy: "↓" };

// 同一动作 T1/T2 是两套独立重量，所以 key 带 tier
function weightKey(ex) {
  return `${ex.tier}|${ex.lift}`;
}
function lastWeight(state, ex) {
  const arr = (state.weights || {})[weightKey(ex)];
  return arr && arr.length ? arr[arr.length - 1] : null;
}
function recordWeight(state, ex, weight, verdict, date) {
  if (!state.weights) state.weights = {};
  const k = weightKey(ex);
  const arr = state.weights[k] || (state.weights[k] = []);
  const entry = { date, w: weight, verdict };
  // 同一天重复记 = 修改当天记录 (练的过程中可随时改)，不同天才追加
  if (arr.length && arr[arr.length - 1].date === date) arr[arr.length - 1] = entry;
  else arr.push(entry);
  if (arr.length > 30) arr.splice(0, arr.length - 30); // 每个动作最多留 30 条
  return state;
}
// 今天已记的那条 (没有则 null)
function todayWeight(state, ex, today) {
  const last = lastWeight(state, ex);
  return last && last.date === today ? last : null;
}
// 上次记录的短文案，如 "135lb↑"；无记录返回 null
function lastWeightBrief(state, ex) {
  const last = lastWeight(state, ex);
  return last ? `${last.w}${state.units}${VERDICT_ARROW[last.verdict] || ""}` : null;
}

// ============================================================================
// 以下为 Scriptable 专属 (存储 / 渲染 / 交互)。Node 下 (无 FileManager) 不执行。
// ============================================================================
function todayStr() {
  return fmtDate(new Date());
}

function withDefaults(raw) {
  const s = Object.assign({}, DEFAULTS, raw || {});
  // 课表(program)与单位始终以代码里的 DEFAULTS 为准，不从 json 读，
  // 这样改了课表/动作就立即生效。json 只保存「进度」(下面几项)。
  s.program = DEFAULTS.program;
  s.units = DEFAULTS.units;
  if (!Array.isArray(s.weekdays) || !s.weekdays.length) s.weekdays = DEFAULTS.weekdays;
  if (s.programIndex == null || s.programIndex >= s.program.length) s.programIndex = 0;
  if (!Array.isArray(s.history)) s.history = [];
  if (s.weights == null || typeof s.weights !== "object" || Array.isArray(s.weights)) s.weights = {};
  if (!s.nextDate) s.nextDate = firstSlotOnOrAfter(todayStr(), s.weekdays);
  return s;
}

// ── 存储：iCloud 文件 (不可用时退回本地) ────────────────────────────────────
function getFM() {
  try {
    const fm = FileManager.iCloud();
    fm.documentsDirectory();
    return fm;
  } catch (e) {
    return FileManager.local();
  }
}
function dataPath(fm) {
  return fm.joinPath(fm.documentsDirectory(), "gymtrack.json");
}
function load() {
  const fm = getFM();
  const p = dataPath(fm);
  if (!fm.fileExists(p)) return withDefaults(null);
  try {
    if (fm.isFileStoredIniCloud && fm.isFileStoredIniCloud(p) && !fm.isFileDownloaded(p)) {
      fm.downloadFileFromiCloud(p);
    }
  } catch (e) {}
  try {
    return withDefaults(JSON.parse(fm.readString(p)));
  } catch (e) {
    return withDefaults(null);
  }
}
function save(state) {
  const fm = getFM();
  // 只持久化「进度」，不存 program/units(它们由代码 DEFAULTS 决定)
  const persist = {
    weekdays: state.weekdays,
    programIndex: state.programIndex,
    nextDate: state.nextDate,
    history: state.history,
    weights: state.weights,
  };
  fm.writeString(dataPath(fm), JSON.stringify(persist, null, 2));
}

// ── 渲染 widget ────────────────────────────────────────────────────────────
const TRAIN_COLOR = () => new Color("#34c759"); // 绿
const REST_COLOR = () => new Color("#8e8e93");  // 灰
const BG_TOP = () => new Color("#1c1c2e");
const BG_BOT = () => new Color("#11111b");

function dayLabel(name) {
  return name == null ? "休息" : name;
}

// 上次重量的着色：偏轻→绿(该加重了) / 合适→灰 / 偏重→橙(注意降阶)
function verdictColor(verdict, dim) {
  if (dim) return new Color("#6a6a80");
  if (verdict === "light") return TRAIN_COLOR();
  if (verdict === "heavy") return new Color("#ff9f0a");
  return new Color("#9a9ab0");
}

// 给 widget 加一行动作："T1 深蹲 Squat  5×3+  135lb↑"
function addExerciseRow(w, ex, dim, state) {
  const row = w.addStack();
  row.centerAlignContent();
  const t = row.addText(ex.tier + " ");
  t.font = Font.semiboldSystemFont(11);
  t.textColor = new Color(dim ? "#6a6a80" : "#9a9ab0");
  const l = row.addText(`${ex.lift}  ${ex.scheme}`);
  l.font = Font.mediumSystemFont(13);
  l.textColor = dim ? REST_COLOR() : Color.white();
  const brief = lastWeightBrief(state, ex);
  if (brief) {
    const wt = row.addText(`  ${brief}`);
    wt.font = Font.semiboldSystemFont(12);
    wt.textColor = verdictColor(lastWeight(state, ex).verdict, dim);
  }
  w.addSpacer(3);
}

function buildWidget(state) {
  const w = new ListWidget();
  const g = new LinearGradient();
  g.colors = [BG_TOP(), BG_BOT()];
  g.locations = [0, 1];
  w.backgroundGradient = g;
  w.setPadding(14, 20, 14, 16);

  const today = todayStr();
  const st = todayStatus(state, today);
  const upcoming = computeUpcoming(state, 8);
  const family = typeof config !== "undefined" ? config.widgetFamily : "small";
  const isTrainToday = st.kind === "today" || st.kind === "overdue";
  const exercises = (st.day && st.day.exercises) || [];

  // 标题行
  const wdToday = "日一二三四五六"[weekdayOf(today)];
  const title = w.addText(`GYM · 周${wdToday}`);
  title.font = Font.semiboldSystemFont(11);
  title.textColor = new Color("#9a9ab0");
  w.addSpacer(5);

  // 今日大字：训练日显示 day 名，休息日显示「休息」
  const big = w.addText(
    isTrainToday ? `今天 ${st.dayName} 💪` : "今天 · 休息"
  );
  big.font = Font.boldSystemFont(19);
  big.textColor = isTrainToday ? Color.white() : REST_COLOR();

  if (isTrainToday) {
    if (st.kind === "overdue") {
      const od = w.addText(`(原定 ${st.date.slice(5)}，待补)`);
      od.font = Font.systemFont(11);
      od.textColor = new Color("#e0a030");
    }
    w.addSpacer(7);
    // 动作：small 只显示 T1；medium/large 显示全部
    const list = family === "small" ? exercises.slice(0, 1) : exercises;
    for (const ex of list) addExerciseRow(w, ex, false, state);
  } else {
    const sub = w.addText(`下次 周${"日一二三四五六"[weekdayOf(st.date)]} ${st.date.slice(5)} · ${st.dayName}`);
    sub.font = Font.mediumSystemFont(13);
    sub.textColor = TRAIN_COLOR();
    w.addSpacer(6);
    const list = family === "small" ? exercises.slice(0, 1) : exercises;
    for (const ex of list) addExerciseRow(w, ex, true, state);
  }

  // large：再列出明天、后天
  if (family === "large") {
    w.addSpacer(8);
    for (let offset = 1; offset <= 2; offset++) {
      const cd = addDays(today, offset);
      const day = sessionOn(cd, upcoming);
      const row = w.addStack();
      const wd = "日一二三四五六"[weekdayOf(cd)];
      const lbl = row.addText(`${offset === 1 ? "明天" : "后天"} 周${wd}`);
      lbl.font = Font.systemFont(13);
      lbl.textColor = new Color("#b8b8c8");
      row.addSpacer();
      const val = row.addText(dayLabel(progName(day)));
      val.font = Font.mediumSystemFont(13);
      val.textColor = day == null ? REST_COLOR() : TRAIN_COLOR();
      w.addSpacer(4);
    }
  }

  w.addSpacer();
  const hint = w.addText("点一下 → 记重量 / 完成 / 顺延");
  hint.font = Font.systemFont(10);
  hint.textColor = new Color("#6a6a80");

  // 跨午夜后自动刷新到新一天
  const tomorrowMidnight = new Date();
  tomorrowMidnight.setHours(24, 0, 30, 0);
  w.refreshAfterDate = tomorrowMidnight;
  return w;
}

// ── 交互菜单 ───────────────────────────────────────────────────────────────
async function runInteractive(state) {
  const today = todayStr();
  const st = todayStatus(state, today);
  const upcoming = computeUpcoming(state, 5);

  const a = new Alert();
  a.title = "GymTrack";
  const lines = [];
  if (st.kind === "today") lines.push(`今天 ${st.dayName} 💪`);
  else if (st.kind === "overdue") lines.push(`待补 ${st.dayName} (原定 ${st.date})`);
  else if (st.kind === "tomorrow") lines.push(`今天休息 · 明天 ${st.dayName}`);
  else lines.push(`今天休息 · 下次 ${st.date} ${st.dayName}`);
  // 下一次训练的动作 (带今天/上次重量)
  for (const ex of (st.day && st.day.exercises) || []) {
    const last = lastWeight(state, ex);
    const tail = last
      ? ` · ${last.date === today ? "今天" : "上次"}${last.w}${state.units} ${VERDICT_LABEL[last.verdict] || ""}`
      : "";
    lines.push(`  ${ex.tier} ${ex.lift} ${ex.scheme}${tail}`);
  }
  lines.push("");
  lines.push("接下来：");
  for (const s of upcoming.slice(1, 4)) {
    const wd = "日一二三四五六"[weekdayOf(s.date)];
    lines.push(`  ${s.date} 周${wd} · ${progName(s.day)}`);
  }
  a.message = lines.join("\n");

  a.addAction("🏋️ 记重量");
  a.addAction("✅ 完成今天");
  a.addAction("⏭️ 顺延一天");
  a.addDestructiveAction("⚙️ 设置");
  a.addCancelAction("关闭");

  const idx = await a.presentAlert();
  if (idx === 0) {
    await weightsMenu(state, st.day, today);
  } else if (idx === 1) {
    complete(state, today);
    save(state);
    await toast(`已完成 · 下次 ${state.nextDate}`);
  } else if (idx === 2) {
    defer(state);
    save(state);
    await toast(`已顺延 · 下次 ${state.nextDate}`);
  } else if (idx === 3) {
    await settingsMenu(state);
  }
  // 退出前更新一次小组件预览
  Script.setWidget(buildWidget(state));
}

// 记重量主菜单：列出当次训练的动作，点哪个记哪个，可随时进来反复改。
// 每记一个立即落盘，练到最后忘了也不怕。
async function weightsMenu(state, day, today) {
  const exs = (day && day.exercises) || [];
  if (!exs.length) return;
  while (true) {
    const a = new Alert();
    a.title = `记重量 · ${progName(day)}`;
    a.message = "点动作记录/修改今天的重量 (随时可再进来改)";
    for (const ex of exs) {
      const rec = todayWeight(state, ex, today);
      const last = lastWeight(state, ex);
      const tail = rec
        ? ` — 今天 ${rec.w}${state.units} ${VERDICT_LABEL[rec.verdict] || ""}`
        : last
          ? ` — 上次 ${last.w}${state.units} ${VERDICT_LABEL[last.verdict] || ""}`
          : " — 未记";
      a.addAction(`${ex.tier} ${ex.lift}${tail}`);
    }
    a.addCancelAction("返回");
    const idx = await a.presentAlert();
    if (idx === -1) return;
    await editWeight(state, exs[idx], today);
    save(state);
  }
}

// 单个动作的记录/修改弹框
async function editWeight(state, ex, today) {
  const rec = todayWeight(state, ex, today);
  const last = lastWeight(state, ex);
  const ref = rec || last; // 预填今天已记的，否则上次的
  const a = new Alert();
  a.title = `${ex.tier} ${ex.lift}`;
  a.message =
    `${ex.scheme}` +
    (rec ? `\n今天已记 ${rec.w}${state.units} · ${VERDICT_LABEL[rec.verdict] || ""}` : "") +
    (last && last !== rec ? `\n上次 ${last.w}${state.units} · ${VERDICT_LABEL[last.verdict] || ""} (${last.date.slice(5)})` : "") +
    `\n输入重量，再选感觉：`;
  const tf = a.addTextField(`重量 (${state.units})`, ref ? String(ref.w) : "");
  if (tf && tf.setDecimalPadKeyboard) tf.setDecimalPadKeyboard();
  a.addAction("⬆️ 偏轻 (下次加重)");
  a.addAction("✅ 合适");
  a.addAction("⬇️ 偏重 (没完成次数)");
  a.addCancelAction("取消");
  const idx = await a.presentAlert();
  if (idx === -1) return;
  const w = parseFloat(a.textFieldValue(0));
  if (isNaN(w)) return; // 没填重量就不记
  recordWeight(state, ex, w, ["light", "ok", "heavy"][idx], today);
}

async function toast(msg) {
  const a = new Alert();
  a.title = "GymTrack";
  a.message = msg;
  a.addCancelAction("好");
  await a.presentAlert();
}

async function settingsMenu(state) {
  const a = new Alert();
  a.title = "设置";
  a.message =
    `课表 (GZCLP): ${state.program.map((d) => d.name).join(" / ")}\n` +
    `训练星期: ${weekdaysLabel(state.weekdays)}\n` +
    `单位: ${state.units}\n\n` +
    `动作内容如需修改，编辑脚本顶部的 DEFAULTS.program（改完即生效）`;
  a.addAction("改训练星期");
  a.addDestructiveAction("↺ 重置进度");
  a.addCancelAction("返回");
  const idx = await a.presentAlert();
  if (idx === 0) await editWeekdays(state);
  else if (idx === 1) await resetProgress(state);
}

// 重置进度：清空历史、回到第一天、下次设为最近的训练日 (课表不变)
async function resetProgress(state) {
  const a = new Alert();
  a.title = "重置进度？";
  a.message = "清空训练历史、回到课表第一天、下次设为最近的训练日。\n课表/动作与重量记录不变。";
  a.addDestructiveAction("确认重置");
  a.addCancelAction("取消");
  if ((await a.presentAlert()) !== 0) return;
  state.programIndex = 0;
  state.history = [];
  state.nextDate = firstSlotOnOrAfter(todayStr(), state.weekdays);
  save(state);
  await toast(`已重置 · 下次 ${state.nextDate} ${progName(state.program[0])}`);
}

function weekdaysLabel(wd) {
  return wd.slice().sort().map((d) => "日一二三四五六"[d]).join("");
}

async function editWeekdays(state) {
  const a = new Alert();
  a.title = "训练星期 (用数字 0-6，逗号分隔，0=周日)";
  a.addTextField("如 1,3,5", state.weekdays.join(","));
  a.addAction("保存");
  a.addCancelAction("取消");
  const idx = await a.presentAlert();
  if (idx !== 0) return;
  const wd = a
    .textFieldValue(0)
    .split(",")
    .map((s) => parseInt(s.trim(), 10))
    .filter((n) => n >= 0 && n <= 6);
  if (wd.length) {
    state.weekdays = Array.from(new Set(wd)).sort();
    if (!state.weekdays.includes(weekdayOf(state.nextDate))) {
      state.nextDate = firstSlotOnOrAfter(state.nextDate, state.weekdays);
    }
    save(state);
  }
}

// ── 5. 入口 (仅在 Scriptable 环境执行) ─────────────────────────────────────
if (typeof FileManager !== "undefined") {
  const state = load();
  if (typeof config !== "undefined" && config.runsInWidget) {
    // 小组件路径：全同步、不写盘 (widget 时间预算很小，写 iCloud 会超时)
    Script.setWidget(buildWidget(state));
    Script.complete();
  } else {
    // 仅在 App 里交互时才用 async + 落盘
    (async () => {
      save(state); // 首次/交互时把补全后的默认值落盘
      await runInteractive(state);
      Script.complete();
    })();
  }
}

// ── 导出 (Node 下用于测试；Scriptable 也有 module 对象，赋值无害) ───────────
if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    DEFAULTS,
    parseDate, fmtDate, addDays, weekdayOf, cmp,
    nextSlotAfter, firstSlotOnOrAfter, progName, withDefaults,
    complete, defer, computeUpcoming, todayStatus, sessionOn,
    VERDICT_LABEL, VERDICT_ARROW, weightKey, lastWeight, recordWeight, lastWeightBrief, todayWeight,
  };
}
