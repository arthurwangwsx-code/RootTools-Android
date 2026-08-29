package com.aibox.backgroundserver.ui.power

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aibox.backgroundserver.domain.PowerSettings
import com.aibox.backgroundserver.ui.components.CardDivider
import com.aibox.backgroundserver.ui.components.PageScaffold
import com.aibox.backgroundserver.ui.components.SectionCard
import com.aibox.backgroundserver.ui.components.SettingRow
import com.aibox.backgroundserver.ui.components.ToggleRow

@Composable
fun ScreenWakeScreen(
    settings: PowerSettings,
    onBack: () -> Unit,
    onDoubleTapToWake: (Boolean) -> Unit,
    onScreenOffWithoutLock: (Boolean) -> Unit,
    onSleepNow: () -> Unit,
    onWakeNow: () -> Unit,
) {
    PageScaffold(title = "息屏与唤醒", onBack = onBack) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard {
                    SettingRow(
                        title = "当前显示状态",
                        subtitle = if (settings.interactive) "屏幕可交互 / Awake" else "屏幕已关闭或 Dozing",
                    )
                    CardDivider()
                    ToggleRow(
                        title = "双击屏幕唤醒",
                        subtitle = "写入系统 secure.double_tap_to_wake；具体能力由设备触控驱动决定",
                        checked = settings.doubleTapToWake == true,
                        enabled = settings.doubleTapToWake != null,
                        onCheckedChange = onDoubleTapToWake,
                    )
                    CardDivider()
                    ToggleRow(
                        title = "无锁息屏",
                        subtitle = "不让 Android 进入 Sleep/Keyguard，而是把当前页面变成纯黑并将窗口亮度降到 0。双击黑屏立即恢复当前页面；省电略逊于真正 Screen OFF。",
                        checked = settings.screenOffWithoutLock,
                        onCheckedChange = onScreenOffWithoutLock,
                    )
                }
            }
            item {
                SectionCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("测试", style = MaterialTheme.typography.titleMedium)
                        Text("“立即唤醒”主要用于远程控制/ADB 验证；手机已黑屏时通常通过电源键或双击手势唤醒。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Button(onClick = onSleepNow, modifier = Modifier.fillMaxWidth()) {
                            Text(if (settings.screenOffWithoutLock) "进入无锁息屏" else "立即息屏（会锁屏）")
                        }
                        OutlinedButton(onClick = onWakeNow, modifier = Modifier.fillMaxWidth()) { Text("发送唤醒事件") }
                    }
                }
            }
        }
    }
}
