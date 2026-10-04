package com.mi.explorer.ui.screens

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import com.mi.explorer.utils.FileOpener
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoState by viewModel.videoPlayerState.collectAsStateWithLifecycle()
    val file = videoState.file

    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekTarget by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isCompleted by remember { mutableStateOf(false) }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Position updater ticker
    LaunchedEffect(isPlaying, videoViewRef) {
        while (isActive) {
            val vv = videoViewRef
            if (vv != null && vv.isPlaying && !isSeeking) {
                currentPos = vv.currentPosition
                duration = vv.duration.coerceAtLeast(1)
            }
            delay(500)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("video_player_screen"),
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    showControls = !showControls
                }
        ) {
            // Native Video View
            if (file != null && file.exists()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.fromFile(file))
                            setOnPreparedListener { mp ->
                                duration = mp.duration.coerceAtLeast(1)
                                mp.isLooping = false
                                start()
                                isPlaying = true
                                isCompleted = false
                            }
                            setOnCompletionListener {
                                isPlaying = false
                                isCompleted = true
                                showControls = true
                            }
                            setOnErrorListener { _, _, _ ->
                                viewModel.showMessage("Cannot play this video format")
                                true
                            }
                            videoViewRef = this
                        }
                    },
                    update = { vv ->
                        videoViewRef = vv
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Video file not found", color = Color.White)
                }
            }

            // Controls Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            videoViewRef?.stopPlayback()
                            viewModel.handleBackPress()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = videoState.title.ifEmpty { file?.name ?: "Video Player" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (file != null) {
                            IconButton(onClick = {
                                FileOpener.shareFile(context, com.mi.explorer.data.model.FileItem(file))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Center Play/Pause/Replay Button
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable {
                                val vv = videoViewRef ?: return@clickable
                                if (isCompleted) {
                                    vv.seekTo(0)
                                    vv.start()
                                    isPlaying = true
                                    isCompleted = false
                                } else if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            },
                        color = MiOrange.copy(alpha = 0.9f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isCompleted -> Icons.Default.Replay
                                    isPlaying -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    // Bottom Control Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        // Slider
                        val displayPos = if (isSeeking) seekTarget.toInt() else currentPos
                        val maxPos = duration.coerceAtLeast(1)

                        Slider(
                            value = (displayPos.toFloat() / maxPos.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                isSeeking = true
                                seekTarget = fraction * maxPos
                            },
                            onValueChangeFinished = {
                                videoViewRef?.seekTo(seekTarget.toInt())
                                currentPos = seekTarget.toInt()
                                isSeeking = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MiOrange,
                                activeTrackColor = MiOrange,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Timestamps and Nav
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatVideoTime(displayPos)} / ${formatVideoTime(duration)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (videoState.playlist.size > 1) {
                                    IconButton(onClick = { viewModel.playPreviousVideo() }) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Previous Video",
                                            tint = Color.White
                                        )
                                    }

                                    IconButton(onClick = { viewModel.playNextVideo() }) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Video",
                                            tint = Color.White
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
}

private fun formatVideoTime(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
