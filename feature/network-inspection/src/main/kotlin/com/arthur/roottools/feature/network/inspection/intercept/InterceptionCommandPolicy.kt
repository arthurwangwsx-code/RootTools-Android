package com.arthur.roottools.feature.network.inspection.intercept

data class InterceptionRuleCommands(
    val cleanup: String,
    val install: String,
)

object InterceptionCommandPolicy {
    fun rules(uid: Int, proxyPort: Int, blockQuic: Boolean): InterceptionRuleCommands? {
        if (uid < FIRST_APPLICATION_UID || proxyPort !in 1024..65535) return null
        return InterceptionRuleCommands(
            cleanup = cleanupRules(),
            install = buildString {
                appendLine("iptables -t nat -N $NAT_CHAIN 2>/dev/null || true")
                appendLine("iptables -t nat -F $NAT_CHAIN")
                appendLine("iptables -t nat -C OUTPUT -j $NAT_CHAIN 2>/dev/null || iptables -t nat -I OUTPUT 1 -j $NAT_CHAIN")
                appendLine("iptables -t nat -A $NAT_CHAIN -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $proxyPort")
                if (blockQuic) {
                    appendLine("iptables -N $QUIC_CHAIN 2>/dev/null || true")
                    appendLine("iptables -F $QUIC_CHAIN")
                    appendLine("iptables -C OUTPUT -j $QUIC_CHAIN 2>/dev/null || iptables -I OUTPUT 1 -j $QUIC_CHAIN")
                    appendLine("iptables -A $QUIC_CHAIN -p udp --dport 443 -m owner --uid-owner $uid -j REJECT")
                }
                appendLine("if command -v ip6tables >/dev/null 2>&1; then")
                appendLine("  ip6tables -t nat -N $NAT_CHAIN 2>/dev/null || true")
                appendLine("  ip6tables -t nat -F $NAT_CHAIN 2>/dev/null || true")
                appendLine("  ip6tables -t nat -C OUTPUT -j $NAT_CHAIN 2>/dev/null || ip6tables -t nat -I OUTPUT 1 -j $NAT_CHAIN 2>/dev/null || true")
                appendLine("  ip6tables -t nat -A $NAT_CHAIN -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $proxyPort 2>/dev/null || true")
                if (blockQuic) {
                    appendLine("  ip6tables -N $QUIC_CHAIN 2>/dev/null || true")
                    appendLine("  ip6tables -F $QUIC_CHAIN 2>/dev/null || true")
                    appendLine("  ip6tables -C OUTPUT -j $QUIC_CHAIN 2>/dev/null || ip6tables -I OUTPUT 1 -j $QUIC_CHAIN")
                    appendLine("  ip6tables -A $QUIC_CHAIN -p udp --dport 443 -m owner --uid-owner $uid -j REJECT")
                }
                append("fi")
            },
        )
    }

    fun cleanupRules(): String = buildString {
        cleanupChain("iptables", NAT_CHAIN, nat = true)
        cleanupChain("iptables", QUIC_CHAIN, nat = false)
        cleanupChain("iptables", LEGACY_NAT_CHAIN, nat = true)
        cleanupChain("iptables", LEGACY_QUIC_CHAIN, nat = false)
        appendLine("if command -v ip6tables >/dev/null 2>&1; then")
        cleanupChain("ip6tables", NAT_CHAIN, nat = true, indent = "  ")
        cleanupChain("ip6tables", QUIC_CHAIN, nat = false, indent = "  ")
        cleanupChain("ip6tables", LEGACY_NAT_CHAIN, nat = true, indent = "  ")
        cleanupChain("ip6tables", LEGACY_QUIC_CHAIN, nat = false, indent = "  ")
        append("fi")
    }

    fun forceStop(packageName: String): String? {
        if (!PACKAGE_NAME.matches(packageName)) return null
        return "am force-stop ${shellQuote(packageName)}"
    }

    private fun StringBuilder.cleanupChain(
        binary: String,
        chain: String,
        nat: Boolean,
        indent: String = "",
    ) {
        val table = if (nat) "-t nat " else ""
        appendLine("$indent$binary $table-D OUTPUT -j $chain 2>/dev/null || true")
        appendLine("$indent$binary $table-F $chain 2>/dev/null || true")
        appendLine("$indent$binary $table-X $chain 2>/dev/null || true")
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private const val FIRST_APPLICATION_UID = 10_000
    private const val NAT_CHAIN = "ROOTTOOLS_MITM"
    private const val QUIC_CHAIN = "ROOTTOOLS_QUIC"
    private const val LEGACY_NAT_CHAIN = "NETTOOLS_MITM"
    private const val LEGACY_QUIC_CHAIN = "NETTOOLS_QUIC"
    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
}
