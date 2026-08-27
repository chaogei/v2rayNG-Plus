package com.v2ray.ang.ui.compose

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Glassmorphism design system.
 *
 * Every visible surface in the app shares this recipe: a soft gradient
 * backdrop painted once at the theme root, translucent "frosted" panels with
 * hairline borders floating above it, and (API 31+) real window blur behind
 * dialogs. Below API 31 the same translucent fills plus borders are used as a
 * graceful fallback, no runtime blur is required anywhere else, so low-end
 * devices never pay for large-area blur.
 */

/** Unified corner radii: panels/cards 16-24dp, controls 12dp. */
val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Turn an opaque Material color scheme into its glass variant: the window
 * background becomes transparent (the gradient backdrop shows through) and
 * every surface role keeps its hue but gains translucency. Light surfaces sit
 * at 40-70% opacity, dark ones at 25-50%. surfaceTint is disabled so tonal
 * elevation does not muddy the translucent fills.
 */
fun ColorScheme.toGlassScheme(darkTheme: Boolean): ColorScheme {
    return if (darkTheme) copy(
        background = Color.Transparent,
        surface = surface.copy(alpha = 0.50f),
        surfaceVariant = surfaceVariant.copy(alpha = 0.35f),
        surfaceContainerLowest = surfaceContainerLowest.copy(alpha = 0.25f),
        surfaceContainerLow = surfaceContainerLow.copy(alpha = 0.32f),
        surfaceContainer = surfaceContainer.copy(alpha = 0.38f),
        surfaceContainerHigh = surfaceContainerHigh.copy(alpha = 0.44f),
        surfaceContainerHighest = surfaceContainerHighest.copy(alpha = 0.50f),
        surfaceTint = Color.Transparent,
    ) else copy(
        background = Color.Transparent,
        surface = surface.copy(alpha = 0.70f),
        surfaceVariant = surfaceVariant.copy(alpha = 0.50f),
        surfaceContainerLowest = surfaceContainerLowest.copy(alpha = 0.40f),
        surfaceContainerLow = surfaceContainerLow.copy(alpha = 0.48f),
        surfaceContainer = surfaceContainer.copy(alpha = 0.55f),
        surfaceContainerHigh = surfaceContainerHigh.copy(alpha = 0.62f),
        surfaceContainerHighest = surfaceContainerHighest.copy(alpha = 0.70f),
        surfaceTint = Color.Transparent,
    )
}

/** Hairline border for glass panels: a bright top-left edge fading out. */
@Composable
fun glassBorderBrush(): Brush {
    return if (LocalDarkTheme.current) {
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
        )
    } else {
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.30f))
        )
    }
}

@Composable
fun glassBorder(): BorderStroke = BorderStroke(1.dp, glassBorderBrush())

/**
 * Fill for surfaces that float in their own window without blur (dropdown
 * menus, drawer). Higher opacity than in-window panels keeps text readable
 * over arbitrary content.
 */
@Composable
fun glassOverlayColor(): Color {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    return base.copy(alpha = if (LocalDarkTheme.current) 0.90f else 0.94f)
}

/**
 * Fill for dialogs. Slightly more translucent than menus because dialogs get
 * blur-behind (API 31+) plus the dim scrim.
 */
@Composable
fun glassDialogColor(): Color {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    return base.copy(alpha = if (LocalDarkTheme.current) 0.80f else 0.85f)
}

/**
 * A frosted panel: rounded clip, translucent fill and hairline border.
 * [fill] defaults to the list-item tier; pass a stronger container color for
 * prominent panels. [borderBrush] can be overridden e.g. to accent-tint the
 * selected item.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape = GlassShapes.medium,
    fill: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderBrush: Brush = glassBorderBrush(),
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(1.dp, borderBrush, shape)

/**
 * Full-window gradient backdrop drawn once behind all content: a soft
 * two-tone base with three large color glows, giving the "blurred wallpaper"
 * depth that the frosted panels float on. Plain gradients are already smooth,
 * so no runtime blur is needed and the cost is a single draw pass.
 *
 * The neutral base gradient is shared by every theme; only the low-opacity
 * [glow] brand colors change, so all presets keep the same glass material.
 */
@Composable
fun AppGlassBackground(
    glow: GlassGlow = ThemePreset.AMBER.glassGlow(),
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val baseStart = if (dark) Color(0xFF12161F) else Color(0xFFF3F6FD)
    val baseEnd = if (dark) Color(0xFF191522) else Color(0xFFFDF5EE)
    val glowFirst = glow.first.copy(alpha = if (dark) 0.20f else 0.14f)
    val glowSecond = glow.second.copy(alpha = if (dark) 0.14f else 0.10f)
    val glowThird = glow.third.copy(alpha = if (dark) 0.16f else 0.10f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(baseStart, baseEnd),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                )
                drawGlow(glowFirst, center = Offset(size.width * 0.88f, size.height * 0.08f), radius = size.width * 0.75f)
                drawGlow(glowSecond, center = Offset(size.width * 0.08f, size.height * 0.42f), radius = size.width * 0.65f)
                drawGlow(glowThird, center = Offset(size.width * 0.55f, size.height * 0.95f), radius = size.width * 0.85f)
            }
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlow(
    color: Color,
    center: Offset,
    radius: Float,
) {
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** Frosted card container for a plain (non-reorderable) list row. */
@Composable
fun GlassListCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .glassPanel(fill = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        content()
    }
}

/**
 * Frost the content behind the current dialog window on API 31+. Older
 * versions silently keep the translucent-fill fallback. Also honored only
 * when the system has cross-window blur enabled, so it can never crash or
 * misrender.
 */
@Composable
fun GlassDialogWindowEffect(radius: Dp = 20.dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { it.blurBehindRadius = radiusPx }
    }
}

/**
 * AlertDialog with the shared glass treatment: translucent container,
 * hairline border and blur-behind on supported devices. All dialogs in the
 * app route through this wrapper so the style stays consistent.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.extraLarge
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            GlassDialogWindowEffect()
            confirmButton()
        },
        modifier = modifier.border(1.dp, glassBorderBrush(), shape),
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = shape,
        containerColor = glassDialogColor(),
    )
}
