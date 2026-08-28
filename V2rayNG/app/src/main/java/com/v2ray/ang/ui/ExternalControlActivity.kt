package com.v2ray.ang.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastInfo
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.ui.ExternalControlPolicy.Decision
import com.v2ray.ang.util.LogUtil

/**
 * Lets automation apps and adb flip the service without opening the app, the way the
 * quick-settings tile and the widget already do from inside it.
 *
 * Runs in the daemon process because that is the only one where [CoreServiceManager.isRunning]
 * reflects the core; asking from the UI process would always answer "stopped" and turn a
 * toggle into a start. The window never draws: it decides, acts and finishes.
 */
class ExternalControlActivity : Activity() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val decision = ExternalControlPolicy.decide(action, CoreServiceManager.isRunning())
        LogUtil.i(AppConfig.TAG, "ExternalControl: $action resolved to $decision")

        when (decision) {
            // startServiceFromToggle already reports both the startup mode and any failure,
            // so a second toast here would only fight with it for the same screen corner.
            Decision.START -> LauncherManager.startServiceFromToggle(this)

            Decision.STOP -> {
                LauncherManager.stopService(this)
                toast(R.string.toast_services_stop)
            }

            Decision.ALREADY_RUNNING -> toastInfo(R.string.external_control_already_running)
            Decision.ALREADY_STOPPED -> toastInfo(R.string.external_control_already_stopped)
            Decision.IGNORE -> toastError(R.string.external_control_unknown_action)
        }

        finish()
    }
}
