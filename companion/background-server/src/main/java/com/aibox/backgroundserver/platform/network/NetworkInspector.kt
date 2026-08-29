package com.aibox.backgroundserver.platform.network

import android.content.Context
import android.net.ConnectivityManager
import com.aibox.backgroundserver.domain.NetworkAddress
import com.aibox.backgroundserver.domain.NetworkSnapshot
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkInspector {
    fun snapshot(context: Context): NetworkSnapshot {
        val interfaces = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
        }.getOrDefault(emptyList())

        val addresses = interfaces
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses)
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .mapNotNull { address ->
                        when (address) {
                            is Inet4Address -> NetworkAddress(networkInterface.name, address.hostAddress.orEmpty(), false)
                            is Inet6Address -> NetworkAddress(networkInterface.name, address.hostAddress.orEmpty().substringBefore('%'), true)
                            else -> null
                        }
                    }
            }
            .sortedWith(compareBy<NetworkAddress> { it.ipv6 }.thenBy { it.interfaceName })

        val preferredIpv4 = addresses.firstOrNull {
            !it.ipv6 && (it.interfaceName.startsWith("wlan") || it.interfaceName.startsWith("eth"))
        }?.address ?: addresses.firstOrNull { !it.ipv6 }?.address

        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val linkProperties = connectivity.activeNetwork?.let(connectivity::getLinkProperties)
        val primaryInterface = linkProperties?.interfaceName
            ?: addresses.firstOrNull { it.address == preferredIpv4 }?.interfaceName
        val ipv4LinkAddress = linkProperties?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val gateway = linkProperties?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway
            ?.hostAddress
        val dns = linkProperties?.dnsServers.orEmpty().mapNotNull { it.hostAddress }

        return NetworkSnapshot(
            addresses = addresses,
            wifiLikeIpv4 = preferredIpv4,
            primaryInterface = primaryInterface,
            primaryCidr = ipv4LinkAddress?.let { "${it.address.hostAddress}/${it.prefixLength}" },
            gateway = gateway,
            dnsServers = dns,
        )
    }
}
