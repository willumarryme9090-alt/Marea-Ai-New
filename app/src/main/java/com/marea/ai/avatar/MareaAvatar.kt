package com.marea.ai.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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

/** Position of a facial feature as FRACTIONS of the artwork size, so any resolution works. */
data class FeatureRig(val cx: Float, val cy: Float, val w: Float, val h: Float, val tiltDeg: Float = 0f)

/**
 * Describes where the face features are on the artwork. To use different artwork, replace
 * res/drawable-nodpi/marea_character.webp and adjust these numbers - the animation
 * engine itself does not change.
 */
data class AvatarRig(
    val artRes: Int = R.drawable.marea_character,
    val aspect: Float = 736f / 1308f,
    val leftEye: FeatureRig = FeatureRig(0.444f, 0.301f, 0.062f, 0.017f, 12f),
    val rightEye: FeatureRig = FeatureRig(0.573f, 0.288f, 0.060f, 0.017f, -12f),
    val mouth: FeatureRig = FeatureRig(0.523f, 0.344f, 0.040f, 0.011f),
    val lidColor: Color = Color(0xFFF3CDC5),
    val mouthColor: Color = Color(0xFF6B2230)
)

/**
 * Layered 2D avatar: static artwork + animated overlays (eyelids, mouth).
 * This is NOT a rigged character; it is an honest, lightweight approximation.
 * Mouth movement is generic open/close while TTS is actually playing, not phoneme lip-sync.
 *
 * @param animationsActive set false when the app is not visible to stop all animation work.
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
    // Base eyelid level per state (0 = fully open, 1 = closed)
    val baseLid by animateFloatAsState(
        targetValue = when (state) {
            AvatarState.THINKING -> 0.30f
            AvatarState.ERROR -> 0.45f
            else -> 0f
        },
        animationSpec = tween(300), label = "baseLid"
    )

    var blink by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animationsActive, blinkEnabled) {
        blink = 0f
        if (!animationsActive || !blinkEnabled) return@LaunchedEffect
        val a = Animatable(0f)
        while (true) {
            delay(Random.nextLong(2500, 5500))
            a.animateTo(1f, tween(70, easing = LinearEasing)) { blink = value }
            a.animateTo(0f, tween(110, easing = LinearEasing)) { blink = value }
        }
    }

    val speaking = state == AvatarState.SPEAKING && mouthEnabled && animationsActive
    val mouthOpen = if (speaking) {
        val t = rememberInfiniteTransition(label = "mouth")
        val v by t.animateFloat(
            initialValue = 0.1f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(150, easing = LinearEasing), RepeatMode.Reverse),
            label = "mouthOpen"
        )
        v
    } else 0f

    val glow by animateFloatAsState(
        targetValue = if (state == AvatarState.IDLE) 0f else 1f,
        animationSpec = tween(250), label = "glow"
    )

    Box(modifier = modifier) {
        Image(
            painter = painterResource(rig.artRes),
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
                    topLeft = Offset(size.width * m.cx - w / 2, size.height * m.cy - h / 2),
                    size = Size(w, h)
                )
            }
            if (glow > 0f) {
                drawRect(
                    color = accent.copy(alpha = 0.55f * glow),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
            }
        }
    }
}

private fun DrawScope.drawEye(f: FeatureRig, lid: Float, color: Color) {
    val w = size.width * f.w
    val fullH = size.height * f.h
    val h = fullH * lid
    val cx = size.width * f.cx
    val top = size.height * f.cy - fullH / 2
    rotate(degrees = f.tiltDeg, pivot = Offset(cx, size.height * f.cy)) {
        drawOval(color = color, topLeft = Offset(cx - w / 2, top), size = Size(w, h))
    }
}
