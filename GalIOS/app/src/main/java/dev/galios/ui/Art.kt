package dev.galios.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer

/**
 * An original drawing of a slab phone seen from the back, in the spirit of a titanium Galaxy Ultra: flat sides, small
 * corners, a column of three large camera rings, two small lenses and a flash beside them, and a stylus leaning on its
 * left. Drawn in code (no photo, no logo). It floats up and settles into a slight tilt when it first appears.
 */
@Composable
fun PhoneArt(modifier: Modifier = Modifier) {
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 140f)) }
    Canvas(
        modifier.graphicsLayer {
            translationY = rise.value * 28f * density
            rotationZ = -7f + rise.value * 5f
            alpha = 1f - rise.value * 0.6f
        },
    ) {
        val w = size.width
        val h = size.height
        // The phone takes most of the box; the stylus sits to its left.
        val body = Size(w * 0.70f, h * 0.94f)
        val origin = Offset(w * 0.27f, h * 0.02f)
        shadow(origin, body)
        stylus(origin, body)
        phone(origin, body)
    }
}

private fun DrawScope.shadow(origin: Offset, body: Size) {
    val c = Offset(origin.x + body.width * 0.52f, origin.y + body.height * 1.0f)
    drawOval(
        Brush.radialGradient(listOf(Color(0x40000000), Color(0x00000000)), center = c, radius = body.width * 0.62f),
        topLeft = Offset(c.x - body.width * 0.62f, c.y - body.height * 0.05f),
        size = Size(body.width * 1.24f, body.height * 0.10f),
    )
}

private fun DrawScope.stylus(origin: Offset, body: Size) {
    val len = body.height * 0.92f
    val thick = body.width * 0.075f
    val at = Offset(origin.x - thick * 1.6f, origin.y + body.height * 0.07f)
    rotate(degrees = 6f, pivot = Offset(at.x, at.y + len)) {
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFFB9B7B2), Color(0xFFEDECE9), Color(0xFFC3C1BC)), startX = at.x, endX = at.x + thick),
            topLeft = at, size = Size(thick, len), cornerRadius = CornerRadius(thick / 2, thick / 2),
        )
        // the tip and the button
        drawRoundRect(Color(0xFF8C8A86), topLeft = Offset(at.x + thick * 0.2f, at.y + len - thick * 0.9f), size = Size(thick * 0.6f, thick * 0.9f), cornerRadius = CornerRadius(thick, thick))
        drawRoundRect(Color(0xFFA19F9A), topLeft = Offset(at.x + thick * 0.3f, at.y + len * 0.78f), size = Size(thick * 0.4f, len * 0.05f), cornerRadius = CornerRadius(thick, thick))
    }
}

private fun DrawScope.phone(origin: Offset, body: Size) {
    val r = body.width * 0.09f
    // The frame, then the back glass a hair inside it: a cool titanium silver with a soft vertical sheen.
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFF9E9C98), Color(0xFFDAD8D4), Color(0xFFA5A39F)), startX = origin.x, endX = origin.x + body.width),
        topLeft = origin, size = body, cornerRadius = CornerRadius(r, r),
    )
    val inset = body.width * 0.025f
    val glass = Offset(origin.x + inset, origin.y + inset)
    val glassSize = Size(body.width - inset * 2, body.height - inset * 2)
    drawRoundRect(
        Brush.linearGradient(
            listOf(Color(0xFFF1F0EE), Color(0xFFDCDBD8), Color(0xFFE9E8E5), Color(0xFFD3D2CF)),
            start = glass, end = Offset(glass.x + glassSize.width, glass.y + glassSize.height),
        ),
        topLeft = glass, size = glassSize, cornerRadius = CornerRadius(r * 0.8f, r * 0.8f),
    )
    // A highlight along the left edge.
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0x66FFFFFF), Color(0x00FFFFFF)), startX = glass.x, endX = glass.x + glassSize.width * 0.25f),
        topLeft = glass, size = Size(glassSize.width * 0.25f, glassSize.height), cornerRadius = CornerRadius(r * 0.8f, r * 0.8f),
    )
    // Side keys on the right edge.
    drawRoundRect(Color(0xFF8E8C88), topLeft = Offset(origin.x + body.width - inset * 0.4f, origin.y + body.height * 0.22f), size = Size(inset * 0.8f, body.height * 0.12f), cornerRadius = CornerRadius(inset, inset))
    drawRoundRect(Color(0xFF8E8C88), topLeft = Offset(origin.x + body.width - inset * 0.4f, origin.y + body.height * 0.37f), size = Size(inset * 0.8f, body.height * 0.06f), cornerRadius = CornerRadius(inset, inset))

    // The cameras: a column of three large rings on the left, two small lenses and a flash beside them.
    val big = body.width * 0.135f
    val colX = glass.x + glassSize.width * 0.27f
    val top = glass.y + glassSize.height * 0.09f
    for (k in 0 until 3) lens(Offset(colX, top + k * big * 2.45f), big)
    val small = big * 0.62f
    val sideX = colX + big * 2.35f
    lens(Offset(sideX, top + big * 0.15f), small)
    lens(Offset(sideX, top + big * 2.6f), small)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF6D8), Color(0xFFD8CFA8)), center = Offset(sideX, top + big * 1.4f), radius = small * 0.38f),
        radius = small * 0.38f, center = Offset(sideX, top + big * 1.4f))
}

/** One camera: a silver ring, a black bezel, dark glass with a blue cast, and a small reflection. */
private fun DrawScope.lens(c: Offset, radius: Float) {
    drawCircle(Brush.linearGradient(listOf(Color(0xFFEDECE9), Color(0xFF8F8D89)), start = Offset(c.x - radius, c.y - radius), end = Offset(c.x + radius, c.y + radius)), radius * 1.18f, c)
    drawCircle(Color(0xFF111111), radius * 1.02f, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF2A3446), Color(0xFF0B0E14)), center = c, radius = radius * 0.8f), radius * 0.78f, c)
    drawCircle(Color(0xFF3B4558), radius * 0.36f, c, style = Stroke(radius * 0.06f))
    translate(-radius * 0.25f, -radius * 0.28f) { drawCircle(Color(0x66FFFFFF), radius * 0.13f, c) }
}
