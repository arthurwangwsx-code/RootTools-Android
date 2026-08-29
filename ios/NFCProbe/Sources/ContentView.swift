import SwiftUI
import UIKit

struct ContentView: View {
    @ObservedObject var controller: NFCProbeController

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Label(
                            controller.isReadingAvailable ? "Core NFC 可用" : "Core NFC 不可用",
                            systemImage: controller.isReadingAvailable ? "checkmark.circle.fill" : "xmark.circle.fill"
                        )
                        .font(.headline)

                        Text(controller.status)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        Text("测试 AID: \(NFCProbeController.testAID)")
                            .font(.caption.monospaced())
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)

                    Button {
                        controller.isScanning ? controller.stopScan() : controller.startScan()
                    } label: {
                        Label(
                            controller.isScanning ? "停止扫描" : "开始 ISO14443 扫描",
                            systemImage: controller.isScanning ? "stop.circle" : "wave.3.right.circle"
                        )
                    }
                    .disabled(!controller.isReadingAvailable)
                } header: {
                    Text("实时验证")
                } footer: {
                    Text("验证 Samsung NFC Lab 时，先让三星进入 HCE 模式，再把 iPhone 顶部靠近三星 NFC 天线区域。")
                }

                if let latest = controller.records.first {
                    Section("最近结果") {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(latest.summary)
                                .font(.headline)
                            Text("Type: \(latest.tagType)")
                            Text("Identifier: \(latest.identifier.isEmpty ? "—" : latest.identifier)")
                                .font(.system(.caption, design: .monospaced))

                            ForEach(latest.details.keys.sorted(), id: \.self) { key in
                                HStack(alignment: .top) {
                                    Text(key)
                                        .foregroundStyle(.secondary)
                                    Spacer(minLength: 12)
                                    Text(latest.details[key] ?? "")
                                        .multilineTextAlignment(.trailing)
                                        .font(.system(.caption, design: .monospaced))
                                }
                            }
                        }

                        Button("复制最近结果 JSON") {
                            UIPasteboard.general.string = latest.jsonText
                        }
                    }
                }

                if !controller.records.isEmpty {
                    Section("历史") {
                        ForEach(controller.records) { record in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(record.summary)
                                Text("\(record.tagType) · \(record.identifier)")
                                    .font(.caption.monospaced())
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Button("清空记录", role: .destructive) {
                            controller.clear()
                        }
                    }
                }

                Section("能力边界") {
                    Text("iPhone 可以验证 ISO14443 RF、MIFARE-compatible 标签识别、ISO7816 AID 与 APDU。Core NFC 不提供 MIFARE Classic Crypto1 认证，因此不能把 iPhone 当作完整 Classic/Crypto1 验证器。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("NFC Probe")
        }
    }
}

