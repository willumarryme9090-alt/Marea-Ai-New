
package com.marea.ai.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.marea.ai.R
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Facial feature coordinates as fractions of the artwork size. */
data class FeatureRig(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val tiltDeg: Float = 0f
)

/** Configuration for the 2D avatar artwork and facial overlays. */
data class AvatarRig(
    val artRes: Int = R.drawable.marea_character,
    val aspect: Float = 736f / 1308f,
    val leftEye: FeatureRig =
        FeatureRig(0.444f, 0.301f, 0.062f, 0.017f, 12f),
    val rightEye: FeatureRig =
        FeatureRig(0.573f, 0.288f, 0.060f, 0.017f, -12f),
    val mouth: FeatureRig =
        FeatureRig(0.523f, 0.344f, 0.040f, 0.011f),
    val lidColor: Color = Color(0xFFF3CDC5),
    val mouthColor: Color = Color(0xFF6B2230)
)

/**
 * Lightweight 2D avatar using static artwork and animated overlays.
 * Mouth movement is generic open/close animation while speaking;
 * it is not phoneme-based lip sync.
 */
@Composable
fun MareaAvatar(
    state: AvatarState,
    animationsActive: Boolean,
    blinkEnabled: Boolean,
    mouthEnabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    rig: AvatarRig = AvatarRig()
) {
    val baseLid by animateFloatAsState(
        targetValue = when (state) {
            AvatarState.THINKING -> 0.30f
            AvatarState.ERROR -> 0.45f
            else -> 0f
        },
        animationSpec = tween(300),
        label = "baseLid"
    )

    var blink by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(animationsActive, blinkEnabled) {
        blink = 0f

        if (!animationsActive || !blinkEnabled) {
            return@LaunchedEffect
        }

        val blinkAnimation = Animatable(0f)

        while (true) {
            delay(Random.nextLong(2500, 5500))

            blinkAnimation.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 70,
                    easing = LinearEasing
                )
            ) {
                blink = value
            }

            blinkAnimation.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 110,
                    easing = LinearEasing
                )
            ) {
                blink = value
            }
        }
    }

    val speaking =
        state == AvatarState.SPEAKING &&
        mouthEnabled &&
        animationsActive

    val mouthAnimation = remember { Animatable(0.1f) }

    LaunchedEffect(speaking) {
        if (speaking) {
            mouthAnimation.snapTo(0.1f)

            while (true) {
                mouthAnimation.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 150,
                        easing = LinearEasing
                    )
                )

                mouthAnimation.animateTo(
                    targetValue = 0.1f,
                    animationSpec = tween(
                        durationMillis = 150,
                        easing = LinearEasing
                    )
                )
            }
        } else {
            mouthAnimation.snapTo(0f)
        }
    }

    val mouthOpen = if (speaking) mouthAnimation.value else 0f

    val glow by animateFloatAsState(
        targetValue = if (state == AvatarState.IDLE) 0f else 1f,
        animationSpec = tween(250),
        label = "glow"
    )

    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = rig.artRes),
            contentDescription = "Marea avatar, ${state.name.lowercase()}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val lid = maxOf(baseLid, blink)

            if (lid > 0.01f) {
                drawEye(rig.leftEye, lid, rig.lidColor)
                drawEye(rig.rightEye, lid, rig.lidColor)
            }

            if (mouthOpen > 0.01f) {
                val m = rig.mouth
                val w = size.width * m.w
                val h = size.height * m.h * mouthOpen

                drawOval(
                    color = rig.mouthColor,
                    topLeft = Offset(
                        x = size.width * m.cx - w / 2f,
                        y = size.height * m.cy - h / 2f
                    ),
                    size = Size(w, h)
                )
            }

            if (glow > 0f) {
                drawRect(
                    color = accent.copy(alpha = 0.55f * glow),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4.dp.toPx()
                    )
                )
            }
        }
    }
}

private fun DrawScope.drawEye(
    feature: FeatureRig,
    lid: Float,
    color: Color
) {
    val w = size.width * feature.w
    val fullHeight = size.height * feature.h
    val h = fullHeight * lid
    val cx = size.width * feature.cx
    val top = size.height * feature.cy - fullHeight / 2f

    rotate(
        degrees = feature.tiltDeg,
        pivot = Offset(cx, size.height * feature.cy)
    ) {
        drawOval(
            color = color,
            topLeft = Offset(cx - w / 2f, top),
            size = Size(w, h)
        )
    }
}
