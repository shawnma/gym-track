package com.gymtrack

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import java.time.LocalDate

// 在桌面小组件上点「完成」
class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.update(context) { Schedule.complete(it, LocalDate.now()) }
        GymWidget().update(context, glanceId)
    }
}

// 在桌面小组件上点「顺延一天」
class DeferAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.update(context) { Schedule.defer(it) }
        GymWidget().update(context, glanceId)
    }
}
