package com.boss.cameraguard.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CameraGuard's neumorphic soft-UI system.
 *
 * Real neumorphism reads a surface's depth from a MATCHED PAIR of soft, blurred shadows - a dark
 * shadow cast away from an implied light source (top-left), and a light "highlight" shadow cast
 * toward it - with no hard border/outline doing the work instead. Compose's built-in
 * Modifier.shadow only produces a single shadow from directly overhead, so both shadows here are
 * drawn by hand with a blurred native Paint and composited behind (raised) or clipped inside
 * (inset/pressed) the surface.
 *
 * Colours are derived from CameraGuardPalette, so the locked signature palette (black/graphite
 * background and surfaces / warm golden-yellow accent) is unchanged - only the shadow and shape
 * language changes to match a soft-UI reference. Safety-critical warning colours are untouched;
 * callers may still borrow this shape/depth treatment for them.
 */

private fun DrawScope.drawSoftShadow(shape: Shape, color: Color, blur: Dp, dx: Dp, dy: Dp) {
    if (size.width <= 0f || size.height <= 0f) return
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }.asAndroidPath()
    val blurPx = blur.toPx().coerceAtLeast(0.01f)
    drawContext.canvas.nativeCanvas.let { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = color.toArgb()
        paint.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        canvas.save()
        canvas.translate(dx.toPx(), dy.toPx())
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

/** A surface that appears to float above the background, lit from the top-left. */
@Composable
fun Modifier.neumorphicRaised(
    shape: Shape = RoundedCornerShape(22.dp),
    background: Color = CameraGuardPalette.Surface,
    elevation: Dp = 10.dp,
    borderAlpha: Float = 0.22f
): Modifier {
    val dark = CameraGuardPalette.isDark
    val darkShadow = if (dark) Color.Black.copy(alpha = 0.50f) else Color(0xFF8A8272).copy(alpha = 0.34f)
    val lightShadow = if (dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.95f)
    val blur = elevation * 1.3f
    val travel = elevation * 0.55f
    return this
        .drawBehind {
            drawSoftShadow(shape, darkShadow, blur, travel, travel)
            drawSoftShadow(shape, lightShadow, blur, -travel, -travel)
        }
        .background(background, shape)
}

/** A surface that appears carved/pressed into the background - used for inputs and active tabs. */
@Composable
fun Modifier.neumorphicInset(
    shape: Shape = RoundedCornerShape(18.dp),
    background: Color = CameraGuardPalette.Raised,
    borderAlpha: Float = 0.26f
): Modifier {
    val dark = CameraGuardPalette.isDark
    val darkShadow = if (dark) Color.Black.copy(alpha = 0.46f) else Color(0xFF8A8272).copy(alpha = 0.30f)
    val lightShadow = if (dark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.85f)
    val blur = 7.dp
    val travel = 4.dp
    return this
        .background(background, shape)
        .drawWithContent {
            drawContent()
            val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
            clipPath(clip) {
                // Reversed from the raised treatment: the dark "wall" shadow sits on the near
                // (top-left) edge and the light catch-shadow sits on the far edge, so the surface
                // reads as pushed in rather than floating.
                drawSoftShadow(shape, darkShadow, blur, -travel, -travel)
                drawSoftShadow(shape, lightShadow, blur, travel, travel)
            }
        }
}
