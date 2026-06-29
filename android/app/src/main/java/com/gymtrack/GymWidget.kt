package com.gymtrack

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.LocalDate

private val WD = "日一二三四五六"

class GymWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = Store.load(context)
        provideContent { Content(state) }
    }

    @Composable
    private fun Content(state: State) {
        val today = LocalDate.now()
        val st = Schedule.todayStatus(state, today)
        val isTrain = st.kind == Schedule.Kind.TODAY || st.kind == Schedule.Kind.OVERDUE

        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ColorProvider(Color(0xFF14141F)))
                .padding(14.dp)
        ) {
            Text(
                "GYM · 周${WD[Schedule.weekdayIndex(today)]}",
                style = TextStyle(color = ColorProvider(Color(0xFF9A9AB0)), fontSize = 11.sp)
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                if (isTrain) "今天 ${st.day.name} 💪" else "今天 · 休息",
                style = TextStyle(
                    color = ColorProvider(if (isTrain) Color.White else Color(0xFF8E8E93)),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            )
            if (isTrain && st.kind == Schedule.Kind.OVERDUE) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    "(原定 ${st.date}，待补)",
                    style = TextStyle(color = ColorProvider(Color(0xFFE0A030)), fontSize = 11.sp)
                )
            }
            Spacer(GlanceModifier.height(6.dp))

            // 动作列表占据中间剩余空间并可滚动 (内容多于组件高度时不再被裁掉)，
            // 完成/顺延按钮始终固定在底部。
            if (isTrain) {
                LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    items(st.day.exercises) { ex ->
                        Row(GlanceModifier.padding(bottom = 3.dp)) {
                            Text("${ex.tier} ", style = TextStyle(
                                color = ColorProvider(Color(0xFF9A9AB0)), fontSize = 12.sp, fontWeight = FontWeight.Medium))
                            Text("${ex.lift}  ${ex.scheme}", style = TextStyle(
                                color = ColorProvider(Color.White), fontSize = 13.sp))
                        }
                    }
                }
            } else {
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Text("下次 周${WD[Schedule.weekdayIndex(st.date)]} ${st.date} · ${st.day.name}",
                        style = TextStyle(color = ColorProvider(Color(0xFF34C759)), fontSize = 13.sp))
                }
            }

            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Button(text = "✅ 完成", onClick = actionRunCallback<CompleteAction>())
                Spacer(GlanceModifier.width(8.dp))
                Button(text = "⏭️ 顺延", onClick = actionRunCallback<DeferAction>())
            }
        }
    }
}
