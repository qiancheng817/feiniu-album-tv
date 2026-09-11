package com.fnphoto.tv.login

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanServerDiscoveryTest {

    @Test
    fun classCSubnetHosts_returnsPeerHostsInOrderAndExcludesLocalAddress() {
        val hosts = LanServerDiscovery.classCSubnetHosts("192.168.1.200")

        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertFalse("192.168.1.200" in hosts)
        assertTrue("192.168.1.1" in hosts)
    }

    @Test
    fun normalizeServerInput_addsAccountLoginPortWhenPortIsMissing() {
        assertEquals(
            "http://192.168.1.1:5666",
            LanServerDiscovery.normalizeServerInput("192.168.1.1")
        )
    }

    @Test
    fun normalizeServerInput_keepsExplicitSchemeAndPort() {
        assertEquals(
            "https://nas.example.com:8443",
            LanServerDiscovery.normalizeServerInput("https://nas.example.com:8443/path")
        )
    }

    @Test
    fun discoveryPorts_includesFeiNiuDefaultAndCommonAltPorts() {
        // 飞牛旧版默认 5666；新版默认 8000；5000 兼容旧固件；80 HTTP 标准
        val ports = LanServerDiscovery.discoveryPortsForTest().toList()
        assertEquals(listOf(5666, 8000, 5000, 80), ports)
    }

    @Test
    fun parseFeiNiuSysConfig_recognizesStandardResponse() {
        val body = """{"code":0,"data":{"name":"fnNAS","deviceName":"MyFeiNiu","version":"1.2.3"}}"""
        assertEquals("fnNAS", LanServerDiscovery.parseFeiNiuSysConfig(body))
    }

    @Test
    fun parseFeiNiuSysConfig_fallsBackToDeviceName() {
        val body = """{"code":0,"data":{"deviceName":"卧室NAS","version":"1.0"}}"""
        assertEquals("卧室NAS", LanServerDiscovery.parseFeiNiuSysConfig(body))
    }

    @Test
    fun parseFeiNiuSysConfig_returnsEmptyWhenFeiNiuButNoName() {
        val body = """{"code":0,"data":{"version":"1.0"}}"""
        assertEquals("", LanServerDiscovery.parseFeiNiuSysConfig(body))
    }

    @Test
    fun parseFeiNiuSysConfig_rejectsNonFeiNiuResponse() {
        // 没有 code 字段
        assertNull(LanServerDiscovery.parseFeiNiuSysConfig("""{"hello":"world"}"""))
        // code 不是 0
        assertNull(LanServerDiscovery.parseFeiNiuSysConfig("""{"code":1,"msg":"err"}"""))
        // 没有 data
        assertNull(LanServerDiscovery.parseFeiNiuSysConfig("""{"code":0}"""))
    }

    @Test
    fun extractJsonString_handlesEscapedChars() {
        // JSON 源码中 \" 反转义为 "；\\n 反转义为字面 \n（反斜杠+n，不是换行符）。
        // 输入 body（原始 JSON 文本，三引号不处理转义）：{"name":"a\"b\\nc"}
        val body = """{"name":"a\"b\\nc"}"""
        // 期望值（普通字符串，需要转义）：a"b\nc（6 字符：a 引号 b 反斜杠 n c）
        assertEquals("a\"b\\nc", LanServerDiscovery.extractJsonString(body, "name"))
    }

    @Test
    fun fingerprintProbe_returnsNullForClosedPort() {
        // 找一个空闲端口，确保没服务在监听
        ServerSocket(0).use { s ->
            val baseUrl = "http://127.0.0.1:${s.localPort}"
            assertNull(LanServerDiscovery.fingerprintProbe(baseUrl))
        }
    }

    @Test
    fun fingerprintProbe_returnsServerNameForFeiNiuLikeResponse() {
        // 起一个简易 HTTP 服务，返回飞牛风格响应
        val requestCount = AtomicInteger(0)
        val server = ServerSocket(0)
        val port = server.localPort
        Thread {
            try {
                while (!server.isClosed) {
                    val client = server.accept()
                    requestCount.incrementAndGet()
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val requestLine = reader.readLine() ?: return@Thread
                    // 跳过 headers
                    while (true) {
                        val line = reader.readLine()
                        if (line.isNullOrEmpty()) break
                    }
                    val body = """{"code":0,"data":{"name":"客厅飞牛","version":"1.2.6"}}"""
                    val resp = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n" +
                            "Connection: close\r\n\r\n" + body
                    client.getOutputStream().write(resp.toByteArray(Charsets.UTF_8))
                    client.getOutputStream().flush()
                    client.close()
                    if (requestCount.get() >= 1) break
                }
            } catch (_: Exception) {
                // socket closed
            }
        }.start()

        try {
            val name = LanServerDiscovery.fingerprintProbe("http://127.0.0.1:$port")
            assertNotNull(name)
            assertEquals("客厅飞牛", name)
        } finally {
            server.close()
        }
    }

    @Test
    fun fingerprintProbe_returnsNullForNonFeiNiuHttpResponse() {
        val requestCount = AtomicInteger(0)
        val server = ServerSocket(0)
        val port = server.localPort
        Thread {
            try {
                while (!server.isClosed) {
                    val client = server.accept()
                    requestCount.incrementAndGet()
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    reader.readLine()
                    while (true) {
                        val line = reader.readLine()
                        if (line.isNullOrEmpty()) break
                    }
                    // 普通 HTTP 服务返回首页 HTML（无 code/data 字段）
                    val body = "<html><body>router</body></html>"
                    val resp = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: ${body.length}\r\n" +
                            "Connection: close\r\n\r\n" + body
                    client.getOutputStream().write(resp.toByteArray(Charsets.UTF_8))
                    client.getOutputStream().flush()
                    client.close()
                    if (requestCount.get() >= 1) break
                }
            } catch (_: Exception) {
            }
        }.start()

        try {
            assertNull(LanServerDiscovery.fingerprintProbe("http://127.0.0.1:$port"))
        } finally {
            server.close()
        }
    }

    @Test
    fun discoverSavedBlocking_returnsOnlyReachableSavedServers() {
        val closedPort = ServerSocket(0).use { it.localPort }
        ServerSocket(0).use { server ->
            val openPort = server.localPort

            val found = LanServerDiscovery.discoverSavedBlocking(
                listOf(
                    "127.0.0.1:$closedPort",
                    "127.0.0.1:$openPort",
                    "127.0.0.1:$openPort"
                )
            )

            assertEquals(listOf("http://127.0.0.1:$openPort"), found.map { it.baseUrl })
            // 历史服务器无法通过指纹（端口开但无 HTTP 响应），回退为「历史服务器」标签
            assertEquals("历史服务器", found.single().name)
            assertFalse(found.single().discovered)
        }
    }
}