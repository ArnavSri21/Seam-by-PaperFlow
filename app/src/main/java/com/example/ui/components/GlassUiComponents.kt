package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamWarmIvory

/**
 * Shared Liquid Smoked-Glass Backdrop across the entire Seam by PaperFlow body.
 * Deep obsidian canvas layered with soft translucent lavender-silver silk waves.
 */
@Composable
fun SeamGlassBackdrop(
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier
) {
    val effectiveReduced = rememberEffectiveReducedMotion(reducedMotion)
    val transition = rememberInfiniteTransition(label = "whole_body_glass_backdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glass_wave_phase"
    )
    val drift = if (effectiveReduced) 0.4f else phase

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF09090F),
                    Color(0xFF07070B),
                    Color(0xFF0C0B14)
                )
            )
        )

        val w = size.width
        val h = size.height

        // Ambient upper-left lavender luminescence
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x265F5288),
                    Color(0x0B2E2744),
                    Color.Transparent
                ),
                center = Offset(w * (0.15f + 0.05f * drift), h * 0.14f),
                radius = w * 0.72f
            ),
            radius = w * 0.72f,
            center = Offset(w * (0.15f + 0.05f * drift), h * 0.14f)
        )

        // Upper-right silk glass fold
        val upperRightWave = Path().apply {
            moveTo(w * 0.56f, 0f)
            cubicTo(
                w * (0.64f + 0.04f * drift), h * 0.16f,
                w * 0.92f, h * 0.12f,
                w, h * 0.27f
            )
            lineTo(w, 0f)
            close()
        }
        drawPath(
            path = upperRightWave,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x1C8F82B8),
                    Color(0x08252136),
                    Color(0x22A899D6)
                ),
                start = Offset(w * 0.55f, 0f),
                end = Offset(w, h * 0.28f)
            )
        )
        drawPath(
            path = Path().apply {
                moveTo(w * 0.56f, 0f)
                cubicTo(
                    w * (0.64f + 0.04f * drift), h * 0.16f,
                    w * 0.92f, h * 0.12f,
                    w, h * 0.27f
                )
            },
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x3CD4C8F7),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Lower-right ambient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x2C8E7EC7),
                    Color(0x14413763),
                    Color.Transparent
                ),
                center = Offset(w * (0.86f - 0.04f * drift), h * 0.88f),
                radius = w * 0.68f
            ),
            radius = w * 0.68f,
            center = Offset(w * (0.86f - 0.04f * drift), h * 0.88f)
        )
    }
}

/**
 * Smoked frosted-glass container modifier with translucent obsidian-plum depth
 * and luminous specular rim border.
 */
fun Modifier.seamGlassSurface(
    shape: Shape = RoundedCornerShape(22.dp),
    elevated: Boolean = false,
    accentBorder: Color? = null
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            colors = if (elevated) {
                listOf(
                    Color(0xD4232231),
                    Color(0xC2171722),
                    Color(0xD621202F)
                )
            } else {
                listOf(
                    Color(0xC4191924),
                    Color(0xB3101118),
                    Color(0xC91A1927)
                )
            }
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                accentBorder?.copy(alpha = 0.48f) ?: Color.White.copy(alpha = 0.30f),
                SeamLavenderAccent.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.09f),
                accentBorder?.copy(alpha = 0.28f) ?: Color.White.copy(alpha = 0.22f)
            )
        ),
        shape = shape
    )

/**
 * Pressable frosted glass card modifier with subtle 0.985 press scale and luminous highlight rim.
 */
fun Modifier.seamPressableGlassCard(
    reducedMotion: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    elevated: Boolean = false,
    accentBorder: Color? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.985f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "glass_card_press_scale"
    )
    val rimColor = if (isPressed) {
        Color.White.copy(alpha = 0.48f)
    } else {
        accentBorder?.copy(alpha = 0.45f) ?: Color.White.copy(alpha = 0.28f)
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = if (elevated || isPressed) {
                    listOf(
                        Color(0xD8242333),
                        Color(0xC6181824),
                        Color(0xDA222130)
                    )
                } else {
                    listOf(
                        Color(0xC4191924),
                        Color(0xB3101118),
                        Color(0xC91A1927)
                    )
                }
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    rimColor,
                    SeamLavenderAccent.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.09f),
                    Color.White.copy(alpha = 0.22f)
                )
            ),
            shape = shape
        )
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(color = SeamWarmIvory),
            role = Role.Button,
            onClick = onClick
        )
}

/**
 * Unified frosted pill search/filter bar matching the obsidian glass aesthetic.
 */
@Composable
fun SeamGlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingContent: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val pillShape = RoundedCornerShape(22.dp)

    Row(
        modifier = modifier
            .height(54.dp)
            .clip(pillShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xBA1A1A25),
                        Color(0xAA12131B),
                        Color(0xBA1A1926)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isFocused) {
                        listOf(
                            SeamLavenderAccent.copy(alpha = 0.72f),
                            Color.White.copy(alpha = 0.35f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    }
                ),
                shape = pillShape
            )
            .padding(start = 16.dp, end = if (trailingContent != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = placeholder,
            tint = if (isFocused) SeamLavenderAccent else SeamMutedGrey,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                    color = SeamMutedGrey
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = SeamWarmIvory,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(SeamLavenderAccent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
            )
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

/**
 * Metallic frosted-glass CTA button used for primary Vault & Reveal actions.
 */
@Composable
fun SeamGlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    reducedMotion: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.985f else 1f,
        animationSpec = tween(durationMillis = 130),
        label = "glass_pill_scale"
    )
    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .height(54.dp)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(pillShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF3A3949),
                        Color(0xFF7D7B91),
                        Color(0xFF424150)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.52f),
                        Color.White.copy(alpha = 0.14f),
                        SeamLavenderAccent.copy(alpha = 0.30f)
                    )
                ),
                shape = pillShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = Color.White),
                enabled = enabled && !isLoading,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Compact frosted glass badge for cipher tags, masked placeholders, and strength labels.
 */
@Composable
fun SeamGlassBadge(
    text: String,
    tint: Color = SeamSilver,
    borderTint: Color = Color.White.copy(alpha = 0.18f),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x991C1D28))
            .border(1.dp, borderTint, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = tint
        )
    }
}
