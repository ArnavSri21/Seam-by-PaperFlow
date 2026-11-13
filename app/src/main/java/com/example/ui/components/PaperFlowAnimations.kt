package com.example.ui.components

import android.net.Uri
import android.provider.Settings
import android.widget.VideoView
import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.SeamBorderSubtle
import com.example.ui.theme.SeamHighlightBorder
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamSuccessSage
import com.example.ui.theme.SeamWarmIvory
import kotlin.math.cos
import kotlin.math.sin

/**
 * Section 09 · Motion specification:
 * Checks both user's in-app Reduced Motion preference AND Android system ANIMATOR_DURATION_SCALE.
 */
@Composable
fun rememberEffectiveReducedMotion(userReducedMotionPref: Boolean): Boolean {
    val context = LocalContext.current
    val systemScaleZero = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    return userReducedMotionPref || systemScaleZero
}

/**
 * Section 09 · Card press:
 * Trigger: Tap / touch down | Timing: 100–180 ms | Behaviour: Scale to 0.985 and subtle highlight; no bounce.
 */
fun Modifier.seamPressableCard(
    reducedMotion: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.985f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "seam_card_press_scale"
    )
    val borderColor = if (isPressed) SeamHighlightBorder else SeamBorderSubtle

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .border(1.dp, borderColor, shape)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(),
            role = Role.Button,
            onClick = onClick
        )
}

/**
 * Section 09 · Ambient light:
 * Trigger: Background idle | Timing: 12–20 s loop | Behaviour: Very subtle drift; low contrast; no flashing.
 */
@Composable
fun AmbientLightBackdrop(
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val effectiveReduced = rememberEffectiveReducedMotion(reducedMotion)
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_light_drift")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    val phase = if (effectiveReduced) 0.35f else drift
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width * (0.25f + 0.45f * phase)
        val centerY = size.height * (0.12f + 0.14f * (1f - phase))
        val radius = size.minDimension * 0.85f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SeamLavenderAccent.copy(alpha = 0.055f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = radius
            ),
            radius = radius,
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * Section 02 & 09 · Memory constellation (Home hero enters):
 * Timing: 700–1200 ms | Behaviour: Lines draw in, memory points appear in sequence, then settle.
 * Never autoplays a blocking video on Home or blocks scrolling.
 */
@Composable
fun MemoryConstellationHeroCanvas(
    memoryCount: Int,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val effectiveReduced = rememberEffectiveReducedMotion(reducedMotion)
    var entered by remember { mutableStateOf(effectiveReduced) }
    LaunchedEffect(effectiveReduced) {
        entered = true
    }

    // 950 ms entrance draw-in (within the 700–1200 ms specification), then settles at 1f
    val drawProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (effectiveReduced) 0 else 950,
            easing = FastOutSlowInEasing
        ),
        label = "constellation_draw_in"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.52f, size.height * 0.48f)
        val maxRadiusX = size.width * 0.42f
        val maxRadiusY = size.height * 0.38f

        // 4 concentric elliptical orbits that draw in smoothly and settle
        val ringScales = listOf(1.0f, 0.82f, 0.64f, 0.46f)
        ringScales.forEachIndexed { index, scale ->
            val itemStart = index * 0.12f
            val ringProgress = ((drawProgress - itemStart) / (1f - itemStart)).coerceIn(0f, 1f)
            if (ringProgress > 0f) {
                val rx = maxRadiusX * scale
                val ry = maxRadiusY * scale
                drawArc(
                    color = SeamSilver.copy(alpha = (0.14f + index * 0.05f) * ringProgress),
                    startAngle = -140f,
                    sweepAngle = 360f * ringProgress,
                    useCenter = false,
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Memory points appear in sequence along the constellation orbits, then settle
        val pointAngles = listOf(22f, 138f, 215f, 305f, 75f, 178f)
        val activePoints = pointAngles.take(memoryCount.coerceIn(3, 6))
        activePoints.forEachIndexed { idx, deg ->
            val threshold = 0.35f + idx * 0.1f
            val pointAlpha = ((drawProgress - threshold) / 0.25f).coerceIn(0f, 1f)
            if (pointAlpha > 0f) {
                val ringScale = ringScales[idx % ringScales.size]
                val rad = Math.toRadians(deg.toDouble())
                val px = center.x + (maxRadiusX * ringScale * cos(rad)).toFloat()
                val py = center.y + (maxRadiusY * ringScale * sin(rad)).toFloat()
                drawCircle(
                    color = SeamLavenderAccent.copy(alpha = 0.28f * pointAlpha),
                    radius = 6.dp.toPx() * pointAlpha,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = SeamWarmIvory.copy(alpha = 0.9f * pointAlpha),
                    radius = 2.6.dp.toPx() * pointAlpha,
                    center = Offset(px, py)
                )
            }
        }
    }
}

/**
 * Top Brand Header matching Pages 3, 5, 7, 9, 11, 13:
 * Shows SEAM / by PaperFlow lockup on the left, optional Back button,
 * honest system status pill, and Settings shortcut icon on the right.
 */
@Composable
fun SeamTopHeader(
    statusLabel: String,
    isStatusActive: Boolean = false,
    reducedMotion: Boolean = false,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    rightActionContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showBackButton && onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SeamWarmIvory
                    )
                }
            }
            SeamBrandWordmark(
                emblemSize = 32.dp,
                lightTextOnDark = true,
                reducedMotion = reducedMotion
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (rightActionContent != null) {
                rightActionContent()
            } else {
                GlowingSyncPill(
                    label = statusLabel,
                    isActive = isStatusActive,
                    reducedMotion = reducedMotion
                )
                if (onSettingsClick != null) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Open Settings",
                            tint = SeamSilver
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section 09 · Success confirmation:
 * Trigger: Save / import done | Timing: 180–260 ms | Behaviour: Small checkmark and status text.
 */
@Composable
fun InlineSaveStatusBadge(
    statusText: String,
    isSaved: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF17191D))
            .border(1.dp, SeamBorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AnimatedVisibility(
            visible = isSaved,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180))
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Saved",
                tint = SeamSuccessSage,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSaved) SeamSilver else SeamMutedGrey
        )
    }
}

