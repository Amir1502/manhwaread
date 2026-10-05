package com.manhwaread.core.network

import okhttp3.Dns
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

class VpnSafeDnsTest {
    private val ipv4Address = InetAddress.getByName("188.114.96.1")
    private val ipv6Address = InetAddress.getByName("2606:4700::6810:db")
    private val loopbackAddress = InetAddress.getByName("127.0.0.1")

    private val emptyDoh = object : DohResolver {
        override fun resolve(hostname: String): List<InetAddress> = emptyList()
    }

    @Test
    fun `prioritizeIpv4 orders IPv4 before IPv6`() {
        val dummySystemDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = emptyList()
        }
        val dns = VpnSafeDns(systemDns = dummySystemDns, dohFallback = emptyDoh)
        val sorted = dns.prioritizeIpv4(listOf(ipv6Address, ipv4Address))
        assertTrue(sorted[0] is Inet4Address)
        assertTrue(sorted[1] is Inet6Address)
    }

    @Test
    fun `system dns success returns sorted addresses`() {
        val mockSystem = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(ipv6Address, ipv4Address)
        }
        val dns = VpnSafeDns(systemDns = mockSystem, dohFallback = emptyDoh)
        val result = dns.lookup("manga18fx.com")
        assertEquals(2, result.size)
        assertEquals(ipv4Address, result[0])
        assertEquals(ipv6Address, result[1])
    }

    @Test
    fun `system dns failure falls back to DoH`() {
        val mockSystem = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                throw UnknownHostException("Network unreachable")
        }
        val mockDoh = object : DohResolver {
            override fun resolve(hostname: String): List<InetAddress> = listOf(ipv4Address)
        }
        val dns = VpnSafeDns(systemDns = mockSystem, dohFallback = mockDoh)
        val result = dns.lookup("manga18fx.com")
        assertEquals(listOf(ipv4Address), result)
    }

    @Test
    fun `system dns returning only loopback falls back to DoH`() {
        val mockSystem = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(loopbackAddress)
        }
        val mockDoh = object : DohResolver {
            override fun resolve(hostname: String): List<InetAddress> = listOf(ipv4Address)
        }
        val dns = VpnSafeDns(systemDns = mockSystem, dohFallback = mockDoh)
        val result = dns.lookup("manga18fx.com")
        assertEquals(listOf(ipv4Address), result)
    }

    @Test
    fun `both system and doh failing throws UnknownHostException`() {
        val mockSystem = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                throw UnknownHostException("fail")
        }
        val dns = VpnSafeDns(systemDns = mockSystem, dohFallback = emptyDoh)
        assertThrows(UnknownHostException::class.java) {
            dns.lookup("unknown-domain.invalid")
        }
    }

    @Test
    fun `localhost and IP literals delegate directly to system DNS`() {
        val mockSystem = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(loopbackAddress)
        }
        val mockDoh = object : DohResolver {
            override fun resolve(hostname: String): List<InetAddress> =
                throw AssertionError("DoH should not be called for localhost")
        }
        val dns = VpnSafeDns(systemDns = mockSystem, dohFallback = mockDoh)
        val result = dns.lookup("localhost")
        assertEquals(listOf(loopbackAddress), result)
    }

    @Test
    fun `parseDnsJsonAnswers parses Cloudflare and Google JSON format`() {
        val resolver = DefaultDohResolver()
        val json = """
            {
              "Status": 0,
              "Answer": [
                {"name": "manga18fx.com", "type": 1, "TTL": 300, "data": "188.114.96.1"},
                {"name": "manga18fx.com", "type": 1, "TTL": 300, "data": "188.114.97.1"}
              ]
            }
        """.trimIndent()
        val parsed = resolver.parseDnsJsonAnswers(json)
        assertEquals(2, parsed.size)
        assertEquals("188.114.96.1", parsed[0].hostAddress)
        assertEquals("188.114.97.1", parsed[1].hostAddress)
    }
}
