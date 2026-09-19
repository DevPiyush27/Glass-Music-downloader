package com.example.audiodownloader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Strictly Neomorphic Dark Palette
 * Background: Solid dark charcoal (#222222) with absolutely NO blue/violet tint.
 * Accent: Light pink & soft rose (#F4A6B8, #FBC4CE, #B86477).
 * Text: Warm cream and soft neutral gray.
 */
object NeumorphColors {
    val Background = Color(0xFF222222)
    val Surface = Color(0xFF222222)
    val SurfacePressed = Color(0xFF1B1B1B)
    val SurfaceLight = Color(0xFF282828)

    // Light Pink Accents (Soft Sakura & Pastel Rose)
    val AccentCopper = Color(0xFFF4A6B8) // Light Pink primary
    val AccentCopperLight = Color(0xFFFBC4CE) // Soft Light Pink
    val AccentCopperDark = Color(0xFFB86477) // Muted Rose Pink for gradients
    val AccentWarmGold = Color(0xFFF2A7B5)

    // Aliases for explicit Pink naming
    val AccentPink = AccentCopper
    val AccentPinkLight = AccentCopperLight
    val AccentPinkDark = AccentCopperDark

    // Text Hierarchy
    val TextCream = Color(0xFFF5F0EB)
    val TextMuted = Color(0xFFA8A39D)
    val TextFaint = Color(0xFF6E6A65)

    // Status Colors (Warm & Neutral)
    val StatusWarning = Color(0xFFD99B26)
    val StatusError = Color(0xFFD9534F)
    val StatusSuccess = Color(0xFFF4A6B8)
}

/**
 * Neomorphic Extruded Surface Modifier
 * Casts a soft dark ambient shadow at the bottom-right and renders a subtle top-left light highlight border.
 */
fun Modifier.neumorphicExtruded(
    cornerRadius: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    backgroundColor: Color = NeumorphColors.Surface,
    elevation: Dp = 4.dp
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.7f),
        spotColor = Color.Black.copy(alpha = 0.7f)
    )
    .clip(shape)
    .background(backgroundColor)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = 0.09f),
            0.35f to Color.White.copy(alpha = 0.02f),
            0.65f to Color.Transparent,
            1.0f to Color.Black.copy(alpha = 0.6f),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset.Infinite
        ),
        shape = shape
    )

/**
 * Neomorphic Recessed Surface Modifier
 * Renders an inset, pressed surface with an inner shadow on top/left and inner highlight on bottom/right.
 */
fun Modifier.neumorphicRecessed(
    cornerRadius: Dp = 14.dp,
    backgroundColor: Color = NeumorphColors.SurfacePressed
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .drawWithContent {
        drawContent()
        val radius = cornerRadius.toPx()
        // Top-left dark shadow
        drawRoundRect(
            brush = Brush.linearGradient(
                0.0f to Color.Black.copy(alpha = 0.65f),
                0.15f to Color.Black.copy(alpha = 0.3f),
                0.45f to Color.Transparent,
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
        // Dual-edge border: top-left dark, bottom-right soft light highlight
        drawRoundRect(
            brush = Brush.linearGradient(
                0.0f to Color.Black.copy(alpha = 0.8f),
                0.5f to Color.Transparent,
                1.0f to Color.White.copy(alpha = 0.1f),
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset(size.width, size.height)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            style = Stroke(width = 1.dp.toPx())
        )
    }

enum class AudioQualityOption(val title: String, val desc: String) {
    HIGH("High Quality (itag 251 - Opus)", "Highest audio stream (~160 kbps Opus)"),
    NORMAL("Normal Quality (itag 140 - AAC)", "Standard AAC M4A (~128 kbps)"),
    LOW("Data Saver (Lowest)", "Minimal file size (~50-70 kbps)")
}

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeumorphColors.Background)
    ) {
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .neumorphicExtruded(
                cornerRadius = cornerRadius,
                shape = shape,
                backgroundColor = NeumorphColors.Surface
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .neumorphicRecessed(cornerRadius = 14.dp, backgroundColor = NeumorphColors.SurfacePressed)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = NeumorphColors.TextMuted.copy(alpha = 0.6f)) },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            singleLine = singleLine,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedTextColor = NeumorphColors.TextCream,
                unfocusedTextColor = NeumorphColors.TextCream,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = NeumorphColors.AccentCopper
            )
        )
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    content: (@Composable RowScope.() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.shadow(
            elevation = if (enabled) 4.dp else 0.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.6f),
            spotColor = Color.Black.copy(alpha = 0.6f)
        ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = NeumorphColors.AccentCopper,
            contentColor = NeumorphColors.TextCream,
            disabledContainerColor = NeumorphColors.AccentCopper.copy(alpha = 0.35f),
            disabledContentColor = NeumorphColors.TextMuted.copy(alpha = 0.4f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.25f),
                    Color.Black.copy(alpha = 0.4f)
                )
            )
        )
    ) {
        if (content != null) {
            content()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (text != null) {
                    Text(text = text, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun GlassChip(
    modifier: Modifier = Modifier,
    title: String = "",
    label: String = title,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onRemove: (() -> Unit)? = null
) {
    val displayLabel = if (title.isNotEmpty()) title else label
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) NeumorphColors.AccentCopper.copy(alpha = 0.25f)
                else NeumorphColors.SurfacePressed
            )
            .border(
                width = 1.dp,
                brush = if (selected) {
                    SolidColor(NeumorphColors.AccentCopper)
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.5f))
                    )
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = displayLabel,
                color = if (selected) NeumorphColors.AccentCopperLight else NeumorphColors.TextCream,
                style = MaterialTheme.typography.labelMedium
            )
            if (onRemove != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = NeumorphColors.TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onRemove)
                )
            }
        }
    }
}

