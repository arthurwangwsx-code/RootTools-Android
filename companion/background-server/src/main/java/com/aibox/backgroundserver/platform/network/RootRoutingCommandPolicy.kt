package com.aibox.backgroundserver.platform.network

data class NatRuleCommands(
    val apply: String,
    val remove: String,
)

object RootRoutingCommandPolicy {
    const val ENABLE_IPV4_FORWARDING = "sysctl -w net.ipv4.ip_forward=1"

    fun findTunnelInterface(address: String): String? {
        if (!IPV4.matches(address)) return null
        val escaped = address.replace(".", "\\.")
        return "ip -o -4 addr show | awk '\$4 ~ /^$escaped\\// {print \$2; exit}'"
    }

    fun natRules(tunnelInterface: String, egressInterface: String, subnet: String): NatRuleCommands? {
        if (!INTERFACE.matches(tunnelInterface) || !INTERFACE.matches(egressInterface) || !CIDR.matches(subnet)) {
            return null
        }
        val apply = """
            iptables -C FORWARD -i $tunnelInterface -o $egressInterface -j ACCEPT 2>/dev/null || iptables -A FORWARD -i $tunnelInterface -o $egressInterface -j ACCEPT
            iptables -C FORWARD -i $egressInterface -o $tunnelInterface -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || iptables -A FORWARD -i $egressInterface -o $tunnelInterface -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
            iptables -t nat -C POSTROUTING -s $subnet -o $egressInterface -j MASQUERADE 2>/dev/null || iptables -t nat -A POSTROUTING -s $subnet -o $egressInterface -j MASQUERADE
        """.trimIndent()
        val remove = """
            iptables -D FORWARD -i $tunnelInterface -o $egressInterface -j ACCEPT 2>/dev/null || true
            iptables -D FORWARD -i $egressInterface -o $tunnelInterface -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || true
            iptables -t nat -D POSTROUTING -s $subnet -o $egressInterface -j MASQUERADE 2>/dev/null || true
        """.trimIndent()
        return NatRuleCommands(apply, remove)
    }

    fun validInterface(value: String): String? = value.takeIf(INTERFACE::matches)

    private val INTERFACE = Regex("[A-Za-z0-9_.:@-]{1,32}")
    private val IPV4 = Regex("(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}")
    private val CIDR = Regex("(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}/(?:[0-9]|[12][0-9]|3[0-2])")
}
