package com.v2ray.ang.ui.main

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    /** True when a start would run Local proxy · Direct (no node selected / explicit direct mode). */
    startsLocalProxyDirect: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = { onAction(MainAction.TestCurrentServer) })
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            AppDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = displayText
                    }
                )
            }
        }
        // Short color tween makes start/stop feel like a state change rather
        // than a hard repaint; the debounce swallows accidental double-taps so
        // repeated connect/disconnect requests can't race the service.
        val fabColor by animateColorAsState(
            targetValue = if (isRunning) MaterialTheme.colorScheme.secondary
            else if (isDarkTheme) colorFabInactiveDark
            else colorFabInactiveLight,
            animationSpec = tween(200),
            label = "fabColor"
        )
        // The running FAB is painted with the scheme's secondary, which is a light
        // color in dark mode and can be anything at all under Material You. A
        // hardcoded white icon disappeared on it; onSecondary is the color the
        // scheme guarantees to be readable there. The idle gray is a fixed
        // constant, so it keeps its fixed white.
        val fabContentColor by animateColorAsState(
            targetValue = if (isRunning) MaterialTheme.colorScheme.onSecondary else Color.White,
            animationSpec = tween(200),
            label = "fabContentColor"
        )
        var lastToggleAt by remember { mutableLongStateOf(0L) }
        FloatingActionButton(
            onClick = {
                val now = SystemClock.elapsedRealtime()
                if (now - lastToggleAt >= 600L) {
                    lastToggleAt = now
                    onAction(MainAction.ToggleService)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp)
                .offset(y = (-28).dp)
                .navigationBarsPadding(),
            containerColor = fabColor
        ) {
            Icon(
                painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                else painterResource(R.drawable.ic_play_24dp),
                // With no node selected the FAB starts Local proxy · Direct (same as
                // the empty-list button), so say that instead of a vague "start service".
                contentDescription = stringResource(
                    when {
                        isRunning -> R.string.acc_stop
                        startsLocalProxyDirect -> R.string.action_start_local_proxy_direct
                        else -> R.string.acc_start
                    }
                ),
                tint = fabContentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
