package com.aibox.backgroundserver.platform.network

import com.aibox.backgroundserver.platform.root.RootCommandGateway

class RootRoutingController(
    private val root: RootCommandGateway,
) {
    fun enableIpv4Forwarding() = root.execute(RootRoutingCommandPolicy.ENABLE_IPV4_FORWARDING)

    fun findTunnelInterface(addressPrefix: String = "10.77.0.1"): String? {
        val command = RootRoutingCommandPolicy.findTunnelInterface(addressPrefix) ?: return null
        val result = root.execute(command)
        return result.stdout.trim().takeIf { result.ok }?.let(RootRoutingCommandPolicy::validInterface)
    }

    fun applyNat(tunnelInterface: String, egressInterface: String, subnet: String = "10.77.0.0/24") =
        RootRoutingCommandPolicy.natRules(tunnelInterface, egressInterface, subnet)
            ?.let { root.execute(it.apply) }
            ?: com.aibox.backgroundserver.platform.root.RootCommandResult(2, "", "invalid routing input")

    fun removeNat(tunnelInterface: String, egressInterface: String, subnet: String = "10.77.0.0/24") =
        RootRoutingCommandPolicy.natRules(tunnelInterface, egressInterface, subnet)
            ?.let { root.execute(it.remove) }
            ?: com.aibox.backgroundserver.platform.root.RootCommandResult(2, "", "invalid routing input")
}
