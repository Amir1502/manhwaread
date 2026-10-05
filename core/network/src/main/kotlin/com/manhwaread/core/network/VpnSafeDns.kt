package com.manhwaread.core.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Резолвер DoH для резервного разрешения имён при сбоях системного DNS.
 */
interface DohResolver {
    fun resolve(hostname: String): List<InetAddress>
}

/**
 * Резервный DoH-резолвер через Cloudflare (1.1.1.1) и Google (8.8.8.8) по прямым IP-адресам.
 * Запросы идут поверх HTTPS (порт 443), минуя повреждённые или заблокированные DNS-серверы VPN/провайдера.
 */
class DefaultDohResolver(
    private val client: OkHttpClient = createDohBootstrapClient(),
) : DohResolver {
    override fun resolve(hostname: String): List<InetAddress> {
        val cloudflareResult = resolveViaCloudflare(hostname)
        if (cloudflareResult.isNotEmpty()) return cloudflareResult
        return resolveViaGoogle(hostname)
    }

    private fun resolveViaCloudflare(hostname: String): List<InetAddress> =
        queryDoH("https://1.1.1.1/dns-query?name=$hostname&type=A", addAcceptJson = true)

    private fun resolveViaGoogle(hostname: String): List<InetAddress> =
        queryDoH("https://8.8.8.8/resolve?name=$hostname&type=A", addAcceptJson = false)

    private fun queryDoH(url: String, addAcceptJson: Boolean): List<InetAddress> {
        return try {
            val builder = Request.Builder().url(url)
            if (addAcceptJson) {
                builder.header("Accept", "application/dns-json")
            }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                parseDnsJsonAnswers(body)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseDnsJsonAnswers(json: String): List<InetAddress> {
        return DATA_IP_REGEX.findAll(json).mapNotNull { match ->
            val ip = match.groupValues[1]
            try {
                InetAddress.getByName(ip)
            } catch (_: Exception) {
                null
            }
        }.toList()
    }

    companion object {
        private val DATA_IP_REGEX = Regex(""""data"\s*:\s*"([0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3})"""")
        private const val BOOTSTRAP_TIMEOUT_MS = 4_000L

        fun createDohBootstrapClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(BOOTSTRAP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(BOOTSTRAP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
    }
}

/**
 * DNS-резолвер с защитой от проблем VPN-соединений и мобильных сетей на Android:
 * 1. Приоритезирует IPv4-адреса ([Inet4Address]) перед IPv6. На Android большинство
 *    VPN-туннелей (WireGuard, V2Ray, OpenVPN) маршрутизируют только IPv4 (0.0.0.0/0).
 *    Попытка подключения к IPv6 приводит к SocketException ("Network is unreachable")
 *    или 15-секундному таймауту соединения.
 * 2. Фильтрует адреса-заглушки ТСПУ/провайдеров (127.0.0.1, 0.0.0.0) для внешних доменов.
 * 3. При [UnknownHostException] или отсутствии валидных адресов задействует [dohFallback].
 */
class VpnSafeDns(
    private val systemDns: Dns = Dns.SYSTEM,
    private val dohFallback: DohResolver = DefaultDohResolver(),
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.isBlank()) throw UnknownHostException("Hostname cannot be blank")

        if (isLocalOrIp(hostname)) {
            return systemDns.lookup(hostname)
        }

        val systemAddresses = try {
            systemDns.lookup(hostname)
        } catch (_: UnknownHostException) {
            emptyList()
        }

        val validSystem = systemAddresses.filterNot { it.isLoopbackAddress || it.isAnyLocalAddress }
        if (validSystem.isNotEmpty()) {
            return prioritizeIpv4(validSystem)
        }

        val fallbackAddresses = dohFallback.resolve(hostname)
        if (fallbackAddresses.isNotEmpty()) {
            return prioritizeIpv4(fallbackAddresses)
        }

        throw UnknownHostException("Unable to resolve host \"$hostname\": system and DoH DNS failed")
    }

    internal fun prioritizeIpv4(addresses: List<InetAddress>): List<InetAddress> =
        addresses.sortedWith { a, b ->
            when {
                a is Inet4Address && b !is Inet4Address -> -1
                a !is Inet4Address && b is Inet4Address -> 1
                else -> 0
            }
        }

    private fun isLocalOrIp(hostname: String): Boolean =
        hostname.equals("localhost", ignoreCase = true) ||
            IPV4_PATTERN.matches(hostname) ||
            hostname.contains(':')

    private companion object {
        val IPV4_PATTERN = Regex("""^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$""")
    }
}
