import Foundation

struct ProbeRecord: Identifiable, Codable {
    let id: UUID
    let timestamp: Date
    let tagType: String
    let identifier: String
    let summary: String
    let details: [String: String]

    init(
        id: UUID = UUID(),
        timestamp: Date = Date(),
        tagType: String,
        identifier: String,
        summary: String,
        details: [String: String]
    ) {
        self.id = id
        self.timestamp = timestamp
        self.tagType = tagType
        self.identifier = identifier
        self.summary = summary
        self.details = details
    }

    var jsonText: String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(self) else { return "{}" }
        return String(decoding: data, as: UTF8.self)
    }
}

extension Data {
    var nfcHex: String {
        map { String(format: "%02X", $0) }.joined()
    }
}

