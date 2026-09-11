package com.fnphoto.tv.update

data class AppReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

data class AppReleaseInfo(
    val version: String,
    val title: String,
    val pageUrl: String,
    val notes: String,
    val apkAsset: AppReleaseAsset?,
)

sealed class AppUpdateCheckResult {
    data class Skipped(val lastCheckMillis: Long) : AppUpdateCheckResult()
    data class UpToDate(val release: AppReleaseInfo) : AppUpdateCheckResult()
    data class UpdateAvailable(val release: AppReleaseInfo) : AppUpdateCheckResult()
    data class NoInstallableAsset(val release: AppReleaseInfo) : AppUpdateCheckResult()
    data class Error(val message: String, val throwable: Throwable? = null) : AppUpdateCheckResult()
}
