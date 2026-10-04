package com.mi.explorer.data.model

enum class DriveProtocol {
    WEBDAV,
    SMB,
    FTP
}

data class NetworkDrive(
    val id: String,
    val name: String,
    val protocol: DriveProtocol,
    val serverHost: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val remotePath: String = "/",
    val lastConnected: Long = 0L
) {
    val displaySubtitle: String
        get() = "${protocol.name} • $serverHost:$port$remotePath"
}

data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
) {
    val formattedSize: String get() = FileItem.formatBytes(size)
}
