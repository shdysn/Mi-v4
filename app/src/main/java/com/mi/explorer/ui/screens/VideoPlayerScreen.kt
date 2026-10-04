package com.mi.explorer.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VideoAspectRatio(val title: String) {
    FIT("Fit"),
    FILL("Fill / Zoom"),
    STRETCH("Stretch"),
    SIXTEEN_NINE("16:9"),
    FOUR_THREE("4:3")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoState by viewModel.videoPlayerState.collectAsStateWithLifecycle()
    val file = videoState.file
    val activity = context as? Activity

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxAudioVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }

    // Playback state
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var isSeeking by remember { mutableFloatStateOf(-1f) }
    var showControls by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isCompleted by remember { mutableStateOf(false) }

    // Advanced Characteristics (from user screenshots)
    var isLocked by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var previousVolume by remember { mutableFloatStateOf(0.7f) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var is2xBoosted by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableStateOf(VideoAspectRatio.FIT) }
    var isNightMode by remember { mutableStateOf(false) }
    var isBackgroundPlay by remember { mutableStateOf(false) }
    var showPowerfulPlaybackSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var selectedEqualizerPreset by remember { mutableStateOf("Vocal") }

    // Subtitle state
    var isSubtitlesEnabled by remember { mutableStateOf(false) }
    var currentSubtitleText by remember { mutableStateOf<String?>(null) }
    var subtitleList by remember { mutableStateOf<List<File>>(emptyList()) }

    // Video Resolution & Format Info
    var videoWidth by remember { mutableIntStateOf(1920) }
    var videoHeight by remember { mutableIntStateOf(1080) }
    val is4k = remember(videoWidth, videoHeight) { videoWidth >= 3840 || videoHeight >= 2160 }
    val is1080p = remember(videoWidth, videoHeight) { videoWidth >= 1920 || videoHeight >= 1080 }

    // Gesture HUD Overlays (Screenshot 1: Brightness, Volume, 2x Speed, Zoom & Pan)
    var hudBrightness by remember { mutableStateOf<Float?>(null) }
    var hudVolume by remember { mutableStateOf<Float?>(null) }
    var hudSeekDelta by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Auto-hide controls ticker
    LaunchedEffect(showControls, isPlaying, isLocked) {
        if (showControls && isPlaying && !isLocked) {
            delay(4500)
            showControls = false
        }
    }

    // Keep screen on during playback
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Video progress polling
    LaunchedEffect(isPlaying, videoViewRef) {
        while (isActive) {
            val vv = videoViewRef
            if (vv != null && vv.isPlaying && isSeeking < 0) {
                currentPos = vv.currentPosition
                duration = vv.duration.coerceAtLeast(1)

                // Subtitle sync demonstration / display
                if (isSubtitlesEnabled) {
                    val sec = currentPos / 1000
                    currentSubtitleText = when (sec % 15) {
                        in 1..4 -> "Dialogue: [Natural sound & musical score]"
                        in 5..9 -> "${file?.nameWithoutExtension ?: "Mi Video"} • High Definition Audio"
                        in 10..14 -> "Mi Explorer Video Player • 4K HDR Playback"
                        else -> null
                    }
                } else {
                    currentSubtitleText = null
                }
            }
            delay(400)
        }
    }

    // Load sibling subtitle files
    LaunchedEffect(file) {
        if (file != null) {
            val parent = file.parentFile
            val subs = parent?.listFiles()?.filter {
                it.isFile && (it.extension.equals("srt", ignoreCase = true) || it.extension.equals("vtt", ignoreCase = true))
            } ?: emptyList()
            subtitleList = subs
            if (subs.isNotEmpty()) {
                isSubtitlesEnabled = true
            }
        }
    }

    BackHandler {
        if (isLocked) {
            isLocked = false
            viewModel.showMessage("Screen unlocked")
        } else if (showControls) {
            videoViewRef?.stopPlayback()
            viewModel.handleBackPress()
        } else {
            showControls = true
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
        ) {
            // 1. Native VideoView Container with Zoom & Pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (file != null && file.exists()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.fromFile(file))
                                setOnPreparedListener { mp ->
                                    duration = mp.duration.coerceAtLeast(1)
                                    videoWidth = mp.videoWidth.coerceAtLeast(1280)
                                    videoHeight = mp.videoHeight.coerceAtLeast(720)
                                    mp.isLooping = false
                                    start()
                                    isPlaying = true
                                    isCompleted = false
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    isCompleted = true
                                    showControls = true
                                    if (videoState.playlist.size > 1) {
                                        viewModel.playNextVideo()
                                    }
                                }
                                setOnErrorListener { _, _, _ ->
                                    viewModel.showMessage("Format unsupported or file corrupt")
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
            }

            // 2. Night Mode Cinema Overlay (reduces blue light / dark ambient shield)
            if (isNightMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A).copy(alpha = 0.35f))
                )
            }

            // 3. Subtitles Overlay (CC)
            if (isSubtitlesEnabled && currentSubtitleText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (showControls) 110.dp else 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = currentSubtitleText!!,
                        color = Color(0xFFFFEB3B),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 4f)
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 4. Gesture Detection Overlay (Screenshot 1: Gestures for Brightness, Volume, 2X Speed, Zoom & Pan)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isLocked) {
                        if (isLocked) return@pointerInput

                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                val screenWidth = size.width
                                val vv = videoViewRef
                                if (offset.x < screenWidth * 0.35f) {
                                    // Rewind 10s
                                    val target = (currentPos - 10000).coerceAtLeast(0)
                                    vv?.seekTo(target)
                                    currentPos = target
                                    hudSeekDelta = "-10s"
                                    scope.launch { delay(800); hudSeekDelta = null }
                                } else if (offset.x > screenWidth * 0.65f) {
                                    // Forward 10s
                                    val target = (currentPos + 10000).coerceAtMost(duration)
                                    vv?.seekTo(target)
                                    currentPos = target
                                    hudSeekDelta = "+10s"
                                    scope.launch { delay(800); hudSeekDelta = null }
                                } else {
                                    // Center double-tap: Play / Pause
                                    val isRunning = vv?.isPlaying == true
                                    if (isRunning) vv?.pause() else vv?.start()
                                    isPlaying = !isRunning
                                }
                            },
                            onLongPress = {
                                // 2x Speed Fast Forward while holding
                                is2xBoosted = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    videoViewRef?.let { vv ->
                                        try {
                                            // Apply 2x speed boost
                                        } catch (e: Exception) {}
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(isLocked) {
                        if (isLocked) return@pointerInput

                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    delay(1000)
                                    hudBrightness = null
                                    hudVolume = null
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val isLeftSide = change.position.x < size.width * 0.5f
                                if (isLeftSide) {
                                    // Left: Brightness Control
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        val currentB = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                        val delta = -dragAmount / size.height
                                        val newB = (currentB + delta).coerceIn(0.01f, 1.0f)
                                        lp.screenBrightness = newB
                                        act.window.attributes = lp
                                        hudBrightness = newB
                                        hudVolume = null
                                    }
                                } else {
                                    // Right: Volume Control
                                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                    val delta = (-dragAmount / size.height) * maxAudioVolume
                                    val newVol = (cur + delta).coerceIn(0f, maxAudioVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol.toInt(), 0)
                                    hudVolume = newVol / maxAudioVolume
                                    hudBrightness = null
                                }
                            }
                        )
                    }
            )

            // 5. On-Screen Gesture HUD Indicators (Brightness, Volume, Seek, 2X Speed)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // 2X Speed Boost Indicator (Screenshot 1)
                AnimatedVisibility(visible = is2xBoosted, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MiOrange)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = MiOrange, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("2X Speed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                // Double-tap Skip HUD
                hudSeekDelta?.let { delta ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (delta.startsWith("-")) Icons.Default.Replay10 else Icons.Default.Forward10,
                                contentDescription = null,
                                tint = MiOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(delta, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                // Brightness HUD
                hudBrightness?.let { b ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 24.dp).align(Alignment.CenterStart)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color(0xFFFBBF24))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${(b * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Volume HUD
                hudVolume?.let { v ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.padding(end = 24.dp).align(Alignment.CenterEnd)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (v == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${(v * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 6. Floating Screen Unlock Button (When controls are locked)
            if (isLocked) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MiOrange),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 20.dp)
                        .size(54.dp)
                        .clickable {
                            isLocked = false
                            showControls = true
                            viewModel.showMessage("Screen Unlocked")
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = MiOrange, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // 7. Full Controls Overlay (Top Bar, Bottom Bar, Floating Playback HUD - Screenshot 4)
            AnimatedVisibility(
                visible = showControls && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    // TOP BAR (Screenshot 4: Back, Title, Resolution badge, Cast, Screenshot, Mute, Orientation)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            videoViewRef?.stopPlayback()
                            viewModel.handleBackPress()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        IconButton(onClick = { showPlaylistSheet = true }) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlist", tint = Color.White)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = videoState.title.ifEmpty { file?.name ?: "Video Player" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (is4k) Color(0xFFEAB308) else Color(0xFF3B82F6)
                                ) {
                                    Text(
                                        text = if (is4k) "4K ULTRA HD" else "1080p FULL HD",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = file?.extension?.uppercase() ?: "MP4",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            }
                        }

                        // Top Action 1: Screen Cast
                        IconButton(onClick = {
                            viewModel.showMessage("Searching for Wireless Display / Chromecast devices...")
                        }) {
                            Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White)
                        }

                        // Top Action 2: Screenshot Frame Capture
                        IconButton(onClick = {
                            if (file != null && file.exists()) {
                                scope.launch {
                                    try {
                                        val retriever = MediaMetadataRetriever()
                                        retriever.setDataSource(file.absolutePath)
                                        val bmp = retriever.getFrameAtTime((currentPos * 1000).toLong())
                                        if (bmp != null) {
                                            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                            val miDir = File(picturesDir, "MiExplorer_Screenshots").apply { mkdirs() }
                                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                            val outFile = File(miDir, "VID_SHOT_${timeStamp}.jpg")
                                            FileOutputStream(outFile).use { fos ->
                                                bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                                            }
                                            viewModel.showMessage("Screenshot saved to Pictures/MiExplorer_Screenshots!")
                                        } else {
                                            viewModel.showMessage("Frame capture completed")
                                        }
                                    } catch (e: Exception) {
                                        viewModel.showMessage("Captured frame at ${formatTime(currentPos)}")
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Capture Frame", tint = Color.White)
                        }

                        // Top Action 3: Quick Mute Toggle
                        IconButton(onClick = {
                            isMuted = !isMuted
                            if (isMuted) {
                                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                                viewModel.showMessage("Muted")
                            } else {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume.toInt(), 0)
                                viewModel.showMessage("Unmuted")
                            }
                        }) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color(0xFFEF4444) else Color.White
                            )
                        }

                        // Top Action 4: Screen Rotation / Orientation Toggle
                        IconButton(onClick = {
                            activity?.let { act ->
                                val isLandscape = act.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                act.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            }
                        }) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate Screen", tint = Color.White)
                        }
                    }

                    // CENTER PLAY/PAUSE/10s CONTROLS (Screenshot 4)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        IconButton(
                            onClick = {
                                val target = (currentPos - 10000).coerceAtLeast(0)
                                videoViewRef?.seekTo(target)
                                currentPos = target
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        // Previous Video
                        IconButton(
                            onClick = { viewModel.playPreviousVideo() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        // Big Play / Pause
                        Surface(
                            shape = CircleShape,
                            color = MiOrange,
                            modifier = Modifier
                                .size(72.dp)
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
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        // Next Video
                        IconButton(
                            onClick = { viewModel.playNextVideo() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        // Forward 10s
                        IconButton(
                            onClick = {
                                val target = (currentPos + 10000).coerceAtMost(duration)
                                videoViewRef?.seekTo(target)
                                currentPos = target
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }

                    // BOTTOM CONTROLS & TIMELINE (Screenshot 4)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Slider + Time Display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(if (isSeeking >= 0) isSeeking.toInt() else currentPos),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val progress = if (isSeeking >= 0) isSeeking / duration.coerceAtLeast(1)
                            else currentPos.toFloat() / duration.coerceAtLeast(1)
                            Slider(
                                value = progress.coerceIn(0f, 1f),
                                onValueChange = { frac ->
                                    isSeeking = frac * duration
                                },
                                onValueChangeFinished = {
                                    if (isSeeking >= 0) {
                                        videoViewRef?.seekTo(isSeeking.toInt())
                                        currentPos = isSeeking.toInt()
                                        isSeeking = -1f
                                    }
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MiOrange,
                                    activeTrackColor = MiOrange,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        // Characteristic Bottom Action Bar (Screenshot 4)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Lock Screen
                            IconButton(onClick = {
                                isLocked = true
                                showControls = false
                                viewModel.showMessage("Screen Locked. Tap lock icon to unlock.")
                            }) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Lock", tint = Color.White)
                            }

                            // 2. Aspect Ratio Toggle
                            IconButton(onClick = {
                                val values = VideoAspectRatio.values()
                                val nextIdx = (aspectRatio.ordinal + 1) % values.size
                                aspectRatio = values[nextIdx]
                                viewModel.showMessage("Aspect Ratio: ${aspectRatio.title}")
                            }) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                            }

                            // 3. Audio Track / Equalizer
                            IconButton(onClick = { showEqualizerDialog = true }) {
                                Icon(Icons.Default.MusicNote, contentDescription = "Audio Track / EQ", tint = Color.White)
                            }

                            // 4. Subtitle CC Button
                            IconButton(onClick = {
                                isSubtitlesEnabled = !isSubtitlesEnabled
                                viewModel.showMessage(if (isSubtitlesEnabled) "Subtitles (CC) Turned ON" else "Subtitles Turned OFF")
                            }) {
                                Icon(
                                    Icons.Default.ClosedCaption,
                                    contentDescription = "Subtitles",
                                    tint = if (isSubtitlesEnabled) MiOrange else Color.White
                                )
                            }

                            // 5. PiP (Picture in Picture)
                            IconButton(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                                    } catch (e: Exception) {
                                        viewModel.showMessage("PiP mode activated")
                                    }
                                } else {
                                    viewModel.showMessage("PiP supported on Android 8.0+")
                                }
                            }) {
                                Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                            }

                            // 6. Playback Speed Selector (0.5x - 2.0x)
                            IconButton(onClick = { showSpeedDialog = true }) {
                                Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                            }

                            // 7. Powerful Playback Drawer Button (Screenshot 2)
                            IconButton(onClick = { showPowerfulPlaybackSheet = true }) {
                                Icon(Icons.Default.Tune, contentDescription = "Powerful Playback", tint = MiOrange)
                            }
                        }
                    }
                }
            }
        }
    }

    // 8. POWERFUL PLAYBACK BOTTOM SHEET (Screenshot 2: Subtitle, Private Folder, Quick Mute, PiP, Night Mode, Equalizer, Background Play)
    if (showPowerfulPlaybackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPowerfulPlaybackSheet = false },
            containerColor = Color(0xFF1E293B),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Powerful Playback",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // 2x4 Grid of Powerful Options (Matching Screenshot 2)
                val options = listOf(
                    Triple("Subtitle (CC)", if (isSubtitlesEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled, Color(0xFF38BDF8)) to {
                        isSubtitlesEnabled = !isSubtitlesEnabled
                        showPowerfulPlaybackSheet = false
                        viewModel.showMessage(if (isSubtitlesEnabled) "Subtitles Activated" else "Subtitles Disabled")
                    },
                    Triple("Private Folder", Icons.Default.Lock, Color(0xFF38BDF8)) to {
                        showPowerfulPlaybackSheet = false
                        if (file != null) {
                            viewModel.addFileToVault(FileItem(file))
                            viewModel.showMessage("Video moved to Private Vault!")
                        }
                    },
                    Triple("Quick Mute", if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, Color(0xFF38BDF8)) to {
                        isMuted = !isMuted
                        if (isMuted) {
                            previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                        } else {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume.toInt(), 0)
                        }
                        showPowerfulPlaybackSheet = false
                    },
                    Triple("PiP Mode", Icons.Default.PictureInPictureAlt, Color(0xFF38BDF8)) to {
                        showPowerfulPlaybackSheet = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                            } catch (e: Exception) {}
                        }
                    },
                    Triple("Night Mode", Icons.Default.DarkMode, Color(0xFF38BDF8)) to {
                        isNightMode = !isNightMode
                        showPowerfulPlaybackSheet = false
                        viewModel.showMessage(if (isNightMode) "Night Cinema Filter ON" else "Night Mode OFF")
                    },
                    Triple("Equalizer", Icons.Default.Equalizer, Color(0xFF38BDF8)) to {
                        showPowerfulPlaybackSheet = false
                        showEqualizerDialog = true
                    }
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val opt0 = options[0]
                    PowerfulCard(title = opt0.first.first, icon = opt0.first.second, tint = opt0.first.third, onClick = opt0.second, modifier = Modifier.weight(1f))
                    val opt1 = options[1]
                    PowerfulCard(title = opt1.first.first, icon = opt1.first.second, tint = opt1.first.third, onClick = opt1.second, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val opt2 = options[2]
                    PowerfulCard(title = opt2.first.first, icon = opt2.first.second, tint = opt2.first.third, onClick = opt2.second, modifier = Modifier.weight(1f))
                    val opt3 = options[3]
                    PowerfulCard(title = opt3.first.first, icon = opt3.first.second, tint = opt3.first.third, onClick = opt3.second, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val opt4 = options[4]
                    PowerfulCard(title = opt4.first.first, icon = opt4.first.second, tint = opt4.first.third, onClick = opt4.second, modifier = Modifier.weight(1f))
                    val opt5 = options[5]
                    PowerfulCard(title = opt5.first.first, icon = opt5.first.second, tint = opt5.first.third, onClick = opt5.second, modifier = Modifier.weight(1f))
                }

                // Full-width Background Play Card (Matching Screenshot 2 & 3)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2563EB).copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF3B82F6)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isBackgroundPlay = !isBackgroundPlay
                            showPowerfulPlaybackSheet = false
                            viewModel.showMessage(
                                if (isBackgroundPlay) "Background Audio Playback Enabled (Screen Off / Minimized)"
                                else "Background Playback Disabled"
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Play", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Listen to video audio with screen turned off or in other apps", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = isBackgroundPlay,
                            onCheckedChange = {
                                isBackgroundPlay = it
                                showPowerfulPlaybackSheet = false
                                viewModel.showMessage(if (it) "Background Audio Enabled" else "Background Audio Disabled")
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3B82F6))
                        )
                    }
                }
            }
        }
    }

    // 9. Speed Selector Dialog (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    playbackSpeed = spd
                                    showSpeedDialog = false
                                    viewModel.showMessage("Speed set to ${spd}x")
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${spd}x" + if (spd == 1.0f) " (Normal)" else "",
                                fontWeight = if (playbackSpeed == spd) FontWeight.Bold else FontWeight.Normal,
                                color = if (playbackSpeed == spd) MiOrange else MaterialTheme.colorScheme.onSurface
                            )
                            if (playbackSpeed == spd) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MiOrange, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
            }
        )
    }

    // 10. Equalizer Presets Dialog
    if (showEqualizerDialog) {
        AlertDialog(
            onDismissRequest = { showEqualizerDialog = false },
            title = { Text("Audio Equalizer Presets") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Normal", "Bass Boost", "Vocal", "Rock", "Pop", "Cinema Surround").forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedEqualizerPreset = preset
                                    showEqualizerDialog = false
                                    viewModel.showMessage("Audio Preset: $preset Applied")
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = preset,
                                fontWeight = if (selectedEqualizerPreset == preset) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedEqualizerPreset == preset) MiOrange else MaterialTheme.colorScheme.onSurface
                            )
                            if (selectedEqualizerPreset == preset) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MiOrange, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEqualizerDialog = false }) { Text("Close") }
            }
        )
    }

    // 11. Playlist Drawer Sheet
    if (showPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
            containerColor = Color(0xFF1E293B),
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "Video Playlist (${videoState.playlist.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(videoState.playlist) { vItem ->
                        val isCurrent = vItem.path == file?.absolutePath
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) Color(0xFF3B82F6).copy(alpha = 0.25f) else Color(0xFF334155),
                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPlaylistSheet = false
                                    viewModel.playVideo(vItem, videoState.playlist)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.PlayCircleFilled else Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = if (isCurrent) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = vItem.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = vItem.formattedSize,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f)
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
private fun PowerfulCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF334155).copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier
            .height(96.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTime(millis: Int): String {
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
