package dev.galios.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * Opening transitions as a container transform (shared bounds): what you tap grows from exactly where it is into what
 * it opens, its bounds and corners animating together on one spring, frame by frame, and back the same way. Used for
 * a version card into its details, and the Setup button into the Setup page.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalShared = compositionLocalOf<SharedTransitionScope?> { null }

/** The tab's own visibility scope (from the tab's AnimatedContent), for elements that open another tab. */
val LocalTabScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The one spring every opening uses: no bounce, fast to start, soft to land. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val openSpring = BoundsTransform { _, _ -> spring(dampingRatio = 0.9f, stiffness = 280f) }

/**
 * Makes this element one end of the container transform [key]; [shape] is its outline while it moves. [inOverlay]
 * false draws the transform in place, inside the page: the liquid glass tab bar then keeps refracting it (drawn in the
 * overlay, above the page, it left the bar with nothing under it for the whole transform).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedContainer(key: String, scope: AnimatedVisibilityScope?, shape: Shape, inOverlay: Boolean = true): Modifier {
    val shared = LocalShared.current ?: return this
    if (scope == null) return this
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
            boundsTransform = openSpring,
            renderInOverlayDuringTransition = inOverlay,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