/**
 * Plays one of the user's attached MP4 reference animations from res/raw when requested
 * (e.g., on the Auth splash screen, Vault header, or Motion Storyboard in Settings),
 * respecting the Reduced Motion setting.
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
    val effectiveReduced = rememberEffectiveReducedMotion(reducedMotion)
    Box(modifier = modifier.clip(RoundedCornerShape(22.dp))) {
        fallbackContent()

        if (!effectiveReduced) {
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
 * 3D Concentric Gyroscopic Rings inspired by Reference motion 04 (`animtemp4_c127c792.mp4`).
 */
@Composable
fun MindfulRingsCanvas(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    ringColor: Color = SeamSilver
) {
    val effectiveReduced = rememberEffectiveReducedMotion(reducedMotion)
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

    val phase = if (effectiveReduced) 42f else animatedPhase

    Canvas(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFF17191D),
                    Color(0xFF08090B)
                )
            )
        )
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * 0.42f

        for (i in 1..6) {
            val fraction = i / 6f
            val rX = maxRadius * fraction
            val rY = maxRadius * fraction * (0.45f + 0.25f * sin(Math.toRadians((phase + i * 28).toDouble())).toFloat())

            rotate(degrees = phase * (if (i % 2 == 0) 0.35f else -0.25f) + i * 18f, pivot = center) {
                drawOval(
                    color = ringColor.copy(alpha = 0.14f + (1f - fraction) * 0.26f),
                    topLeft = Offset(center.x - rX, center.y - rY),
                    size = Size(rX * 2f, rY * 2f),
                    style = Stroke(width = (1.3f + (1f - fraction) * 1.2f).dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * Honest System Status & Import Progress Pill inspired by Reference motion 02 (`animtemp2_93d65259.mp4`).
 */
@Composable
fun GlowingSyncPill(
    label: String,
    isActive: Boolean,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier
) {
    val effectiveReduced = rememberEffectiveReducedMotion(reducedMotion)
    val transition = rememberInfiniteTransition(label = "sync_pill_glow")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF17191D))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = 1.2.dp.toPx()
            drawRoundRect(
                color = SeamBorderSubtle,
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                style = Stroke(width = strokePx)
            )
            if (isActive && !effectiveReduced) {
                val angle = sweep * 360f
                val rad = Math.toRadians(angle.toDouble())
                val gx = (size.width / 2f) + (size.width / 2f) * cos(rad).toFloat()
                val gy = (size.height / 2f) + (size.height / 2f) * sin(rad).toFloat()
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SeamLavenderAccent,
                            SeamSilver.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        center = Offset(gx, gy),
                        radius = size.width * 0.65f
                    ),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    style = Stroke(width = 1.6.dp.toPx())
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) SeamWarmIvory else SeamMutedGrey
        )
    }
}

/**
 * Brand lockup: SEAM · by PaperFlow paired with the preserved PaperFlow emblem.
 * Section 09 · Logo emblem: 8–16 s slow subtle halo pulse; static fallback when reduced motion is active.
 */
@Composable
fun SeamBrandWordmark(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 36.dp,
    lightTextOnDark: Boolean = true,
    reducedMotion: Boolean = false
) {
    val primaryTextColor = if (lightTextOnDark) SeamWarmIvory else MaterialTheme.colorScheme.onBackground
    val subTextColor = if (lightTextOnDark) SeamMutedGrey else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PaperFlowLogoBadge(size = emblemSize, reducedMotion = reducedMotion)
        Column {
            Text(
                text = "SEAM",
                style = MaterialTheme.typography.titleMedium.copy(
                    letterSpacing = 1.8.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = primaryTextColor
            )
            Text(
                text = "by PaperFlow",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                color = subTextColor
            )
        }
    }
}

/**
 * Preserves the exact user-supplied Paper Flow circular emblem with an 8–16 s subtle border pulse.
 */
@Composable
fun PaperFlowLogoBadge(
    size: Dp = 44.dp,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier
) {
    val effectiveReduced = rememberEffectiveReducedMotion(reducedMotion)
    val infiniteTransition = rememberInfiniteTransition(label = "logo_emblem_loop")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_border_alpha"
    )
    val effectiveAlpha = if (effectiveReduced) 0.45f else borderAlpha

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF000000))
            .border(1.dp, SeamLavenderAccent.copy(alpha = effectiveAlpha), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.paper_flow_emblem),
            contentDescription = "Paper Flow Emblem",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(1.5.dp)
                .clip(CircleShape)
        )
    }
}
