package com.fnphoto.tv

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandingContractTest {
    @Test
    fun manifestAndLoginUseFeiNiuAlbumName() {
        val manifest = parseXml(projectFile("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml"))
        val application = manifest.documentElement
            .getElementsByTagName("application")
            .item(0) as Element
        assertEquals("飞牛相册", application.androidAttr("label"))

        val loginXml = projectFile(
            "src/main/res/layout/activity_login.xml",
            "app/src/main/res/layout/activity_login.xml"
        ).readText()
        assertTrue(loginXml.contains("飞牛相册"))
        assertTrue(!loginXml.contains("fnPhoto TV"))
    }

    private fun projectFile(vararg paths: String): File =
        paths.map(::File).first { it.exists() }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private fun Element.androidAttr(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)
}
