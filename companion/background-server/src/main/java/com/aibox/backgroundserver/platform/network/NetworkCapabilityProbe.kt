package com.aibox.backgroundserver.platform.network

import com.aibox.backgroundserver.domain.NetworkCapabilities
import com.aibox.backgroundserver.platform.root.RootCommandGateway

class NetworkCapabilityProbe(
    private val root: RootCommandGateway,
) {
    fun probe(): NetworkCapabilities {
        val result = root.execute(
            """
            tun=0; [ -e /dev/net/tun ] && tun=1
            ipt=0; command -v iptables >/dev/null 2>&1 && ipt=1
            wgt=0; command -v wg >/dev/null 2>&1 && wgt=1
            wgk=0
            grep -qi wireguard /proc/modules 2>/dev/null && wgk=1
            if [ -r /proc/config.gz ]; then
              zcat /proc/config.gz 2>/dev/null | grep -q '^CONFIG_WIREGUARD=[ym]' && wgk=1
            fi
            fwd=$(cat /proc/sys/net/ipv4/ip_forward 2>/dev/null || echo 0)
            printf 'tun=%s\niptables=%s\nwgtools=%s\nwgkernel=%s\nipforward=%s\n' "${'$'}tun" "${'$'}ipt" "${'$'}wgt" "${'$'}wgk" "${'$'}fwd"
            """.trimIndent(),
        )
        if (!result.ok) {
            return NetworkCapabilities(detail = result.stderr.ifBlank { "Root 网络能力探测失败" })
        }
        return parse(result.stdout)
    }

    internal fun parse(stdout: String): NetworkCapabilities {
        val values = stdout.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
        val tun = values["tun"] == "1"
        val iptables = values["iptables"] == "1"
        val wgKernel = values["wgkernel"] == "1"
        val wgTools = values["wgtools"] == "1"
        val recommended = when {
            wgKernel && wgTools -> "Kernel + wg-quick"
            tun -> "Userspace GoBackend"
            else -> "不可用"
        }
        return NetworkCapabilities(
            tunAvailable = tun,
            iptablesAvailable = iptables,
            kernelWireGuardAvailable = wgKernel,
            wireGuardToolsAvailable = wgTools,
            ipv4ForwardingEnabled = values["ipforward"] == "1",
            recommendedBackend = recommended,
            detail = when {
                !tun -> "缺少 TUN 设备，无法建立 VPN 隧道"
                !iptables -> "可建立隧道，但缺少 iptables，无法完成全流量 NAT"
                wgKernel && wgTools -> "可优先使用内核 WireGuard"
                else -> "使用官方 Android userspace WireGuard，并由 Root 配置转发/NAT"
            },
        )
    }
}
