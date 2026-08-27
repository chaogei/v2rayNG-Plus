package com.v2ray.ang.ui.settings

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : BaseViewModel(application) {

    /**
     * Checks for root access and requests it if necessary.
     * Updates [isLoading] during the process.
     */
    fun checkAndRequestRoot(onSuccess: () -> Unit) {
        launchLoading {
            val hasRoot = withContext(Dispatchers.IO) {
                RootManager.refresh()
            }
            if (hasRoot) {
                onSuccess()
            } else {
                toastError(R.string.toast_root_required)
            }
        }
    }

    /**
     * Validates if the given string is a valid observatory duration.
     * Shows error toast if invalid.
     * @return The trimmed value if valid, null otherwise.
     */
    fun validateObservatoryDuration(value: String): String? {
        val duration = value.trim()
        return if (AppConfig.OBSERVATORY_DURATION_PATTERN.matches(duration)) {
            duration
        } else {
            toastError(R.string.toast_invalid_observatory_duration)
            null
        }
    }

    /**
     * Validates if the given string is a valid observatory sampling value.
     * Shows error toast if invalid.
     * @return The value if valid, null otherwise.
     */
    fun validateObservatorySampling(value: String): String? {
        val sampling = value.trim().toIntOrNull()?.takeIf { it > 0 }
        return if (sampling != null) {
            sampling.toString()
        } else {
            toastError(R.string.toast_invalid_observatory_sampling)
            null
        }
    }

    /**
     * Validates a local inbound port. An empty input resets the field to
     * [defaultPort]. Shows an error toast when the port is out of range or
     * collides with another local inbound port.
     * @return The normalized port string if valid, null otherwise.
     */
    fun validateLocalPort(value: String, defaultPort: String, vararg conflictPorts: Int?): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return defaultPort
        }
        val port = trimmed.toIntOrNull()
        if (port == null || port !in 1..65535) {
            toastError(R.string.toast_invalid_local_port)
            return null
        }
        if (conflictPorts.filterNotNull().contains(port)) {
            toastError(R.string.toast_local_port_conflict)
            return null
        }
        return port.toString()
    }

    /**
     * Warns when local auth is switched on while credentials are incomplete.
     */
    fun warnIfLocalAuthCredentialsMissing(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            toastError(R.string.toast_local_auth_credentials_missing)
        }
    }
}