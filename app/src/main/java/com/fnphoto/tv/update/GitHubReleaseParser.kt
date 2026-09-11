package com.fnphoto.tv.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser

object GitHubReleaseParser {
    fun parseLatestRelease(json: String): AppReleaseInfo {
        val root = JsonParser.parseString(json).asJsonObject
        val assets = root.getAsJsonArray("assets")
            ?.mapNotNull { element ->
                if (element.isJsonObject) {
                    element.asJsonObject.toReleaseAsset()
                } else {
                    null
                }
            }
            .orEmpty()
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val preferredAsset = apkAssets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: apkAssets.firstOrNull()
        val version = root.stringOrBlank("version").ifBlank { root.stringOrBlank("tag_name") }

        return AppReleaseInfo(
            version = version,
            title = root.stringOrBlank("title").ifBlank { root.stringOrBlank("name").ifBlank { version } },
            pageUrl = root.stringOrBlank("page_url").ifBlank { root.stringOrBlank("html_url") },
            notes = root.stringOrBlank("notes").ifBlank { root.stringOrBlank("body") },
            apkAsset = preferredAsset,
        )
    }

    private fun JsonObject.toReleaseAsset(): AppReleaseAsset? {
        val name = stringOrBlank("name")
        val downloadUrl = stringOrBlank("download_url").ifBlank { stringOrBlank("browser_download_url") }
        if (name.isBlank() || downloadUrl.isBlank()) {
            return null
        }

        return AppReleaseAsset(
            name = name,
            downloadUrl = downloadUrl,
            sizeBytes = get("size")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
        )
    }

    private fun JsonObject.stringOrBlank(name: String): String {
        val value = get(name) ?: return ""
        if (!value.isJsonPrimitive || value.isJsonNull) {
            return ""
        }
        return value.asString.orEmpty()
    }
}
