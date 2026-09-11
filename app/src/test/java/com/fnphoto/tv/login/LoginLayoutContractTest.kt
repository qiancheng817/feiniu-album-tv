package com.fnphoto.tv.login

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoginLayoutContractTest {
    @Test
    fun manualAddServerPanel_isCenteredRootOverlay() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(loginLayoutFile())

        val panel = findByAndroidId(document.documentElement, "@+id/manual_add_panel")
        assertNotNull(panel)
        val parent = panel.parentNode as Element

        assertEquals("@+id/login_root", parent.androidAttr("id"))
        assertTrue(panel.androidAttr("layout_gravity").contains("center"))
    }

    @Test
    fun accountLoginEntry_isVisibleAndQrEntryHiddenByDefault() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(loginLayoutFile())

        val qrTab = findByAndroidId(document.documentElement, "@+id/tab_qr_login")
        val qrBody = findByAndroidId(document.documentElement, "@+id/qr_login_body")
        val accountBody = findByAndroidId(document.documentElement, "@+id/account_login_body")

        assertNotNull(qrTab)
        assertNotNull(qrBody)
        assertNotNull(accountBody)
        assertEquals("gone", qrTab.androidAttr("visibility"))
        assertEquals("gone", qrBody.androidAttr("visibility"))
        assertEquals("", accountBody.androidAttr("visibility"))
    }

    @Test
    fun accountLogin_requiresDisclaimerAgreementAndShowsScrollableDisclaimerDialog() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(loginLayoutFile())

        val accountBody = findByAndroidId(document.documentElement, "@+id/account_login_body")
        val agreementCheck = findByAndroidId(document.documentElement, "@+id/cb_disclaimer_agree")
        val agreementText = findByAndroidId(document.documentElement, "@+id/tv_disclaimer_agreement")
        val dialog = findByAndroidId(document.documentElement, "@+id/disclaimer_dialog")
        val scroll = findByAndroidId(document.documentElement, "@+id/disclaimer_scroll")
        val content = findByAndroidId(document.documentElement, "@+id/tv_disclaimer_content")
        val close = findByAndroidId(document.documentElement, "@+id/btn_close_disclaimer")

        assertNotNull(accountBody)
        assertNotNull(agreementCheck)
        assertNotNull(agreementText)
        assertNotNull(dialog)
        assertNotNull(scroll)
        assertNotNull(content)
        assertNotNull(close)

        assertEquals("@+id/account_login_body", (agreementCheck.parentNode.parentNode as Element).androidAttr("id"))
        assertEquals("", agreementCheck.androidAttr("checked"))
        assertEquals("true", agreementText.androidAttr("focusable"))
        assertEquals("true", agreementText.androidAttr("clickable"))
        assertEquals("我已阅读并同意《免责声明》", resolveText(agreementText.androidAttr("text")))

        assertEquals("@+id/login_root", (dialog.parentNode as Element).androidAttr("id"))
        assertEquals("gone", dialog.androidAttr("visibility"))
        assertTrue(scroll.androidAttr("layout_height").isNotBlank())
        assertEquals("关闭", resolveText(close.androidAttr("text")))

        val disclaimer = resolveText(content.androidAttr("text"))
        listOf("用户隐私", "公网", "数据安全", "NAS 数据损坏", "飞牛", "商标", "永久维护", "API 更新", "失效")
            .forEach { keyword -> assertTrue(disclaimer.contains(keyword), "Missing keyword: $keyword") }
        assertFalse(disclaimer.contains("\u5f00\u6e90"))
    }

    @Test
    fun accountLogin_hasHiddenTotpControls() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(loginLayoutFile())

        val accountBody = findByAndroidId(document.documentElement, "@+id/account_login_body")
        val hint = findByAndroidId(document.documentElement, "@+id/tv_totp_hint")
        val code = findByAndroidId(document.documentElement, "@+id/edit_totp_code")
        val trust = findByAndroidId(document.documentElement, "@+id/cb_trust_device")
        val accountOptions = findByAndroidId(document.documentElement, "@+id/account_options_row")

        assertNotNull(accountBody)
        assertNotNull(hint)
        assertNotNull(code)
        assertNotNull(trust)
        assertNotNull(accountOptions)

        assertEquals("@+id/account_login_body", (hint.parentNode as Element).androidAttr("id"))
        assertEquals("@+id/account_login_body", (code.parentNode as Element).androidAttr("id"))
        assertEquals("@+id/account_login_body", (trust.parentNode as Element).androidAttr("id"))
        assertEquals("@+id/account_login_body", (accountOptions.parentNode as Element).androidAttr("id"))
        assertEquals("gone", hint.androidAttr("visibility"))
        assertEquals("gone", code.androidAttr("visibility"))
        assertEquals("gone", trust.androidAttr("visibility"))
        assertEquals("动态验证码", code.androidAttr("hint"))
        assertEquals("number", code.androidAttr("inputType"))
        assertEquals("6", code.androidAttr("maxLength"))
        assertEquals("信任此设备", trust.androidAttr("text"))
    }

    @Test
    fun loginButtons_useReadableStatefulTextColor() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(loginLayoutFile())
        val buttons = collectElements(document.documentElement)
            .filter { it.tagName == "Button" && it.androidAttr("background") == "@drawable/bg_login_button" }

        assertTrue(buttons.isNotEmpty())
        buttons.forEach { button ->
            assertEquals("@color/login_button_text", button.androidAttr("textColor"), button.androidAttr("id"))
        }

        val colorDocument = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(colorFile("login_button_text.xml"))
        val items = colorDocument.documentElement.getElementsByTagName("item")
        assertEquals("#111318", (items.item(0) as Element).androidAttr("color"))
        assertEquals("true", (items.item(0) as Element).androidAttr("state_focused"))
        assertEquals("#FFFFFFFF", (items.item(1) as Element).androidAttr("color"))
        assertEquals("", (items.item(1) as Element).androidAttr("state_focused"))
    }

    private fun loginLayoutFile(): File {
        val candidates = listOf(
            File("app/src/main/res/layout/activity_login.xml"),
            File("src/main/res/layout/activity_login.xml")
        )
        return candidates.first { it.exists() }
    }

    private fun colorFile(name: String): File {
        val candidates = listOf(
            File("app/src/main/res/color/$name"),
            File("src/main/res/color/$name")
        )
        return candidates.first { it.exists() }
    }

    private fun findByAndroidId(element: Element, id: String): Element? {
        if (element.androidAttr("id") == id) return element
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val found = findByAndroidId(child, id)
                if (found != null) return found
            }
        }
        return null
    }

    private fun collectElements(element: Element): List<Element> {
        val result = mutableListOf(element)
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                result += collectElements(child)
            }
        }
        return result
    }

    private fun Element.androidAttr(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)

    private fun resolveText(value: String): String {
        if (!value.startsWith("@string/")) return value
        val name = value.removePrefix("@string/")
        val stringsFile = listOf(
            File("app/src/main/res/values/strings.xml"),
            File("src/main/res/values/strings.xml")
        ).firstOrNull { it.exists() } ?: return value
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(stringsFile)
        val nodes = document.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.getAttribute("name") == name) {
                return node.textContent.trim()
            }
        }
        return value
    }
}
