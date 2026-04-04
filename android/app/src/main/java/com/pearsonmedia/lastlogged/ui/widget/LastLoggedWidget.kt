package com.pearsonmedia.lastlogged.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import com.pearsonmedia.lastlogged.util.TimeFormatUtil

data class WidgetTrackerItem(
    val id: String,
    val name: String,
    val lastCompletedAt: Long?,
    val reminderIntervalDays: Int,
    val iconName: String
)

class LastLoggedWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            androidx.glance.appwidget.DpSize(120.dp, 120.dp),  // Small
            androidx.glance.appwidget.DpSize(250.dp, 120.dp),  // Medium
            androidx.glance.appwidget.DpSize(250.dp, 250.dp)   // Large
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // TODO: Read from Room database via shared data
        // For now, use sample data
        val items = getSampleItems()

        provideContent {
            GlanceTheme {
                WidgetContent(items = items)
            }
        }
    }

    private fun getSampleItems(): List<WidgetTrackerItem> {
        return listOf(
            WidgetTrackerItem("1", "HVAC Filter", System.currentTimeMillis() - 86400000L * 95, 90, "air"),
            WidgetTrackerItem("2", "Oil Change", System.currentTimeMillis() - 86400000L * 85, 90, "oil_barrel"),
            WidgetTrackerItem("3", "Dental Checkup", System.currentTimeMillis() - 86400000L * 200, 180, "medical_services"),
            WidgetTrackerItem("4", "Tire Rotation", System.currentTimeMillis() - 86400000L * 170, 180, "tire_repair"),
            WidgetTrackerItem("5", "Haircut", System.currentTimeMillis() - 86400000L * 50, 42, "content_cut")
        )
    }
}

@Composable
private fun WidgetContent(items: List<WidgetTrackerItem>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
    ) {
        Text(
            text = "Top Overdue",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GlanceTheme.colors.onBackground
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (items.isEmpty()) {
            Text(
                text = "All caught up!",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onBackground
                )
            )
        } else {
            LazyColumn {
                items(items.take(5)) { item ->
                    WidgetRow(item)
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(item: WidgetTrackerItem) {
    val urgency = TimeFormatUtil.urgencyLevel(item.lastCompletedAt, item.reminderIntervalDays)
    val urgencyColor = when (urgency) {
        TimeFormatUtil.UrgencyLevel.GOOD -> ColorProvider(
            day = android.graphics.Color.parseColor("#10B981"),
            night = android.graphics.Color.parseColor("#6EE7B7")
        )
        TimeFormatUtil.UrgencyLevel.DUE_SOON -> ColorProvider(
            day = android.graphics.Color.parseColor("#F59E0B"),
            night = android.graphics.Color.parseColor("#FCD34D")
        )
        TimeFormatUtil.UrgencyLevel.OVERDUE -> ColorProvider(
            day = android.graphics.Color.parseColor("#EF4444"),
            night = android.graphics.Color.parseColor("#FCA5A5")
        )
    }
    val elapsed = TimeFormatUtil.elapsedTimeString(item.lastCompletedAt)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.name,
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = GlanceTheme.colors.onBackground
                ),
                maxLines = 1
            )
            Text(
                text = elapsed,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = urgencyColor
                )
            )
        }
    }
}

class LastLoggedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LastLoggedWidget()
}
