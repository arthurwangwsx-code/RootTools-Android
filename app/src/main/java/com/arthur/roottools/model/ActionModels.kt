package com.arthur.roottools.model

enum class ActionRisk {
    LOW,
    MEDIUM,
    HIGH,
}

enum class SystemActionId(
    val displayName: String,
    val description: String,
    val risk: ActionRisk,
) {
    RESTART_ADBD("Restart adbd", "保留当前 TCP port，仅重启 adbd", ActionRisk.MEDIUM),
    RESTART_SYSTEM_UI("Restart SystemUI", "系统界面会短暂消失后自动恢复", ActionRisk.MEDIUM),
    STOP_BILIBILI("Stop Bilibili", "清理播放器、下载和后台服务残留", ActionRisk.MEDIUM),
    BATTERY_PROTECTION_80("Battery Protect 80%", "开启三星 80% 电池保护", ActionRisk.MEDIUM),
}
