package com.gymtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val WD = "日一二三四五六"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

@Composable
private fun Screen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(Store.load(context)) }
    val today = LocalDate.now()
    val st = Schedule.todayStatus(state, today)
    val isTrain = st.kind == Schedule.Kind.TODAY || st.kind == Schedule.Kind.OVERDUE

    fun apply(transform: (State) -> State) {
        state = Store.update(context, transform)
        scope.launch { GymWidget().updateAll(context) }
    }

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
        Text("改课表/动作/训练星期：编辑 Schedule.kt 里的 Gzclp，重新构建即可。",
            style = MaterialTheme.typography.bodySmall)
    }
}
