package com.fnphoto.tv.update

import com.fnphoto.tv.BuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateBuildConfigTest {
    @Test
    fun updateRepoDefaultsToFeiNiuAlbumReleaseRepo() {
        assertEquals("qiancheng817/feiniu-album-tv", BuildConfig.FN_UPDATE_REPO)
        assertEquals(
            "https://github.com/qiancheng817/feiniu-album-tv/releases/latest/download/feiniu-album-tv-update.json",
            BuildConfig.FN_UPDATE_MANIFEST_URL,
        )
    }
}
