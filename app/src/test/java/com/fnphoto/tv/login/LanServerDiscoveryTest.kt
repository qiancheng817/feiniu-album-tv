package com.fnphoto.tv.login

import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun discoveryPorts_scanOnlyDefaultAccountLoginPort() {
        assertEquals(
            listOf(5666),
            LanServerDiscovery.discoveryPortsForTest().toList()
        )
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
            assertEquals("历史服务器", found.single().name)
            assertFalse(found.single().discovered)
        }
    }
}
