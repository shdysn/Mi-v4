package com.mi.explorer.data.model

import android.graphics.drawable.Drawable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ApkFileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val size: Long = file.length(),
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long = 0L,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val isInstalled: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: Long = 0L,
    val isBackup: Boolean = false,
    val splitCount: Int = 0,
    val icon: Drawable? = null,
    val lastModified: Long = file.lastModified()
) {
    val formattedSize: String get() = FileItem.formatBytes(size)
    val formattedDate: String get() {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(lastModified))
    }

    val isDowngradeCandidate: Boolean
        get() = isInstalled && installedVersionCode > 0 && versionCode > 0 && versionCode < installedVersionCode

    val isUpgradeCandidate: Boolean
        get() = isInstalled && installedVersionCode > 0 && versionCode > installedVersionCode

    val isCurrentVersion: Boolean
        get() = isInstalled && installedVersionCode > 0 && versionCode == installedVersionCode
}

data class AppBackupGroup(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isInstalled: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: Long = 0L,
    val backups: List<ApkFileItem> = emptyList()
) {
    val totalBackupSize: Long get() = backups.sumOf { it.size }
    val formattedTotalSize: String get() = FileItem.formatBytes(totalBackupSize)
    val hasDowngradeOption: Boolean get() = backups.any { it.isDowngradeCandidate }
    val latestBackup: ApkFileItem? get() = backups.maxByOrNull { it.versionCode.takeIf { vc -> vc > 0 } ?: it.lastModified }
    val backupCount: Int get() = backups.size
}

enum class ApkTab {
    INSTALLED_APPS,   // 1-Tap App Extractor & Cloner
    DOWNGRADE_HUB,    // Backup & Downgrade Archive Hub
    APK_FILES         // Storage APKs
}

