package com.example.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.annotation.RawRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Plays one of the user's attached MP4 animations from res/raw seamlessly in a loop,
 * respecting the Reduced Motion setting (when `reducedMotion = true`, renders a calm static
 * concentric artwork without video motion or continuous rotation).
 */
@Composable
fun RawVideoAnimationBanner(
    @RawRes rawResId: Int,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    overlayScrimAlpha: Float = 0.28f,
    fallbackContent: @Composable () -> Unit = {
        MindfulRingsCanvas(
            modifier = Modifier.fillMaxSize(),
            reducedMotion = reducedMotion
        )
    }
) {
    val context = LocalContext.current
    Box(modifier = modifier.clip(RoundedCornerShape(22.dp))) {
        fallbackContent()

        if (!reducedMotion) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            start()
                        }
                        setOnErrorListener { _, _, _ ->
                            true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = overlayScrimAlpha * 0.6f),
                            Color.Black.copy(alpha = overlayScrimAlpha * 1.4f)
                        )
                    )
                )
        )
    }
}

/**
 * 3D Concentric Gyroscopic Rings inspired by the attached animtemp4_c127c792.mp4 video.
 * Freeze rotation when `reducedMotion = true`.
 */
@Composable
fun MindfulRingsCanvas(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    ringColor: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "mindful_rings")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val animatedBreathe by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val phase = if (reducedMotion) 42f else animatedPhase
    val breathe = if (reducedMotion) 0.96f else animatedBreathe

    Canvas(modifier = modifier.background(Color(0xFF06080D))) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = (size.minDimension / 2.2f) * breathe

        for (i in 1..6) {
            val fraction = i / 6f
            val r = maxRadius * fraction
            val angleOffset = phase + (i * 28f)
            val tilt = cos(Math.toRadians(angleOffset.toDouble())).toFloat().coerceIn(-0.92f, 0.92f)
            val ovalWidth = r * 2f * kotlin.math.abs(tilt).coerceAtLeast(0.22f)
            val ovalHeight = r * 2f

            rotate(degrees = i * 15f + phase * 0.25f, pivot = center) {
                drawOval(
                    color = ringColor.copy(alpha = (0.22f + (1f - fraction) * 0.55f).coerceIn(0.1f, 0.85f)),
                    topLeft = Offset(center.x - ovalWidth / 2f, center.y - ovalHeight / 2f),
                    size = Size(ovalWidth, ovalHeight),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

/**
 * Fine luminous upload/sync progress indicator inspired by animtemp2_93d65259.mp4 ("Uploading" pill).
 */
@Composable
fun GlowingSyncPill(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "sync_pill")
    val animatedSweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )
    val sweepAngle = if (reducedMotion) 45f else animatedSweep

    val glowColor = if (isActive) Color(0xFFB8B5FF) else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF0E131F))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val rad = Math.toRadians(sweepAngle.toDouble())
            val cx = w / 2f + (w / 2f) * cos(rad).toFloat()
            val cy = h / 2f + (h / 2f) * sin(rad).toFloat()

            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor,
                        glowColor.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = w * 0.65f
                ),
                cornerRadius = CornerRadius(h / 2f, h / 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(glowColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFF4F1EA),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Refined SEAM wordmark with "by PaperFlow" paired with the preserved PaperFlow emblem.
 */
@Composable
fun SeamBrandWordmark(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 46.dp,
    lightTextOnDark: Boolean = true
) {
    val primaryTextColor = if (lightTextOnDark) Color(0xFFF4F1EA) else MaterialTheme.colorScheme.onBackground
    val subTextColor = if (lightTextOnDark) Color(0xFFB8B5FF) else MaterialTheme.colorScheme.secondary

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PaperFlowLogoBadge(size = emblemSize)
        Column {
            Text(
                text = "SEAM",
                style = MaterialTheme.typography.headlineMedium.copy(
                    letterSpacing = 3.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = primaryTextColor
            )
            Text(
                text = "by PaperFlow",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp
                ),
                color = subTextColor
            )
        }
    }
}

/**
 * Preserves the exact user-supplied Paper Flow circular emblem without redrawing.
 */
@Composable
fun PaperFlowLogoBadge(
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF000000))
            .border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.paper_flow_emblem),
            contentDescription = "Paper Flow Emblem",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(CircleShape)
        )
    }
}
