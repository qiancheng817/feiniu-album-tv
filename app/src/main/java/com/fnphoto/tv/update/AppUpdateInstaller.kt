package com.fnphoto.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdateInstaller(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
) {
    private val appContext = context.applicationContext

    fun canRequestPackageInstalls(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    suspend fun downloadApk(asset: AppReleaseAsset): File = withContext(Dispatchers.IO) {
        val updateDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val target = File(updateDir, asset.name)
        val request = Request.Builder().url(asset.downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载 APK 失败: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("下载 APK 失败: 响应为空")
            target.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
        target
    }

    fun launchInstall(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.updateprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        appContext.startActivity(intent)
    }

    companion object {
        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
