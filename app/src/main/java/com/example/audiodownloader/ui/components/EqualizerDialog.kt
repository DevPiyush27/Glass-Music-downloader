package com.example.audiodownloader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audiodownloader.audio.EqualizerBand
import com.example.audiodownloader.audio.EqualizerState
import kotlin.math.roundToInt

@Composable
fun EqualizerDialog(
    state: EqualizerState,
    onToggleEnabled: (Boolean) -> Unit,
    onBandLevelChange: (bandIndex: Short, levelMb: Short) -> Unit,
    onSelectPreset: (presetIndex: Short) -> Unit,
    onResetToFlat: () -> Unit,
    onBassBoostChange: (strength: Short) -> Unit,
    onBassBoostToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val activeAccent = LocalNeomorphicAccent.current
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(NeumorphColors.Surface)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                activeAccent.copy(alpha = 0.55f),
                                activeAccent.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header Bar: Icon, Title, Status & Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .neumorphicExtruded(
                                        cornerRadius = 14.dp,
                                        backgroundColor = NeumorphColors.Surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = if (state.isEnabled) activeAccent else NeumorphColors.TextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Audio Equalizer",
                                    color = NeumorphColors.TextCream,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (state.isEnabled) "Hardware Engine Active" else "Engine Bypassed",
                                    color = if (state.isEnabled) activeAccent else NeumorphColors.TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Neomorphic ON/OFF pill button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (state.isEnabled) activeAccent.copy(alpha = 0.2f)
                                        else NeumorphColors.SurfacePressed
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (state.isEnabled) activeAccent else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onToggleEnabled(!state.isEnabled) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (state.isEnabled) "ON" else "OFF",
                                    color = if (state.isEnabled) activeAccent else NeumorphColors.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Dismiss button
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(NeumorphColors.SurfacePressed)
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = NeumorphColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!state.isAvailable) {
                        // Notice when audioSession is not yet available or device has no fx
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neumorphicRecessed(
                                    cornerRadius = 16.dp,
                                    backgroundColor = NeumorphColors.SurfacePressed
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = activeAccent,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ready to Equalize",
                                    color = NeumorphColors.TextCream,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Play a song to bind the equalizer hardware session to ExoPlayer.",
                                    color = NeumorphColors.TextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Scrollable content area
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(scrollState)
                        ) {
                            // Presets Row Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Presets",
                                    color = NeumorphColors.TextCream,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                TextButton(
                                    onClick = onResetToFlat,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = "Reset to Flat",
                                        tint = activeAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Flat",
                                        color = activeAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Presets List
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // "Custom" Chip
                                item {
                                    val isCustom = state.currentPreset == (-1).toShort()
                                    PresetChip(
                                        title = "Custom",
                                        isSelected = isCustom,
                                        activeAccent = activeAccent,
                                        onClick = { /* already custom or stays custom */ }
                                    )
                                }

                                itemsIndexed(state.presets) { index, presetName ->
                                    val isSelected = state.currentPreset == index.toShort()
                                    PresetChip(
                                        title = presetName,
                                        isSelected = isSelected,
                                        activeAccent = activeAccent,
                                        onClick = { onSelectPreset(index.toShort()) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Frequency Bands
                            Text(
                                text = "Frequency Bands",
                                color = NeumorphColors.TextCream,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.bands.forEach { band ->
                                    BandSliderRow(
                                        band = band,
                                        isEnabled = state.isEnabled,
                                        activeAccent = activeAccent,
                                        onLevelChange = { newMb ->
                                            onBandLevelChange(band.bandIndex, newMb)
                                        }
                                    )
                                }
                            }

                            // Optional BassBoost Section
                            if (state.isBassBoostSupported) {
                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = if (state.bassBoostEnabled) activeAccent else NeumorphColors.TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Bass Boost",
                                            color = NeumorphColors.TextCream,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Bass Boost On/Off pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (state.bassBoostEnabled) activeAccent.copy(alpha = 0.2f)
                                                else NeumorphColors.SurfacePressed
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (state.bassBoostEnabled) activeAccent else Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { onBassBoostToggle(!state.bassBoostEnabled) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (state.bassBoostEnabled) "ON" else "OFF",
                                            color = if (state.bassBoostEnabled) activeAccent else NeumorphColors.TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .neumorphicRecessed(
                                            cornerRadius = 14.dp,
                                            backgroundColor = NeumorphColors.SurfacePressed
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Slider(
                                        value = state.bassBoostStrength.toFloat(),
                                        onValueChange = { onBassBoostChange(it.roundToInt().toShort()) },
                                        valueRange = 0f..1000f,
                                        enabled = state.isEnabled && state.bassBoostEnabled,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = activeAccent,
                                            activeTrackColor = activeAccent,
                                            inactiveTrackColor = NeumorphColors.SurfaceLight,
                                            disabledThumbColor = NeumorphColors.TextMuted.copy(alpha = 0.3f),
                                            disabledActiveTrackColor = NeumorphColors.TextMuted.copy(alpha = 0.2f)
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    val percent = (state.bassBoostStrength / 10f).roundToInt()
                                    Text(
                                        text = "$percent%",
                                        color = if (state.bassBoostEnabled && state.isEnabled) activeAccent else NeumorphColors.TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(42.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    title: String,
    isSelected: Boolean,
    activeAccent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (isSelected) activeAccent.copy(alpha = 0.25f)
                else NeumorphColors.SurfacePressed
            )
            .border(
                width = 1.dp,
                brush = if (isSelected) {
                    SolidColor(activeAccent)
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.5f))
                    )
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) activeAccent else NeumorphColors.TextCream,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun BandSliderRow(
    band: EqualizerBand,
    isEnabled: Boolean,
    activeAccent: Color,
    onLevelChange: (Short) -> Unit
) {
    var sliderValue by remember(band.currentLevelMb) {
        mutableFloatStateOf(band.currentLevelMb.toFloat())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicRecessed(
                cornerRadius = 14.dp,
                backgroundColor = NeumorphColors.SurfacePressed
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frequency Label Badge
        Text(
            text = band.centerFreqFormatted,
            color = NeumorphColors.TextCream,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(58.dp)
        )

        // Continuous Slider
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onLevelChange(it.roundToInt().toShort())
            },
            valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
            enabled = isEnabled,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = activeAccent,
                activeTrackColor = activeAccent,
                inactiveTrackColor = NeumorphColors.SurfaceLight,
                disabledThumbColor = NeumorphColors.TextMuted.copy(alpha = 0.3f),
                disabledActiveTrackColor = NeumorphColors.TextMuted.copy(alpha = 0.2f)
            )
        )

        // dB Output readout
        val db = sliderValue / 100f
        val dbFormatted = if (db > 0.05f) "+%.1f dB".format(db) else if (db < -0.05f) "%.1f dB".format(db) else "0.0 dB"
        Text(
            text = dbFormatted,
            color = if (isEnabled && kotlin.math.abs(db) > 0.1f) activeAccent else NeumorphColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.End
        )
    }
}
