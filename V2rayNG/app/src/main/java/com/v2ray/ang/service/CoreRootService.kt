package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.root.RootProxyManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service for the root (system-wide) run modes. Unlike [CoreVpnService] it
 * does not use Android VpnService — traffic is routed by iptables instead
 * (see [RootProxyManager]).
 *
 * The in-process core is started first (so its listener is up and the foreground
 * notification is posted promptly), then the root routing rules are installed off the
 * main thread. On teardown the rules are removed before the core stops.
 */
class CoreRootService : Service(), ServiceControl {

    // Written from the main thread, but stopService() can also arrive from the setup
    // coroutine itself when the root setup fails.
    @Volatile
    private var setupJob: Job? = null

    /** Teardown outlives the service instance, so it cannot use the service's own scope. */
    private val teardownScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val teardownStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service created")
        CoreServiceManager.serviceControl = SoftReference(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationManager.ensureForeground()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: command received")

        if (CoreServiceManager.isRunning()) {
            LogUtil.i(AppConfig.TAG, "StartCore-Root: Core is already running")
            return START_STICKY
        }

        CoreServiceManager.refreshRuntimeSocksPort()

        // Start the in-process core first (this also posts the foreground notification),
        // then install the root routing off the main thread.
        if (!CoreServiceManager.startCoreLoop(null)) {
            LogUtil.e(AppConfig.TAG, "StartCore-Root: core failed to start")
            stopService()
            return START_NOT_STICKY
        }

        setupJob = CoroutineScope(Dispatchers.IO).launch {
            // The root scripts are uninterruptible blocking IO, so cancellation can only be
            // observed between them; RootProxyManager checks this before installing anything.
            val started = RootProxyManager.start(this@CoreRootService) { ensureActive() }
            if (!started) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: failed to start root mode, stopping")
                // The core already reported a successful start; without this the UI shows
                // "connected" then silently flips to disconnected with no reason.
                MessageHelper.sendMsg2UI(
                    this@CoreRootService,
                    AppConfig.MSG_STATE_START_FAILURE,
                    getString(R.string.toast_root_setup_failed),
                )
                stopService()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safety net for stops that never went through stopService() (system kill, replaced
        // service): the teardown itself is async and only ever runs once.
        startTeardown()
    }

    override fun getService(): Service = this

    override fun startService() {
        // do nothing
    }

    override fun stopService() {
        // stopSelf() is deferred until teardown finished so the foreground service keeps the
        // daemon process alive: a process death midway would leave the iptables rules behind
        // and break the device's connectivity.
        startTeardown { stopSelf() }
    }

    /**
     * Remove the root routing and stop the core, off the main thread.
     *
     * Both steps block: the setup coroutine sits in an uninterruptible `Process.waitFor`
     * (the setup script alone waits up to 6s for the tun to appear) and the teardown script
     * runs with a 30s timeout. Doing this in [onDestroy] on the main thread is a guaranteed
     * ANR, so onDestroy only triggers it.
     *
     * The setup job is joined first: it is cancelled but can still be inside a script, and
     * letting the teardown overtake it would re-install rules + a tun pointing at a core that
     * is about to die, blackholing all traffic until the next start/stop cycle.
     */
    private fun startTeardown(onFinished: (() -> Unit)? = null) {
        val pendingSetup = setupJob
        setupJob = null
        pendingSetup?.cancel()

        if (!teardownStarted.compareAndSet(false, true)) {
            onFinished?.invoke()
            return
        }

        val context = applicationContext
        teardownScope.launch {
            try {
                pendingSetup?.join()
                // Remove routing rules BEFORE stopping the core so traffic is never
                // redirected to a dead listener.
                RootProxyManager.stop(context)
                CoreServiceManager.stopCoreLoop()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: teardown failed", e)
            } finally {
                onFinished?.invoke()
            }
        }
    }

    override fun vpnProtect(socket: Int): Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let(AppLocaleManager::localizedContext)
        super.attachBaseContext(context)
    }
}
