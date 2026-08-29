package com.aibox.backgroundserver.platform.network

import com.aibox.backgroundserver.platform.root.RootCommandGateway

class RootRoutingController(
    private val root: RootCommandGateway,
) {
    fun enableIpv4Forwarding() = root.execute("sysctl -w net.ipv4.ip_forward=1")

    fun findTunnelInterface(addressPrefix: String = "10.77.0.1"): String? {
        val result = root.execute("ip -o -4 addr show | awk '\$4 ~ /^${addressPrefix.replace(".", "\\.")}/ {print \$2; exit}'")
        return result.stdout.trim().takeIf { result.ok && it.isNotBlank() }
    }

    fun applyNat(tunnelInterface: String, egressInterface: String, subnet: String = "10.77.0.0/24") =
        root.execute(
            """
            iptables -C FORWARD -i $tunnelInterface -o $egressInterface -j ACCEPT 2>/dev/null || iptables -A FORWARD -i $tunnelInterface -o $egressInterface -j ACCEPT
            iptables -C FORWARD -i $egressInterface -o $tunnelInterface -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || iptables -A FORWARD -i $egressInterface -o $tunnelInterface -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
            iptables -t nat -C POSTROUTING -s $subnet -o $egressInterface -j MASQUERADE 2>/dev/null || iptables -t nat -A POSTROUTING -s $subnet -o $egressInterface -j MASQUERADE
            """.trimIndent(),
        )

    fun removeNat(tunnelInterface: String, egressInterface: String, subnet: String = "10.77.0.0/24") =
        root.execute(
            """
            iptables -D FORWARD -i $tunnelInterface -o $egressInterface -j ACCEPT 2>/dev/null || true
            iptables -D FORWARD -i $egressInterface -o $tunnelInterface -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || true
            iptables -t nat -D POSTROUTING -s $subnet -o $egressInterface -j MASQUERADE 2>/dev/null || true
            """.trimIndent(),
        )
}
