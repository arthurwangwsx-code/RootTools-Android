package com.arthur.roottools.feature.network.tailscale.data

object RootTailscaleRuntimeSpec {
    const val VERSION = "1.102.3"
    const val ARCH = "arm64"
    const val DOWNLOAD_URL = "https://pkgs.tailscale.com/stable/tailscale_1.102.3_arm64.tgz"
    const val SHA256 = "a0fa1b154af8c61f862a2259f559f7396d96c0225f4a863eae2333e1546bbe25"
    const val BASE_DIR = "/data/adb/tailscale"
    const val IDENTITY_MARKER = "$BASE_DIR/state/authenticated.marker"
    const val BOOT_SCRIPT = "/data/adb/service.d/99-roottools-tailscale.sh"
    const val OFFICIAL_PACKAGE = "com.tailscale.ipn"
    const val HIDDIFY_PACKAGE = "app.hiddify.com"
}
