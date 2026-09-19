package com.example.audiodownloader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.audiodownloader.domain.model.LocalTrack

/**
 * Neomorphic Playing Queue Screen with Drag-and-Drop Reordering.
 *
 * Characteristics:
 *  - Tactile dark charcoal (#222222) extruded container base.
 *  - Currently playing song highlighted dynamically via [LocalNeomorphicAccent.current].
 *  - Real-time drag-and-drop reordering with long-press detection.
 *  - Visual elevation and scale when lifting an item.
 *  - Quick actions to play, remove, or clear the queue.
 */
@Composable
fun NeomorphicQueueScreen(
    queue: List<LocalTrack>,
    currentIndex: Int,
    isPlaying: Boolean,
    onTrackClick: (Int) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAccent = LocalNeomorphicAccent.current
    val listState = rememberLazyListState()

    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Queue Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .neumorphicExtruded(cornerRadius = 12.dp, backgroundColor = NeumorphColors.Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = activeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Playing Queue",
                        color = NeumorphColors.TextCream,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (queue.isNotEmpty()) "${queue.size} track(s) • Hold & drag to reorder" else "Queue is empty",
                        color = NeumorphColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            if (queue.size > 1) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .neumorphicExtruded(
                            cornerRadius = 10.dp,
                            backgroundColor = NeumorphColors.Surface,
                            elevation = 2.dp
                        )
                        .clickable(onClick = onClearQueue)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear Queue",
                            tint = NeumorphColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Clear",
                            color = NeumorphColors.TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Empty State
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .neumorphicRecessed(cornerRadius = 28.dp, backgroundColor = NeumorphColors.SurfacePressed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = activeAccent,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "No Tracks in Queue",
                            color = NeumorphColors.TextCream,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Play songs from your library or tap \"Play Next\" on any playlist to build your queue.",
                            color = NeumorphColors.TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            // Reorderable LazyColumn
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
            ) {
                itemsIndexed(
                    items = queue,
                    key = { index, track -> "${track.id}_$index" }
                ) { index, track ->
                    val isCurrent = index == currentIndex
                    val isDragging = draggedItemIndex == index

                    val elevation = if (isDragging) 8.dp else 3.dp
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.025f else 1.0f,
                        label = "QueueItemScale"
                    )

                    val itemShape = RoundedCornerShape(14.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 5f else 1f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                scaleX = scale
                                scaleY = scale
                            }
                            .neumorphicExtruded(
                                cornerRadius = 14.dp,
                                backgroundColor = if (isCurrent) NeumorphColors.SurfaceLight else NeumorphColors.Surface,
                                elevation = elevation
                            )
                            .border(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                brush = if (isCurrent) {
                                    Brush.linearGradient(
                                        listOf(
                                            activeAccent,
                                            activeAccent.copy(alpha = 0.35f)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.05f),
                                            Color.Black.copy(alpha = 0.35f)
                                        )
                                    )
                                },
                                shape = itemShape
                            )
                            .clickable { onTrackClick(index) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Left Status Badge (Playing Waveform or Index)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .neumorphicRecessed(
                                        cornerRadius = 10.dp,
                                        backgroundColor = NeumorphColors.SurfacePressed
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCurrent) {
                                    if (isPlaying) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = "Playing",
                                            tint = activeAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Paused",
                                            tint = activeAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "${index + 1}",
                                        color = NeumorphColors.TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Track Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = if (isCurrent) activeAccent else NeumorphColors.TextCream,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (track.artist.isBlank()) "Unknown Artist" else track.artist,
                                    color = NeumorphColors.TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Remove Item from Queue Button
                            IconButton(
                                onClick = { onRemoveItem(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove track from queue",
                                    tint = NeumorphColors.TextMuted.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Drag Handle Icon with Gesture Detection
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .pointerInput(queue) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedItemIndex = index
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y

                                                val currentDragging = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                                                val layoutInfo = listState.layoutInfo
                                                val currentItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentDragging }
                                                    ?: return@detectDragGesturesAfterLongPress

                                                val currentMid = currentItem.offset + currentItem.size / 2f + dragOffsetY

                                                val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                                    item.index != currentDragging && currentMid in item.offset.toFloat()..(item.offset + item.size).toFloat()
                                                }

                                                if (targetItem != null) {
                                                    val targetIndex = targetItem.index
                                                    onMoveItem(currentDragging, targetIndex)
                                                    dragOffsetY += (currentItem.offset - targetItem.offset)
                                                    draggedItemIndex = targetIndex
                                                }
                                            },
                                            onDragEnd = {
                                                draggedItemIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedItemIndex = null
                                                dragOffsetY = 0f
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = if (isCurrent || isDragging) activeAccent else NeumorphColors.TextMuted.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
