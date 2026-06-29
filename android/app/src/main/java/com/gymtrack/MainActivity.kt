package com.gymtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val WD = "日一二三四五六"

// 家庭杠铃+哑铃可做的常见动作，供编辑时下拉选择 (仍可手动输入其它名称)。
private val COMMON_LIFTS = listOf(
    // 杠铃 Barbell
    "杠铃深蹲 Squat",
    "杠铃前蹲 Front Squat",
    "杠铃卧推 Bench",
    "窄握卧推 Close-Grip Bench",
    "杠铃推举 OHP",
    "杠铃硬拉 Deadlift",
    "罗马尼亚硬拉 RDL",
    "杠铃俯身划船 Barbell Row",
    "杠铃臀推 Hip Thrust",
    "杠铃弯举 Barbell Curl",
    "杠铃耸肩 Barbell Shrug",
    "杠铃健腹轮 Barbell Rollout",
    "杠铃箭步蹲 Barbell Lunge",
    // 哑铃 Dumbbell
    "哑铃卧推 DB Bench",
    "哑铃上斜卧推 DB Incline Press",
    "哑铃推举 DB Shoulder Press",
    "哑铃单臂划船 DB One-Arm Row",
    "哑铃高脚杯深蹲 Goblet Squat",
    "保加利亚分腿蹲 Bulgarian Split Squat",
    "哑铃箭步蹲 DB Lunge",
    "哑铃罗马尼亚硬拉 DB RDL",
    "哑铃侧平举 Lateral Raise",
    "哑铃飞鸟 DB Fly",
    "哑铃俯身飞鸟 Rear Delt Fly",
    "哑铃弯举 DB Curl",
    "锤式弯举 Hammer Curl",
    "哑铃过顶臂屈伸 DB Triceps Ext",
    "哑铃耸肩 DB Shrug",
    // 自重 Bodyweight (家庭器械可做)
    "引体向上 Pull-up",
    "双杠臂屈伸 Dip",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { Root() }
            }
        }
    }
}

private enum class Screen { HOME, EDIT }

@Composable
private fun Root() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(Store.load(context)) }
    var screen by remember { mutableStateOf(Screen.HOME) }

    fun apply(transform: (State) -> State) {
        state = Store.update(context, transform)
        scope.launch { GymWidget().updateAll(context) }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(state, onEdit = { screen = Screen.EDIT }, apply = ::apply)
        Screen.EDIT -> EditScreen(
            state = state,
            onSave = { weekdays, program ->
                apply { Schedule.applyEdits(it, LocalDate.now(), weekdays, program) }
                screen = Screen.HOME
            },
            onCancel = { screen = Screen.HOME },
        )
    }
}

