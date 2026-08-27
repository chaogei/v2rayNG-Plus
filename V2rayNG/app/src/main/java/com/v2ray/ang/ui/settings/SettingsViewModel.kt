package com.v2ray.ang.ui.settings

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.LocalInboundSnapshot
import com.v2ray.ang.enums.LocalInboundMode
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
     * Warns while the local auth credentials are incomplete: the toggle stays on but
     * config generation falls back to "noauth" until both fields are filled in.
     */
    fun warnIfLocalAuthCredentialsMissing(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            toastError(R.string.toast_local_auth_credentials_missing)
        }
    }

    /**
     * Warns right when 0.0.0.0 is selected without working authentication, not only
     * at the next service start: an unauthenticated inbound open to the LAN is the
     * single most dangerous switch on this screen.
     */
    fun warnIfUnauthenticatedLanSharing(sharing: Boolean, authInEffect: Boolean) {
        if (sharing && !authInEffect) {
            toastError(R.string.toast_warning_pref_proxysharing_unauthenticated)
        }
    }

    /**
     * Resolve an HTTP/SOCKS port collision when a local inbound mode with a dedicated HTTP
     * port is selected.
     *
     * Modes without one leave the HTTP port field disabled and out of the conflict
     * validation, so a mode that has one can be entered with both ports equal. Config
     * generation resolves that to the neighbour port on its own, leaving the settings screen
     * showing a port nothing listens on.
     *
     * @return the port to store, or null when the current one is fine.
     */
    fun normalizeHttpPortOnModeChange(mode: LocalInboundMode, socksPort: Int, httpPort: Int): Int? {
        val hasSeparateHttpPort = mode == LocalInboundMode.SOCKS_HTTP || mode == LocalInboundMode.HTTP
        if (!hasSeparateHttpPort || httpPort != socksPort) {
            return null
        }
        val normalized = LocalInboundSnapshot.neighborPort(socksPort)
        toast(getString(R.string.toast_http_port_normalized, normalized))
        return normalized
    }
}