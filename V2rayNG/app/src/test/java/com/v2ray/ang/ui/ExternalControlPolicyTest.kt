package com.v2ray.ang.ui

import com.v2ray.ang.ui.ExternalControlPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalControlPolicyTest {

    @Test
    fun toggleStartsWhenStopped() {
        assertEquals(
            Decision.START,
            ExternalControlPolicy.decide(ExternalControlPolicy.ACTION_TOGGLE, isRunning = false)
        )
    }

    @Test
    fun toggleStopsWhenRunning() {
        assertEquals(
            Decision.STOP,
            ExternalControlPolicy.decide(ExternalControlPolicy.ACTION_TOGGLE, isRunning = true)
        )
    }

    @Test
    fun startOnARunningServiceIsReportedRatherThanRepeated() {
        assertEquals(
            Decision.ALREADY_RUNNING,
            ExternalControlPolicy.decide(ExternalControlPolicy.ACTION_START, isRunning = true)
        )
    }

    @Test
    fun startWhenStopped() {
        assertEquals(
            Decision.START,
            ExternalControlPolicy.decide(ExternalControlPolicy.ACTION_START, isRunning = false)
        )
    }

    @Test
    fun stopWhenRunning() {
        assertEquals(
            Decision.STOP,
            ExternalControlPolicy.decide(ExternalControlPolicy.ACTION_STOP, isRunning = true)
        )
    }

    @Test
    fun stopOnAStoppedServiceIsANoOp() {
        assertEquals(
            Decision.ALREADY_STOPPED,
            ExternalControlPolicy.decide(ExternalControlPolicy.ACTION_STOP, isRunning = false)
        )
    }

    @Test
    fun unknownActionsAreRefused() {
        val refused = listOf(
            null,
            "",
            "android.intent.action.VIEW",
            "android.intent.action.MAIN",
            "com.v2ray.ang.action.toggle",
            "com.v2ray.ang.action.TOGGLE ",
            "com.v2ray.ang.action.TOGGLE_EVERYTHING",
            "com.github.metacubex.clash.meta.action.TOGGLE_CLASH",
        )
        refused.forEach { action ->
            assertEquals(action, Decision.IGNORE, ExternalControlPolicy.decide(action, isRunning = false))
            assertEquals(action, Decision.IGNORE, ExternalControlPolicy.decide(action, isRunning = true))
        }
    }

    @Test
    fun actionsStayStableAcrossFlavours() {
        // Flavours carry an application id suffix; automations are written once against these.
        assertEquals("com.v2ray.ang.action.TOGGLE", ExternalControlPolicy.ACTION_TOGGLE)
        assertEquals("com.v2ray.ang.action.START", ExternalControlPolicy.ACTION_START)
        assertEquals("com.v2ray.ang.action.STOP", ExternalControlPolicy.ACTION_STOP)
    }
}
