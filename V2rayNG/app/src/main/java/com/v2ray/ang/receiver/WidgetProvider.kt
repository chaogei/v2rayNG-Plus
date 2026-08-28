package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.NotificationTitlePolicy

class WidgetProvider : AppWidgetProvider() {
    /**
     * This method is called every time the widget is updated.
     * It updates the widget background based on the V2Ray service running state.
     *
     * @param context The Context in which the receiver is running.
     * @param appWidgetManager The AppWidgetManager instance.
     * @param appWidgetIds The appWidgetIds for which an update is needed.
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateWidgetBackground(context, appWidgetManager, appWidgetIds, CoreServiceManager.isRunning())
    }

    /**
     * Updates the widget background based on whether the V2Ray service is running.
     *
     * @param context The Context in which the receiver is running.
     * @param appWidgetManager The AppWidgetManager instance.
     * @param appWidgetIds The appWidgetIds for which an update is needed.
     * @param isRunning Boolean indicating if the V2Ray service is running.
     */
    private fun updateWidgetBackground(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, isRunning: Boolean) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_switch)
        val intent = Intent(context, WidgetProvider::class.java)
        intent.action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            R.id.layout_switch,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        remoteViews.setOnClickPendingIntent(R.id.layout_switch, pendingIntent)
        if (isRunning) {
            remoteViews.setInt(R.id.image_switch, "setImageResource", R.drawable.ic_stop_24dp)
            remoteViews.setInt(R.id.layout_background, "setBackgroundResource", R.drawable.ic_rounded_corner_active)
        } else {
            remoteViews.setInt(R.id.image_switch, "setImageResource", R.drawable.ic_play_24dp)
            remoteViews.setInt(R.id.layout_background, "setBackgroundResource", R.drawable.ic_rounded_corner_inactive)
        }
        // The widget is a single unlabelled image, so a screen reader announced
        // nothing at all. Name what the tap does and, like the tile and the
        // notification, say "direct" instead of a node name when there is none.
        remoteViews.setContentDescription(R.id.layout_switch, accessibilityLabel(context, isRunning))

        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    private fun accessibilityLabel(context: Context, isRunning: Boolean): String {
        if (!isRunning) return context.getString(R.string.acc_widget_stopped)
        val name = NotificationTitlePolicy.title(
            CoreServiceManager.getRunningServerName(),
            context.getString(R.string.title_local_proxy_direct)
        )
        return context.getString(R.string.acc_widget_running, name)
    }

    private fun refresh(context: Context, isRunning: Boolean) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        updateWidgetBackground(
            context,
            manager,
            manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java)),
            isRunning
        )
    }

    /**
     * This method is called when the BroadcastReceiver is receiving an Intent broadcast.
     * It handles widget click actions and updates the widget background based on the V2Ray service state.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (AppConfig.BROADCAST_ACTION_WIDGET_CLICK == intent.action) {
            // Nothing broadcasts when the daemon process is killed, so a widget can
            // sit on "running" indefinitely. The tap already acted on the real state;
            // repaint from it too instead of waiting for a state message that a
            // stopped core will never send.
            val nowRunning = if (CoreServiceManager.isRunning()) {
                LauncherManager.stopService(context)
                false
            } else {
                LauncherManager.startServiceFromToggle(context)
            }
            refresh(context, nowRunning)
        } else if (AppConfig.BROADCAST_ACTION_ACTIVITY == intent.action) {
            when (intent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS ->
                    refresh(context, true)

                AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_START_FAILURE, AppConfig.MSG_STATE_STOP_SUCCESS ->
                    refresh(context, false)
            }
        }
    }
}
