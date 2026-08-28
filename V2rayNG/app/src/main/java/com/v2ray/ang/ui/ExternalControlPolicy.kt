package com.v2ray.ang.ui

/**
 * Maps an external automation intent onto the one thing it is allowed to do.
 *
 * The entry point that uses this is exported, so the allow-list lives here rather than in
 * the activity: an action that is not one of the three documented ones resolves to
 * [Decision.IGNORE] and nothing else in the intent — component, data, extras — is read.
 */
object ExternalControlPolicy {

    /**
     * Fixed action strings, deliberately not derived from the application id: automations are
     * written once and must keep working across the flavours, which carry an id suffix.
     */
    const val ACTION_TOGGLE = "com.v2ray.ang.action.TOGGLE"
    const val ACTION_START = "com.v2ray.ang.action.START"
    const val ACTION_STOP = "com.v2ray.ang.action.STOP"

    enum class Decision {
        START,
        STOP,
        ALREADY_RUNNING,
        ALREADY_STOPPED,
        IGNORE,
    }

    fun decide(action: String?, isRunning: Boolean): Decision = when (action) {
        ACTION_TOGGLE -> if (isRunning) Decision.STOP else Decision.START
        ACTION_START -> if (isRunning) Decision.ALREADY_RUNNING else Decision.START
        ACTION_STOP -> if (isRunning) Decision.STOP else Decision.ALREADY_STOPPED
        else -> Decision.IGNORE
    }
}
