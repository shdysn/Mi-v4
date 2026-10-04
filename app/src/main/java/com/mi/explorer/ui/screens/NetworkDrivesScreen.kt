package com.mi.explorer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.DriveProtocol
import com.mi.explorer.data.model.NetworkDrive
import com.mi.explorer.data.model.RemoteFileItem
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDrivesScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val drives by viewModel.networkDrives.collectAsStateWithLifecycle()
    val activeDrive by viewModel.activeNetworkDrive.collectAsStateWithLifecycle()
    val remoteFiles by viewModel.remoteFiles.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingNetworkDrive.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadNetworkDrives()
    }

    BackHandler {
        if (activeDrive != null) {
            viewModel.disconnectNetworkDrive()
        } else {
            viewModel.handleBackPress()
        }
    }

    Scaffold(
        modifier = modifier.testTag("network_drives_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (activeDrive != null) activeDrive!!.name else "Cloud & Network Drives",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (activeDrive != null) activeDrive!!.displaySubtitle else "SMB, WebDAV, Nextcloud & Network Storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeDrive != null) {
                            viewModel.disconnectNetworkDrive()
                        } else {
                            viewModel.handleBackPress()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activeDrive == null) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Drive")
                        }
                    } else {
                        IconButton(onClick = { viewModel.refreshRemoteFiles() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (activeDrive != null) {
            // Remote Explorer View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MiOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connected to ${activeDrive!!.serverHost}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.disconnectNetworkDrive() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Disconnect", fontSize = 12.sp)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(remoteFiles) { file ->
                        RemoteFileRow(
                            item = file,
                            onDownload = {
                                activeDrive?.let { drive ->
                                    viewModel.downloadNetworkFile(drive, file)
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    }
                }
            }
        } else {
            // List of Saved Drives
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Connect Network Storage",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Access your PC shared folders (SMB), NAS, Nextcloud, or WebDAV servers directly inside Mi Explorer.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Saved Network Drives (${drives.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(drives, key = { it.id }) { drive ->
                    NetworkDriveCard(
                        drive = drive,
                        onConnect = { viewModel.connectNetworkDrive(drive) },
                        onDelete = { viewModel.deleteNetworkDrive(drive.id) },
                        onTest = { viewModel.testNetworkDriveConnection(drive) }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add New Network Drive")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddNetworkDriveDialog(
            isTesting = isTesting,
            onDismiss = { showAddDialog = false },
            onSave = { newDrive ->
                viewModel.saveNetworkDrive(newDrive)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun NetworkDriveCard(
    drive: NetworkDrive,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        when (drive.protocol) {
                            DriveProtocol.WEBDAV -> MiOrange.copy(alpha = 0.15f)
                            DriveProtocol.SMB -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            DriveProtocol.FTP -> Color(0xFF52C41A).copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (drive.protocol) {
                        DriveProtocol.WEBDAV -> Icons.Default.CloudQueue
                        DriveProtocol.SMB -> Icons.Default.Computer
                        DriveProtocol.FTP -> Icons.Default.FolderShared
                    },
                    contentDescription = null,
                    tint = when (drive.protocol) {
                        DriveProtocol.WEBDAV -> MiOrange
                        DriveProtocol.SMB -> MaterialTheme.colorScheme.primary
                        DriveProtocol.FTP -> Color(0xFF52C41A)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = drive.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = drive.displaySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onTest) {
                Icon(Icons.Default.NetworkCheck, contentDescription = "Test Connection", tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun RemoteFileRow(
    item: RemoteFileItem,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (item.isDirectory) MiOrange else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (!item.isDirectory) {
                Text(text = item.formattedSize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!item.isDirectory) {
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNetworkDriveDialog(
    isTesting: Boolean,
    onDismiss: () -> Unit,
    onSave: (NetworkDrive) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(DriveProtocol.WEBDAV) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Network / Cloud Drive") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Drive Name (e.g. My PC Share)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DriveProtocol.values().forEach { proto ->
                        FilterChip(
                            selected = protocol == proto,
                            onClick = {
                                protocol = proto
                                port = when (proto) {
                                    DriveProtocol.WEBDAV -> "443"
                                    DriveProtocol.SMB -> "445"
                                    DriveProtocol.FTP -> "21"
                                }
                            },
                            label = { Text(proto.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Server Host / IP") },
                        singleLine = true,
                        modifier = Modifier.weight(2.5f)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Remote Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && host.isNotEmpty()) {
                        val drive = NetworkDrive(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            protocol = protocol,
                            serverHost = host,
                            port = port.toIntOrNull() ?: 443,
                            username = username,
                            password = password,
                            remotePath = if (path.isEmpty()) "/" else path,
                            lastConnected = 0L
                        )
                        onSave(drive)
                    }
                },
                enabled = name.isNotEmpty() && host.isNotEmpty()
            ) {
                Text("Save & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