/**
 * Strictly Inline Recessed Quality Selector.
 * Replaces ExposedDropdownMenuBox/Popup to guarantee ZERO floating overlays or detached windows.
 * Expands directly in-line using AnimatedVisibility, pushing subsequent layout content down smoothly.
 */
@Composable
fun InlineQualitySelector(
    selectedOption: AudioQualityOption,
    onOptionSelected: (AudioQualityOption) -> Unit,
    modifier: Modifier = Modifier,
    options: List<AudioQualityOption> = AudioQualityOption.values().toList()
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .neumorphicRecessed(cornerRadius = 16.dp, backgroundColor = NeumorphColors.SurfacePressed)
            .padding(12.dp)
    ) {
        // Toggle Row (Header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedOption.title,
                    color = NeumorphColors.TextCream,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = selectedOption.desc,
                    color = NeumorphColors.AccentCopperLight,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeumorphColors.Surface)
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.5f))
                        ),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse options" else "Expand options",
                    tint = NeumorphColors.AccentCopper,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Inline expanding list that pushes all content below it in the parent Column
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) NeumorphColors.SurfaceLight else NeumorphColors.Surface
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) NeumorphColors.AccentCopper else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onOptionSelected(option)
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Neomorphic radio indicator
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) NeumorphColors.AccentCopper else Color(0xFF2A2A2A))
                                .border(
                                    1.dp,
                                    if (isSelected) NeumorphColors.AccentCopperLight else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(9.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(NeumorphColors.Background)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.title,
                                color = if (isSelected) NeumorphColors.TextCream else NeumorphColors.TextMuted,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = option.desc,
                                color = if (isSelected) NeumorphColors.AccentCopperLight else NeumorphColors.TextFaint,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Backward-compatible alias for GlassDropdownMenu that routes directly to the inline selector,
 * preventing any floating popup from ever being rendered.
 */
@Composable
fun GlassDropdownMenu(
    selectedOption: AudioQualityOption,
    onOptionSelected: (AudioQualityOption) -> Unit,
    modifier: Modifier = Modifier,
    options: List<AudioQualityOption> = AudioQualityOption.values().toList()
) {
    InlineQualitySelector(
        selectedOption = selectedOption,
        onOptionSelected = onOptionSelected,
        modifier = modifier,
        options = options
    )
}

/**
 * Neomorphic Segmented Toggle / Tab Row
 * Renders a recessed base container with highlighted/extruded active segments.
 */
@Composable
fun NeomorphicSegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .neumorphicRecessed(cornerRadius = 16.dp, backgroundColor = NeumorphColors.SurfacePressed)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, title ->
            val isSelected = index == selectedIndex
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(NeumorphColors.SurfaceLight)
                                .border(
                                    1.dp,
                                    Brush.linearGradient(
                                        listOf(
                                            NeumorphColors.AccentCopper,
                                            NeumorphColors.AccentCopperDark
                                        )
                                    ),
                                    shape
                                )
                        } else {
                            Modifier.background(Color.Transparent)
                        }
                    )
                    .clickable { onOptionSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = if (isSelected) NeumorphColors.TextCream else NeumorphColors.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

