package com.fnphoto.tv.update

import android.content.Context
import com.fnphoto.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdateChecker(
    context: Context,
    private val manifestUrl: String = BuildConfig.FN_UPDATE_MANIFEST_URL,
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val client: OkHttpClient = defaultClient(),
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun check(force: Boolean): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MILLIS, 0L)
        if (!UpdateCheckPolicy.shouldCheck(now, lastCheck, force)) {
            return@withContext AppUpdateCheckResult.Skipped(lastCheck)
        }

        prefs.edit().putLong(KEY_LAST_CHECK_MILLIS, now).apply()

        runCatching {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "feiniu-album-tv/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Update manifest HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    throw IOException("Update manifest response body is empty")
                }
                GitHubReleaseParser.parseLatestRelease(body)
            }
        }.fold(
            onSuccess = { release ->
                when {
                    release.apkAsset == null -> AppUpdateCheckResult.NoInstallableAsset(release)
                    VersionComparator.isRemoteNewer(release.version, currentVersionName) ->
                        AppUpdateCheckResult.UpdateAvailable(release)
                    else -> AppUpdateCheckResult.UpToDate(release)
                }
            },
            onFailure = { throwable ->
                AppUpdateCheckResult.Error(
                    message = throwable.message ?: "检查更新失败",
                    throwable = throwable,
                )
            },
        )
    }

    companion object {
        private const val PREFS_NAME = "fn_photo_update_prefs"
        private const val KEY_LAST_CHECK_MILLIS = "last_check_millis"

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
