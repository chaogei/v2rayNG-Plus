package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.util.LogUtil

class TaskerReceiver : BroadcastReceiver() {

    /**
     * This method is called when the BroadcastReceiver is receiving an Intent broadcast.
     * It retrieves the bundle from the intent and checks the switch and guid values.
     * Depending on the switch value, it starts or stops the V2Ray service.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID).orEmpty()

            if (switch == null || TextUtils.isEmpty(guid)) {
                // Silence here used to look like the app ignoring Tasker; the task is almost
                // always one that was saved by an older build and no longer carries a profile.
                LogUtil.w(AppConfig.TAG, "Tasker: ignoring an action with no switch or profile")
                return
            } else if (switch) {
                if (guid == AppConfig.TASKER_DEFAULT_GUID) {
                    LogUtil.i(AppConfig.TAG, "Tasker: starting the selected profile")
                    LauncherManager.startServiceFromToggle(context)
                } else {
                    LogUtil.i(AppConfig.TAG, "Tasker: starting a specific profile")
                    LauncherManager.startService(context, guid)
                }
            } else {
                LogUtil.i(AppConfig.TAG, "Tasker: stopping the service")
                LauncherManager.stopService(context)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing Tasker broadcast", e)
        }
    }
}
