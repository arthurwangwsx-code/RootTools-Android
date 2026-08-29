import CoreNFC
import Foundation

final class NFCProbeController: NSObject, ObservableObject {
    static let testAID = "F001020304050607"

    @Published private(set) var isScanning = false
    @Published private(set) var status = "准备扫描 Samsung NFC Lab 或 ISO14443 标签"
    @Published private(set) var records: [ProbeRecord] = []

    private var session: NFCTagReaderSession?
    private let sessionQueue = DispatchQueue(label: "com.arthur.nfctools.nfcprobe.session")

    var isReadingAvailable: Bool {
        NFCTagReaderSession.readingAvailable
    }

    func startScan() {
        guard NFCTagReaderSession.readingAvailable else {
            status = "当前设备不支持 Core NFC Tag Reader Session。请在真机 iPhone 上运行。"
            return
        }

        guard let session = NFCTagReaderSession(
            pollingOption: [.iso14443],
            delegate: self,
            queue: sessionQueue
        ) else {
            status = "无法创建 NFC Reader Session"
            return
        }
        session.alertMessage = "把 iPhone 顶部靠近 Samsung 手机 NFC 天线区域，或靠近待测 ISO14443 标签。"
        self.session = session
        isScanning = true
        status = "扫描中：ISO14443-A/B / MIFARE / ISO7816"
        session.begin()
    }

    func stopScan() {
        session?.invalidate()
        session = nil
        isScanning = false
        status = "扫描已停止"
    }

    func clear() {
        records.removeAll()
        status = "记录已清空"
    }

    private func publish(_ record: ProbeRecord, status newStatus: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.records.insert(record, at: 0)
            self.status = newStatus
        }
    }

    private func finishSession(_ message: String) {
        session?.alertMessage = message
        session?.invalidate()
        DispatchQueue.main.async { [weak self] in
            self?.session = nil
            self?.isScanning = false
        }
    }

    private func handleISO7816(_ tag: NFCISO7816Tag, session: NFCTagReaderSession) {
        var baseDetails: [String: String] = [
            "initialSelectedAID": tag.initialSelectedAID,
            "historicalBytes": tag.historicalBytes?.nfcHex ?? "",
            "applicationData": tag.applicationData?.nfcHex ?? "",
            "proprietaryApplicationDataCoding": String(tag.proprietaryApplicationDataCoding),
        ]

        session.connect(to: .iso7816(tag)) { [weak self] error in
            guard let self else { return }
            if let error {
                baseDetails["connectError"] = error.localizedDescription
                let record = ProbeRecord(
                    tagType: "ISO7816",
                    identifier: tag.identifier.nfcHex,
                    summary: "发现 ISO7816，但连接失败",
                    details: baseDetails
                )
                self.publish(record, status: "ISO7816 连接失败")
                self.finishSession("检测到 ISO7816，但连接失败。")
                return
            }

            guard let getData = NFCISO7816APDU(data: Data([0x80, 0xCA, 0x00, 0x00, 0x00])) else {
                baseDetails["apduError"] = "Failed to construct GET DATA APDU"
                let record = ProbeRecord(
                    tagType: "ISO7816",
                    identifier: tag.identifier.nfcHex,
                    summary: "ISO7816 已连接，但测试 APDU 构造失败",
                    details: baseDetails
                )
                self.publish(record, status: "APDU 构造失败")
                self.finishSession("ISO7816 已连接。")
                return
            }

            tag.sendCommand(apdu: getData) { result in
                switch result {
                case .success(let response):
                    baseDetails["getDataPayloadHex"] = response.payload?.nfcHex ?? ""
                    if let payload = response.payload,
                       let utf8 = String(data: payload, encoding: .utf8) {
                        baseDetails["getDataPayloadUTF8"] = utf8
                    }
                    baseDetails["getDataStatus"] = String(
                        format: "%02X%02X",
                        response.statusWord1,
                        response.statusWord2
                    )
                    let aidMatches = tag.initialSelectedAID.uppercased() == Self.testAID
                    baseDetails["testAIDMatched"] = String(aidMatches)
                    let record = ProbeRecord(
                        tagType: "ISO7816",
                        identifier: tag.identifier.nfcHex,
                        summary: aidMatches
                            ? "Samsung NFC Lab HCE 已通过真实 RF + AID + APDU 验证"
                            : "ISO7816 标签已连接并完成 APDU",
                        details: baseDetails
                    )
                    self.publish(record, status: record.summary)
                    self.finishSession(aidMatches ? "HCE 验证成功。" : "ISO7816 验证完成。")

                case .failure(let error):
                    baseDetails["getDataError"] = error.localizedDescription
                    let record = ProbeRecord(
                        tagType: "ISO7816",
                        identifier: tag.identifier.nfcHex,
                        summary: "ISO7816 已连接，但测试 APDU 失败",
                        details: baseDetails
                    )
                    self.publish(record, status: "ISO7816 APDU 失败")
                    self.finishSession("检测到 ISO7816，但 APDU 失败。")
                }
            }
        }
    }

    private func handleMiFare(_ tag: NFCMiFareTag, session: NFCTagReaderSession) {
        let family = mifareFamilyName(rawValue: tag.mifareFamily.rawValue)
        let record = ProbeRecord(
            tagType: "MIFARE",
            identifier: tag.identifier.nfcHex,
            summary: "发现 MIFARE-compatible ISO14443-A 标签（family: \(family)）",
            details: [
                "mifareFamily": family,
                "mifareFamilyRaw": String(tag.mifareFamily.rawValue),
                "historicalBytes": tag.historicalBytes?.nfcHex ?? "",
                "note": "Core NFC 不提供 MIFARE Classic Crypto1 认证，因此这里只做射频/类型/UID 观测。",
            ]
        )
        publish(record, status: record.summary)
        finishSession("MIFARE 标签识别完成。")
    }

    private func mifareFamilyName(rawValue: UInt) -> String {
        switch rawValue {
        case 1: return "unknown / MIFARE-compatible Type A"
        case 2: return "Ultralight"
        case 3: return "Plus"
        case 4: return "DESFire"
        default: return "raw-\(rawValue)"
        }
    }
}

