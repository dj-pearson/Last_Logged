package com.pearsonmedia.lastlogged.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import com.pearsonmedia.lastlogged.R
import com.pearsonmedia.lastlogged.data.repository.TrackerRepository
import com.pearsonmedia.lastlogged.ui.MainActivity
import com.pearsonmedia.lastlogged.util.DeepLinks
import com.pearsonmedia.lastlogged.util.TimeFormatUtil
import com.pearsonmedia.lastlogged.util.WidgetItems
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

data class WidgetTrackerItem(
    val id: String,
    val name: String,
    val lastCompletedAt: Long?,
    val reminderIntervalDays: Int,
    val iconName: String
)

/**
 * Glance widgets are instantiated by the system, so they cannot be annotated
 * `@AndroidEntryPoint`. An `@EntryPoint` is the supported way to reach the Hilt
 * singleton graph from one.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun trackerRepository(): TrackerRepository
}

class LastLoggedWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            androidx.glance.appwidget.DpSize(120.dp, 120.dp),  // Small
            androidx.glance.appwidget.DpSize(250.dp, 120.dp),  // Medium
            androidx.glance.appwidget.DpSize(250.dp, 250.dp)   // Large
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = loadTopOverdue(context)

        provideContent {
            GlanceTheme {
                WidgetContent(context = context, items = items)
            }
        }
    }

    /**
     * Reads the user's real trackers. Glance does not observe Room, so this runs
     * once per refresh and takes the current value off the DAO's Flow.
     */
    private suspend fun loadTopOverdue(context: Context): List<WidgetTrackerItem> {
        return try {
            val repository = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .trackerRepository()

            WidgetItems
                .topOverdue(repository.getActiveItems().first(), MAX_ITEMS)
                .map { item ->
                    WidgetTrackerItem(
                        id = item.id,
                        name = item.name,
                        lastCompletedAt = item.lastCompletedAt,
                        reminderIntervalDays = item.reminderIntervalDays,
                        iconName = item.iconName
                    )
                }
        } catch (e: Exception) {
            // A widget that throws gets torn down by the launcher, so degrade to
            // the empty state and let the next refresh recover.
            emptyList()
        }
    }

    companion object {
        /** Fits the large widget; smaller sizes clip inside the LazyColumn. */
        const val MAX_ITEMS = 5
    }
}

@Composable
private fun WidgetContent(context: Context, items: List<WidgetTrackerItem>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
            .clickable(actionStartActivity(homeIntent(context)))
    ) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GlanceTheme.colors.onBackground
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (items.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_empty),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onBackground
                )
            )
        } else {
            LazyColumn {
                items(items) { item ->
                    WidgetRow(context = context, item = item)
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(context: Context, item: WidgetTrackerItem) {
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
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity(trackerIntent(context, item.id))),
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

/**
 * Widget taps arrive as a fresh Intent, so they are routed through the same
 * deep-link path as an external link instead of a second, parallel mechanism.
 */
private fun trackerIntent(context: Context, trackerId: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.trackerUri(trackerId)))
        .setClass(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun homeIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

class LastLoggedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LastLoggedWidget()
}
