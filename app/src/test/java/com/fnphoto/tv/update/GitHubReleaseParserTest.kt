package com.fnphoto.tv.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubReleaseParserTest {
    @Test
    fun parseLatestRelease_acceptsReleaseManifestAssetFormat() {
        val release = GitHubReleaseParser.parseLatestRelease(
            """
            {
              "version": "v1.3.0",
              "title": "飞牛相册 TV v1.3.0",
              "page_url": "https://github.com/qiancheng817/feiniu-album-tv/releases/tag/v1.3.0",
              "notes": "Release manifest",
              "assets": [
                {
                  "name": "feiniu-album-tv-v1.3.0-universal.apk",
                  "download_url": "https://github.com/qiancheng817/feiniu-album-tv/releases/download/v1.3.0/feiniu-album-tv-v1.3.0-universal.apk",
                  "size": 20971520
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("v1.3.0", release.version)
        assertEquals("飞牛相册 TV v1.3.0", release.title)
        assertEquals("https://github.com/qiancheng817/feiniu-album-tv/releases/tag/v1.3.0", release.pageUrl)
        assertEquals("Release manifest", release.notes)
        assertEquals("feiniu-album-tv-v1.3.0-universal.apk", release.apkAsset?.name)
        assertEquals(
            "https://github.com/qiancheng817/feiniu-album-tv/releases/download/v1.3.0/feiniu-album-tv-v1.3.0-universal.apk",
            release.apkAsset?.downloadUrl,
        )
        assertEquals(20971520L, release.apkAsset?.sizeBytes)
    }

    @Test
    fun parseLatestRelease_prefersUniversalApkAsset() {
        val release = GitHubReleaseParser.parseLatestRelease(
            """
            {
              "tag_name": "v1.2.3",
              "name": "飞牛相册 TV v1.2.3",
              "html_url": "https://github.com/qiancheng817/feiniu-album-tv/releases/tag/v1.2.3",
              "body": "Release notes",
              "assets": [
                {
                  "name": "feiniu-album-tv-v1.2.3-armeabi-v7a.apk",
                  "browser_download_url": "https://example.test/arm.apk",
                  "size": 10
                },
                {
                  "name": "feiniu-album-tv-v1.2.3-universal.apk",
                  "browser_download_url": "https://example.test/universal.apk",
                  "size": 20
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("v1.2.3", release.version)
        assertEquals("飞牛相册 TV v1.2.3", release.title)
        assertEquals("https://github.com/qiancheng817/feiniu-album-tv/releases/tag/v1.2.3", release.pageUrl)
        assertEquals("feiniu-album-tv-v1.2.3-universal.apk", release.apkAsset?.name)
        assertEquals("https://example.test/universal.apk", release.apkAsset?.downloadUrl)
        assertEquals(20L, release.apkAsset?.sizeBytes)
    }

    @Test
    fun parseLatestRelease_returnsFirstApkWhenUniversalIsMissing() {
        val release = GitHubReleaseParser.parseLatestRelease(
            """
            {
              "tag_name": "v1.2.4",
              "assets": [
                {
                  "name": "SHA256SUMS.txt",
                  "browser_download_url": "https://example.test/SHA256SUMS.txt",
                  "size": 100
                },
                {
                  "name": "feiniu-album-tv-v1.2.4-armeabi-v7a.apk",
                  "browser_download_url": "https://example.test/arm.apk",
                  "size": 10
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("feiniu-album-tv-v1.2.4-armeabi-v7a.apk", release.apkAsset?.name)
    }

    @Test
    fun parseLatestRelease_handlesReleaseWithoutApk() {
        val release = GitHubReleaseParser.parseLatestRelease(
            """
            {
              "tag_name": "v1.2.5",
              "assets": [
                {
                  "name": "SHA256SUMS.txt",
                  "browser_download_url": "https://example.test/SHA256SUMS.txt",
                  "size": 100
                }
              ]
            }
            """.trimIndent(),
        )

        assertNull(release.apkAsset)
    }
}