@Composable
private fun HomeScreen(state: State, onEdit: () -> Unit, apply: ((State) -> State) -> Unit) {
    val today = LocalDate.now()
    val st = Schedule.todayStatus(state, today)
    val isTrain = st.kind == Schedule.Kind.TODAY || st.kind == Schedule.Kind.OVERDUE

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("GymTrack · GZCLP", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))

        Text(
            if (isTrain) "今天 ${st.day.name} 💪" else "今天 · 休息",
            style = MaterialTheme.typography.titleMedium
        )
        if (!isTrain) {
            Text("下次 周${WD[Schedule.weekdayIndex(st.date)]} ${st.date} · ${st.day.name}",
                style = MaterialTheme.typography.bodyMedium)
        } else if (st.kind == Schedule.Kind.OVERDUE) {
            Text("(原定 ${st.date}，待补)", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        st.day.exercises.forEach { ex ->
            Text("${ex.tier}  ${ex.lift}  ${ex.scheme}", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(18.dp))
        Row {
            Button(onClick = { apply { Schedule.complete(it, today) } }) { Text("✅ 完成") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = { apply { Schedule.defer(it) } }) { Text("⏭️ 顺延一天") }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { apply { Schedule.reset(it, today) } }) { Text("↺ 重置进度") }

        Spacer(Modifier.height(20.dp))
        Text("接下来", style = MaterialTheme.typography.titleSmall)
        Schedule.computeUpcoming(state, 5).drop(1).forEach {
            Text("${it.date}  周${WD[Schedule.weekdayIndex(it.date)]}  ${it.day.name}",
                style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "训练星期：" + state.weekdays.sorted().joinToString("") { WD[it].toString() } +
                "（单位 ${Gzclp.UNITS}）",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onEdit) { Text("✏️ 编辑课表 / 训练星期") }
    }
}

@Composable
private fun EditScreen(
    state: State,
    onSave: (Set<Int>, List<ProgramDay>) -> Unit,
    onCancel: () -> Unit,
) {
    var weekdays by remember { mutableStateOf(state.weekdays) }
    var program by remember { mutableStateOf(state.program) }

    fun updateDay(di: Int, f: (ProgramDay) -> ProgramDay) {
        program = program.toMutableList().also { it[di] = f(it[di]) }
    }
    fun updateEx(di: Int, ei: Int, f: (Exercise) -> Exercise) {
        updateDay(di) { d -> d.copy(exercises = d.exercises.toMutableList().also { it[ei] = f(it[ei]) }) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("编辑课表", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))

        // ── 训练星期 ────────────────────────────────────────────────
        Text("训练星期", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0..6) {
                val on = i in weekdays
                val toggle = { weekdays = if (on) weekdays - i else weekdays + i }
                if (on) {
                    Button(onClick = toggle, modifier = Modifier.width(42.dp),
                        contentPadding = PaddingValues(0.dp)) { Text(WD[i].toString()) }
                } else {
                    OutlinedButton(onClick = toggle, modifier = Modifier.width(42.dp),
                        contentPadding = PaddingValues(0.dp)) { Text(WD[i].toString()) }
                }
            }
        }
        if (weekdays.isEmpty()) {
            Text("至少选一天，否则保存时会恢复默认一三五。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(18.dp))
        Text("训练日 (按顺序循环)", style = MaterialTheme.typography.titleSmall)

        program.forEachIndexed { di, day ->
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = day.name,
                onValueChange = { v -> updateDay(di) { it.copy(name = v) } },
                label = { Text("第 ${di + 1} 天名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row {
                TextButton(onClick = {
                    if (di > 0) program = program.toMutableList().also {
                        val t = it[di]; it[di] = it[di - 1]; it[di - 1] = t
                    }
                }, enabled = di > 0) { Text("↑ 上移") }
                TextButton(onClick = {
                    if (di < program.lastIndex) program = program.toMutableList().also {
                        val t = it[di]; it[di] = it[di + 1]; it[di + 1] = t
                    }
                }, enabled = di < program.lastIndex) { Text("↓ 下移") }
                TextButton(onClick = {
                    if (program.size > 1) program = program.toMutableList().also { it.removeAt(di) }
                }, enabled = program.size > 1) { Text("🗑 删除日") }
            }

            day.exercises.forEachIndexed { ei, ex ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = ex.tier,
                        onValueChange = { v -> updateEx(di, ei) { it.copy(tier = v) } },
                        label = { Text("级") },
                        singleLine = true,
                        modifier = Modifier.width(72.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    LiftField(
                        value = ex.lift,
                        onValueChange = { v -> updateEx(di, ei) { it.copy(lift = v) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = ex.scheme,
                        onValueChange = { v -> updateEx(di, ei) { it.copy(scheme = v) } },
                        label = { Text("组×次 (如 3×5+)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        updateDay(di) { d -> d.copy(exercises = d.exercises.toMutableList().also { it.removeAt(ei) }) }
                    }) { Text("✕") }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = {
                updateDay(di) { d -> d.copy(exercises = d.exercises + Exercise("T3", "新动作", "3×10")) }
            }) { Text("＋ 加动作") }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = {
            val n = program.size + 1
            program = program + ProgramDay("D$n", "第 $n 天", listOf(Exercise("T1", "新动作", "3×5+")))
        }) { Text("＋ 加训练日") }

        Spacer(Modifier.height(8.dp))
        Text("提示：改了训练星期，保存后会把下一次自动排到最近的新训练日；重量进阶仍自己记。",
            style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)

        Spacer(Modifier.height(20.dp))
        Row {
            Button(onClick = { onSave(weekdays, program) }) { Text("💾 保存") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = onCancel) { Text("取消") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// 动作名输入框：可手动输入，也可从常见杠铃/哑铃动作下拉里选 (按已输入文字过滤)。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiftField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = if (value.isBlank()) COMMON_LIFTS
        else COMMON_LIFTS.filter { it.contains(value, ignoreCase = true) && it != value }
    val open = expanded && suggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text("动作") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = open,
            onDismissRequest = { expanded = false },
        ) {
            suggestions.take(12).forEach { s ->
                DropdownMenuItem(
                    text = { Text(s) },
                    onClick = { onValueChange(s); expanded = false },
                )
            }
        }
    }
}
