package dev.galios.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Liquid glass with Kyant's Backdrop (github.com/Kyant0/AndroidLiquidGlass, Apache 2.0): the element really refracts
 * what is behind it (a lens at its rounded edges, chromatic aberration on the lenses that move), blurs and saturates it,
 * and catches light on its rim. Refraction is a RuntimeShader (Android 13+, the S25 Ultra has 15).
 *
 * The JVM screenshot tests (Robolectric) cannot compile Backdrop's AGSL shaders: there the glass draws without the lens
 * and the rim highlight.
 */
val glassShaders: Boolean = Build.FINGERPRINT != "robolectric" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * A touch on liquid glass: it swells on a spring while held and a soft light follows the finger, as on iOS. [progress]
 * goes 0 to 1 on press and back on release; [touch] is where the finger is.
 */
class GlassPress(private val scope: CoroutineScope) {
    val anim = Animatable(0f)
    var touch by mutableStateOf(Offset.Unspecified)
    val progress get() = anim.value

    fun press(at: Offset) { touch = at; scope.launch { anim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) } }
    fun release() { scope.launch { anim.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 260f)) } }

    /** Tracks the finger without taking the touch from clicks and drags beneath. */
    val gesture: Modifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            press(down.position)
            do {
                val event = awaitPointerEvent()
                event.changes.firstOrNull()?.let { touch = it.position }
            } while (event.changes.any { it.pressed })
            release()
        }
    }

    /** The swell (scale up to [swell]) and the light under the finger. */
    fun effect(swell: Float = 1.06f): Modifier = Modifier
        .graphicsLayer { val s = lerp(1f, swell, progress); scaleX = s; scaleY = s }
        .drawWithContent {
            drawContent()
            val p = progress
            if (p > 0.01f && touch != Offset.Unspecified) {
                val r = size.maxDimension * 0.6f
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f * p), Color.Transparent), center = touch, radius = r),
                    radius = r, center = touch, blendMode = BlendMode.Plus,
                )
            }
        }
}

@Composable
fun rememberGlassPress(): GlassPress {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassPress(scope) }
}

/**
 * A liquid glass surface over [backdrop]: vibrancy, blur, the edge lens, the rim highlight and a soft shadow; [press]
 * deepens the lens, brightens the rim and adds the inner shadow while the glass is held. [tint] is drawn on the glass
 * so text reads on it.
 */
fun Modifier.liquidBackdrop(
    backdrop: LayerBackdrop,
    shape: Shape,
    press: GlassPress? = null,
    blurDp: Float = 8f,
    lensDp: Float = 20f,
    tint: Color = Color(0x73121212),
    aberration: Boolean = false,
): Modifier = this
    .then(press?.effect() ?: Modifier)
    .drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurDp.dp.toPx())
            if (glassShaders) {
                val p = press?.progress ?: 0f
                lens((lensDp + 8f * p).dp.toPx(), (lensDp * 1.4f + 10f * p).dp.toPx(), chromaticAberration = aberration)
            }
        },
        highlight = if (glassShaders) ({ Highlight.Default.copy(alpha = (0.75f + 0.25f * (press?.progress ?: 0f)).coerceAtMost(1f)) }) else null,
        shadow = { Shadow(alpha = 0.6f) },
        innerShadow = { press?.progress?.takeIf { it > 0.01f }?.let { InnerShadow(radius = 8.dp * it, alpha = it) } },
        onDrawSurface = { drawRect(tint) },
    )
