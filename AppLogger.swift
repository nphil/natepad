import Foundation
import SwiftUI
import UIKit

// MARK: - Logger

@MainActor
final class AppLogger: ObservableObject {
    static let shared = AppLogger()

    struct Entry: Identifiable {
        let id = UUID()
        let timestamp: Date
        let level: String
        let message: String
    }

    @Published private(set) var entries: [Entry] = []
    private let maxEntries = 1000

    private init() {}

    func log(_ message: String, level: String = "INFO") {
        let entry = Entry(timestamp: Date(), level: level, message: message)
        entries.append(entry)
        if entries.count > maxEntries {
            entries.removeFirst(entries.count - maxEntries)
        }
        // Also write to stdout so it shows up in any console attached.
        print("[\(level)] \(message)")
    }

    func clear() { entries.removeAll() }

    var asText: String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss.SSS"
        return entries.map { e in
            "[\(f.string(from: e.timestamp))] [\(e.level)] \(e.message)"
        }.joined(separator: "\n")
    }
}

/// Call from anywhere — schedules the log on the main actor.
func appLog(_ message: String, level: String = "INFO") {
    Task { @MainActor in
        AppLogger.shared.log(message, level: level)
    }
}

// MARK: - Logs viewer

struct LogsView: View {
    @ObservedObject private var logger = AppLogger.shared
    @State private var copied = false

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 6) {
                    ForEach(logger.entries) { entry in
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(entry.level)
                                    .font(.system(.caption2, design: .monospaced).weight(.semibold))
                                    .foregroundStyle(color(for: entry.level))
                                Text(timestamp(entry.timestamp))
                                    .font(.system(.caption2, design: .monospaced))
                                    .foregroundStyle(.secondary)
                            }
                            Text(entry.message)
                                .font(.system(.caption, design: .monospaced))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .textSelection(.enabled)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(.ultraThinMaterial.opacity(0.3), in: RoundedRectangle(cornerRadius: 8))
                        .id(entry.id)
                    }
                    if logger.entries.isEmpty {
                        Text("No logs yet. Try the action you want to debug.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .padding()
                    }
                }
                .padding()
            }
            .onChange(of: logger.entries.count) { _, _ in
                if let last = logger.entries.last {
                    withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                }
            }
        }
        .navigationTitle("Logs")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    UIPasteboard.general.string = logger.asText
                    copied = true
                    Task {
                        try? await Task.sleep(nanoseconds: 1_500_000_000)
                        copied = false
                    }
                } label: {
                    Label(copied ? "Copied!" : "Copy all",
                          systemImage: copied ? "checkmark" : "doc.on.doc")
                }
            }
            ToolbarItem(placement: .secondaryAction) {
                Button(role: .destructive) {
                    logger.clear()
                } label: {
                    Label("Clear", systemImage: "trash")
                }
            }
        }
    }

    private func timestamp(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss.SSS"
        return f.string(from: date)
    }

    private func color(for level: String) -> Color {
        switch level {
        case "ERROR": return .red
        case "WARN": return .orange
        case "DEBUG": return .secondary
        default: return .blue
        }
    }
}