extension NFCProbeController: NFCTagReaderSessionDelegate {
    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {
        DispatchQueue.main.async { [weak self] in
            self?.isScanning = true
            self?.status = "NFC Reader 已激活，等待标签进入 RF 场"
        }
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.isScanning = false
            self.session = nil
            let readerError = error as? NFCReaderError
            if readerError?.code == .readerSessionInvalidationErrorUserCanceled {
                self.status = "扫描已取消"
            } else if readerError?.code == .readerSessionInvalidationErrorFirstNDEFTagRead {
                self.status = "扫描完成"
            } else {
                self.status = "NFC Session 结束：\(error.localizedDescription)"
            }
        }
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard tags.count == 1, let tag = tags.first else {
            session.alertMessage = "一次只保留一个 NFC 对端。"
            session.restartPolling()
            return
        }

        switch tag {
        case .iso7816(let isoTag):
            handleISO7816(isoTag, session: session)
        case .miFare(let mifareTag):
            handleMiFare(mifareTag, session: session)
        case .feliCa(let tag):
            let record = ProbeRecord(
                tagType: "FeliCa",
                identifier: tag.currentIDm.nfcHex,
                summary: "发现 NFC-F / FeliCa 标签",
                details: ["systemCode": tag.currentSystemCode.nfcHex]
            )
            publish(record, status: record.summary)
            finishSession("FeliCa 标签识别完成。")
        case .iso15693(let tag):
            let record = ProbeRecord(
                tagType: "ISO15693",
                identifier: tag.identifier.nfcHex,
                summary: "发现 ISO15693 标签",
                details: [:]
            )
            publish(record, status: record.summary)
            finishSession("ISO15693 标签识别完成。")
        @unknown default:
            finishSession("发现未知 NFC Tag 类型。")
        }
    }
}

