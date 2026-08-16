package app.morphe.manager.ui.theme

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt

/** Smallest display scale, below which touch targets stop meeting the minimum size. */
const val UI_SCALE_MIN = 0.75f

/** Largest display scale, above which dense screens start clipping their content. */
const val UI_SCALE_MAX = 1.25f

const val UI_SCALE_DEFAULT = 1f

/** Distance between selectable scales, matching the granularity of the system screen zoom. */
const val UI_SCALE_STEP = 0.05f

/** Snaps onto the nearest supported stop, so a stored value stays valid when the range changes. */
fun Float.coerceToUiScale(): Float {
    val clamped = coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)
    return UI_SCALE_MIN + ((clamped - UI_SCALE_MIN) / UI_SCALE_STEP).roundToInt() * UI_SCALE_STEP
}

fun Float.toUiScalePercent(): Int = (this * 100).roundToInt()

/**
 * Scales everything below by [scale] the way the system screen zoom does, by resolving every dp
 * and sp against a denser or sparser [Density].
 *
 * The configuration is scaled with it so window size classes keep describing the room the content
 * actually has: the same screen holds fewer dp once the scale grows.
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun UiScaleProvider(scale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val effectiveScale = scale.coerceToUiScale()

    val scaledDensity = remember(density, effectiveScale) {
        ScaledDensity(density, effectiveScale)
    }

    val scaledConfiguration = remember(configuration, effectiveScale) {
        Configuration(configuration).apply {
            densityDpi = (configuration.densityDpi * effectiveScale).roundToInt()
            screenWidthDp = (configuration.screenWidthDp / effectiveScale).roundToInt()
            screenHeightDp = (configuration.screenHeightDp / effectiveScale).roundToInt()
            smallestScreenWidthDp = (configuration.smallestScreenWidthDp / effectiveScale).roundToInt()
        }
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalConfiguration provides scaledConfiguration,
        content = content
    )
}

/**
 * Scales this density by [scale], for drawing a sample of the interface at a scale the app has
 * not been switched to yet.
 */
fun Density.scaledBy(scale: Float): Density = ScaledDensity(this, scale)

/**
 * A [Density] scaled by [scale], leaving the system font scale to apply on top of it.
 *
 * Text is converted through [base] rather than by the plain `sp * fontScale` formula, so the
 * non-linear font scaling the platform applies from Android 14 on survives the scaling.
 */
private class ScaledDensity(private val base: Density, scale: Float) : Density {
    override val density = base.density * scale
    override val fontScale = base.fontScale
    override fun TextUnit.toDp(): Dp = with(base) { this@toDp.toDp() }
}
