package com.boss.cameraguard.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * CameraGuard's restrained neumorphic treatment.
 * It keeps the locked signature palette while giving cards/controls a soft raised depth.
 * Safety-critical UI (SOS/camera warning red) should keep its semantic colour and may only
 * borrow the shadow/shape treatment.
 */
@Composable
fun Modifier.neumorphicRaised(
    shape: Shape = RoundedCornerShape(22.dp),
    background: Color = CameraGuardPalette.Surface,
    elevation: Dp = 10.dp,
    borderAlpha: Float = 0.22f
): Modifier {
    val shadow = if (CameraGuardPalette.isDark) Color.Black.copy(alpha = 0.58f)
    else Color(0xFF8FA6BC).copy(alpha = 0.30f)
    val highlight = if (CameraGuardPalette.isDark) Color.White.copy(alpha = borderAlpha * 0.46f)
    else Color.White.copy(alpha = 0.92f)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = shadow,
            spotColor = shadow
        )
        .background(background, shape)
        .border(1.dp, highlight, shape)
}

@Composable
fun Modifier.neumorphicInset(
    shape: Shape = RoundedCornerShape(18.dp),
    background: Color = CameraGuardPalette.Raised,
    borderAlpha: Float = 0.26f
): Modifier {
    val edge = if (CameraGuardPalette.isDark) CameraGuardPalette.Border.copy(alpha = borderAlpha)
    else Color(0xFFCBD9E8).copy(alpha = 0.78f)
    return this
        .background(background, shape)
        .border(1.dp, edge, shape)
}
