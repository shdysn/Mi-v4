package com.mi.explorer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.ui.components.ApkInstallDialog
import com.mi.explorer.ui.components.ChecksumDialog
import com.mi.explorer.ui.components.MiFullAudioPlayerSheet
import com.mi.explorer.ui.components.MiMiniAudioBar
import com.mi.explorer.ui.screens.*
import com.mi.explorer.ui.theme.MiExplorerTheme
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import com.mi.explorer.ui.viewmodel.Screen
import com.mi.explorer.utils.FileOpener

class MainActivity : ComponentActivity() {

    private val viewModel: ExplorerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val isAmoled by viewModel.isAmoledMode.collectAsStateWithLifecycle()
            MiExplorerTheme(amoledMode = isAmoled) {
                MiMainApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            val mimeType = intent.type ?: contentResolver.getType(uri) ?: ""
            val displayName = getFileNameFromUri(uri)

            val lowerName = displayName.lowercase()
            val lowerMime = mimeType.lowercase()

            when {
                // PDF Documents
                lowerName.endsWith(".pdf") || lowerMime.contains("pdf") -> {
                    try {
                        val cacheFile = java.io.File(cacheDir, displayName).apply {
                            contentResolver.openInputStream(uri)?.use { input ->
                                outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        viewModel.openPdfFile(cacheFile)
                    } catch (e: Exception) {
                        viewModel.showMessage("Failed to open PDF: ${e.localizedMessage}")
                    }
                }
                // ZIP Archives
                lowerName.endsWith(".zip") || lowerMime.contains("zip") -> {
                    viewModel.openZipFromUri(uri, displayName)
                }
                // HTML Files
                lowerName.endsWith(".html") || lowerName.endsWith(".htm") || lowerMime.contains("html") -> {
                    viewModel.openTextFromUri(uri, displayName)
                }
                // Text, JSON, XML, Logs, Markdown, CSV, Code, etc.
                else -> {
                    viewModel.openTextFromUri(uri, displayName)
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            name = cursor.getString(idx)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore query error
            }
        }
        return name ?: uri.lastPathSegment ?: "file.txt"
    }
}

@Composable
fun MiMainApp(viewModel: ExplorerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Build permissions list depending on Android version
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    // Permission launcher for standard runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            viewModel.onStoragePermissionGranted()
        }
    }

    // Request permissions on launch if not granted
    LaunchedEffect(Unit) {
        val hasMissing = permissionsToRequest.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (hasMissing) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Track if storage permission was already granted to avoid resetting directory on every ON_RESUME
    var hadStorageAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Lifecycle observer: when returning from system Settings, only refresh if permission was freshly granted
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }
                if (hasAllFilesAccess && !hadStorageAccess) {
                    hadStorageAccess = true
                    viewModel.onStoragePermissionGranted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    var apkChecksumTarget by remember { mutableStateOf<FileItem?>(null) }

    BackHandler(enabled = true) {
        val handled = viewModel.handleBackPress()
        if (!handled) {
            (context as? android.app.Activity)?.finish()
        }
    }

    val audioPlayerState by viewModel.audioPlayerState.collectAsStateWithLifecycle()
    val apkInstallTarget by viewModel.apkInstallTarget.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (audioPlayerState.isVisible) {
                MiMiniAudioBar(
                    state = audioPlayerState,
                    onExpand = { viewModel.toggleAudioExpanded() },
                    onPlayPause = { viewModel.toggleAudioPlayPause() },
                    onNext = { viewModel.playNextAudio() },
                    onClose = { viewModel.closeAudioPlayer() }
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentScreen,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "MiScreenTransition"
        ) { screen ->
            when (screen) {
                Screen.MAIN -> MainScreen(viewModel = viewModel)
                Screen.CLEANER -> CleanerScreen(viewModel = viewModel)
                Screen.FTP_SERVER -> FtpServerScreen(viewModel = viewModel)
                Screen.CATEGORY_VIEW -> CategoryViewScreen(viewModel = viewModel)
                Screen.TEXT_EDITOR -> TextEditorScreen(viewModel = viewModel)
                Screen.IMAGE_VIEWER -> ImageViewerScreen(viewModel = viewModel)
                Screen.APP_MANAGER -> AppManagerScreen(viewModel = viewModel)
                Screen.VAULT -> VaultScreen(viewModel = viewModel)
                Screen.DUPLICATES -> DuplicateFinderScreen(viewModel = viewModel)
                Screen.STORAGE_ANALYZER -> StorageAnalyzerScreen(viewModel = viewModel)
                Screen.ZIP_VIEWER -> ZipViewerScreen(viewModel = viewModel)
                Screen.TRASH -> TrashScreen(viewModel = viewModel)
                Screen.PDF_VIEWER -> PdfViewerScreen(viewModel = viewModel)
                Screen.VIDEO_PLAYER -> VideoPlayerScreen(viewModel = viewModel)
                Screen.NETWORK_DRIVES -> NetworkDrivesScreen(viewModel = viewModel)
                Screen.FAST_SHARE -> FastShareScreen(viewModel = viewModel)
                Screen.SOCIAL_HUB -> SocialHubScreen(viewModel = viewModel)
                Screen.WEB_SHARE -> WebShareScreen(viewModel = viewModel)
                Screen.FILE_SHREDDER -> FileShredderScreen(viewModel = viewModel)
                Screen.STATUS_SAVER -> StatusSaverScreen(viewModel = viewModel)
                Screen.SMART_COLLECTIONS -> SmartCollectionsScreen(viewModel = viewModel)
                Screen.TIME_MACHINE -> TimeMachineScreen(viewModel = viewModel)
            }
        }

        if (audioPlayerState.isExpanded) {
            MiFullAudioPlayerSheet(
                state = audioPlayerState,
                viewModel = viewModel,
                onDismiss = { viewModel.toggleAudioExpanded() }
            )
        }

        apkInstallTarget?.let { apk ->
            ApkInstallDialog(
                apk = apk,
                onDismiss = { viewModel.closeApkInstallDialog() },
                onInstall = {
                    FileOpener.installApk(context, apk.file)
                },
                onShare = {
                    FileOpener.shareFile(context, FileItem(apk.file))
                },
                onChecksum = {
                    apkChecksumTarget = FileItem(apk.file)
                }
            )
        }

        apkChecksumTarget?.let { item ->
            ChecksumDialog(
                item = item,
                onDismiss = { apkChecksumTarget = null }
            )
        }

        val xapkTarget by viewModel.xapkInstallTarget.collectAsStateWithLifecycle()
        val isInstallingXapk by viewModel.isInstallingXapk.collectAsStateWithLifecycle()
        val xapkProgress by viewModel.xapkInstallProgress.collectAsStateWithLifecycle()

        xapkTarget?.let { xapk ->
            com.mi.explorer.ui.components.XapkInstallDialog(
                xapkInfo = xapk,
                isInstalling = isInstallingXapk,
                progressText = xapkProgress,
                onDismiss = { viewModel.closeXapkDialog() },
                onInstall = { viewModel.installActiveXapk() }
            )
        }
    }
}
