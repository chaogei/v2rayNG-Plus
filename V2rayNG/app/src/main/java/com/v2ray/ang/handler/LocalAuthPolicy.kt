package com.v2ray.ang.handler

/**
 * Local inbound auth: the toggle and the credential fields are independent.
 * The switch is only considered on when both username and password are filled.
 */
object LocalAuthPolicy {

    fun credentialsComplete(username: String?, password: String?): Boolean {
        return !username.isNullOrBlank() && !password.isNullOrBlank()
    }

    /** What the UI switch should show. Incomplete credentials never look enabled. */
    fun displayEnabled(toggleOn: Boolean, username: String?, password: String?): Boolean {
        return toggleOn && credentialsComplete(username, password)
    }

    /**
     * Persist the toggle only when turning it off, or turning it on with both
     * credentials already present. Turning it on with empty fields is rejected.
     */
    fun persistEnabled(wantOn: Boolean, username: String?, password: String?): Boolean? {
        if (!wantOn) return false
        if (!credentialsComplete(username, password)) return null
        return true
    }
}
