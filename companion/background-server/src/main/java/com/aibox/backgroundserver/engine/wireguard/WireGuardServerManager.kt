package com.aibox.backgroundserver.engine.wireguard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.edit
import com.aibox.backgroundserver.domain.TunnelRuntimeState
import com.aibox.backgroundserver.domain.WireGuardServerState
import com.aibox.backgroundserver.engine.BackgroundEngine
import com.aibox.backgroundserver.platform.network.RootRoutingController
import com.aibox.backgroundserver.platform.root.RootCommandGateway
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WireGuardServerManager(
    private val application: Application,
    root: RootCommandGateway,
) : BackgroundEngine {
    override val engineId: String = "wireguard"
    private val backend by lazy { GoBackend(application) }
    private val routing = RootRoutingController(root)
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val serverKeys = loadOrCreateKeyPair(SERVER_PRIVATE_KEY)
    private val testClientKeys = loadOrCreateKeyPair(TEST_CLIENT_PRIVATE_KEY)
    private var endpointHost: String = "<LAN-IP>"
    private var tunnelInterface: String? = null

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) = Unit
    }

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<WireGuardServerState> = _state.asStateFlow()

    fun vpnPermissionIntent(): Intent? = VpnService.prepare(application)

    fun refreshPermissionState() {
        _state.update { it.copy(requiresVpnPermission = vpnPermissionIntent() != null) }
    }

    fun updateEndpointHost(host: String?) {
        endpointHost = host?.takeIf { it.isNotBlank() } ?: "<LAN-IP>"
        _state.update { it.copy(clientConfig = clientConfig()) }
    }

    override fun isRunning(): Boolean = _state.value.runtimeState == TunnelRuntimeState.RUNNING

    override fun start(): Result<Unit> = runCatching {
        if (vpnPermissionIntent() != null) {
            _state.update { it.copy(requiresVpnPermission = true, error = "需要先授权 Android VPN 隧道") }
            error("VPN permission required")
        }
        _state.update { it.copy(runtimeState = TunnelRuntimeState.STARTING, requiresVpnPermission = false, error = null) }
        backend.setState(tunnel, Tunnel.State.UP, serverConfig())
        routing.enableIpv4Forwarding().also { result ->
            check(result.ok) { result.stderr.ifBlank { "无法开启 IPv4 forwarding" } }
        }
        tunnelInterface = routing.findTunnelInterface(TUNNEL_HOST)
        val iface = checkNotNull(tunnelInterface) { "WireGuard TUN 已启动，但没有找到 $TUNNEL_HOST 对应接口" }
        routing.applyNat(iface, EGRESS_INTERFACE).also { result ->
            check(result.ok) { result.stderr.ifBlank { "无法配置 WireGuard NAT" } }
        }
        updateStatistics()
        _state.update { it.copy(runtimeState = TunnelRuntimeState.RUNNING, error = null) }
    }.onFailure { error ->
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        _state.update { it.copy(runtimeState = TunnelRuntimeState.ERROR, error = error.message ?: error.javaClass.simpleName) }
    }

    override fun stop(): Result<Unit> = runCatching {
        _state.update { it.copy(runtimeState = TunnelRuntimeState.STOPPING) }
        tunnelInterface?.let { routing.removeNat(it, EGRESS_INTERFACE) }
        backend.setState(tunnel, Tunnel.State.DOWN, null)
        tunnelInterface = null
        _state.update { it.copy(runtimeState = TunnelRuntimeState.STOPPED, rxBytes = 0L, txBytes = 0L, error = null) }
    }.onFailure { error ->
        _state.update { it.copy(runtimeState = TunnelRuntimeState.ERROR, error = error.message ?: error.javaClass.simpleName) }
    }

    fun updateStatistics() {
        if (_state.value.runtimeState != TunnelRuntimeState.RUNNING) return
        runCatching { backend.getStatistics(tunnel) }
            .onSuccess { stats -> _state.update { it.copy(rxBytes = stats.totalRx(), txBytes = stats.totalTx()) } }
    }

    private fun serverConfig(): Config {
        val iface = Interface.Builder()
            .setKeyPair(serverKeys)
            .parseAddresses(TUNNEL_CIDR)
            .setListenPort(LISTEN_PORT)
            .build()
        val peer = Peer.Builder()
            .setPublicKey(testClientKeys.publicKey)
            .parseAllowedIPs(TEST_CLIENT_CIDR)
            .build()
        return Config.Builder().setInterface(iface).addPeer(peer).build()
    }

    private fun clientConfig(): String = """
        [Interface]
        PrivateKey = ${testClientKeys.privateKey.toBase64()}
        Address = $TEST_CLIENT_CIDR
        DNS = 1.1.1.1

        [Peer]
        PublicKey = ${serverKeys.publicKey.toBase64()}
        Endpoint = $endpointHost:$LISTEN_PORT
        AllowedIPs = 0.0.0.0/0
        PersistentKeepalive = 25
    """.trimIndent()

    private fun initialState() = WireGuardServerState(
        runtimeState = TunnelRuntimeState.STOPPED,
        backend = "Official userspace GoBackend",
        listenPort = LISTEN_PORT,
        tunnelAddress = TUNNEL_CIDR,
        peerAddress = TEST_CLIENT_CIDR,
        serverPublicKey = serverKeys.publicKey.toBase64(),
        egressInterface = EGRESS_INTERFACE,
        requiresVpnPermission = vpnPermissionIntent() != null,
        clientConfig = clientConfig(),
    )

    private fun loadOrCreateKeyPair(prefKey: String): KeyPair {
        val saved = prefs.getString(prefKey, null)
        if (!saved.isNullOrBlank()) {
            runCatching { return KeyPair(Key.fromBase64(saved)) }
        }
        return KeyPair().also { pair -> prefs.edit { putString(prefKey, pair.privateKey.toBase64()) } }
    }

    companion object {
        private const val PREFS = "wireguard-server"
        private const val SERVER_PRIVATE_KEY = "server-private-key"
        private const val TEST_CLIENT_PRIVATE_KEY = "test-client-private-key"
        private const val TUNNEL_NAME = "background-server"
        private const val LISTEN_PORT = 51820
        private const val TUNNEL_HOST = "10.77.0.1"
        private const val TUNNEL_CIDR = "$TUNNEL_HOST/24"
        private const val TEST_CLIENT_CIDR = "10.77.0.2/32"
        private const val EGRESS_INTERFACE = "wlan0"
    }
}
