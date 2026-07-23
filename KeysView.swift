import SwiftUI

struct KeysView: View {
    @EnvironmentObject var store: KeyStore
    @EnvironmentObject var theme: ThemeManager

    @State private var showGenerate = false
    @State private var showImport = false
    @State private var showScanner = false
    @State private var exportContext: ExportContext? = nil
    @State private var qrRecord: KeyRecord? = nil
    @State private var deleteTarget: KeyRecord? = nil

    struct ExportContext: Identifiable {
        let id = UUID()
        let record: KeyRecord
        let kind: ExportKind
    }
    enum ExportKind { case publicKey, privateKey }

    var body: some View {
        NavigationStack {
            Group {
                if store.keys.isEmpty {
                    emptyState
                } else {
                    keyList
                }
            }
            .background(theme.current.backgroundColor.ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) { BrandMark() }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Menu {
                        Button { showImport = true } label: {
                            Label("Import…", systemImage: "square.and.arrow.down")
                        }
                        Button { showScanner = true } label: {
                            Label("Scan QR…", systemImage: "qrcode.viewfinder")
                        }
                        Button { showGenerate = true } label: {
                            Label("Generate new", systemImage: "wand.and.stars")
                        }
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showGenerate) {
                GenerateKeySheet().environmentObject(store)
            }
            .sheet(isPresented: $showImport) {
                ImportKeySheet().environmentObject(store)
            }
            .sheet(isPresented: $showScanner) {
                QRScanSheet()
                    .environmentObject(store)
                    .environmentObject(theme)
            }
            .sheet(item: $exportContext) { ctx in
                ExportKeySheet(record: ctx.record, kind: ctx.kind)
            }
            .sheet(item: $qrRecord) { rec in
                KeyQRSheet(record: rec)
                    .environmentObject(theme)
            }
            .confirmationDialog(
                "Delete \(deleteTarget?.primaryUser.name ?? "key")?",
                isPresented: Binding(
                    get: { deleteTarget != nil },
                    set: { if !$0 { deleteTarget = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button("Delete Key", role: .destructive) {
                    if let rec = deleteTarget {
                        store.remove(id: rec.id)
                    }
                    deleteTarget = nil
                }
                Button("Cancel", role: .cancel) { deleteTarget = nil }
            } message: {
                Text(deleteTarget?.hasPrivate == true
                     ? "This deletes the PRIVATE key. Without a backup you will permanently lose the ability to decrypt messages sent to it."
                     : "This removes the public key from your keyring.")
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: "key.fill")
                .font(.system(size: 56))
                .foregroundStyle(theme.current.accentColor.gradient)
            Text("No keys yet")
                .font(.title2.weight(.semibold))
            Text("Generate a new key pair or import one to get started.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 40)
            HStack {
                Button { showImport = true } label: {
                    Label("Import", systemImage: "square.and.arrow.down")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glass)
                Button { showGenerate = true } label: {
                    Label("Generate", systemImage: "wand.and.stars")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glassProminent)
            }
            .padding(.horizontal, 24)
            Spacer()
        }
    }

    private var keyList: some View {
        List {
            ForEach(store.keys) { rec in
                KeyRow(record: rec, exportContext: $exportContext, qrRecord: $qrRecord)
                    .listRowBackground(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(.ultraThinMaterial)
                            .padding(.vertical, 4)
                    )
                    .listRowSeparator(.hidden)
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            deleteTarget = rec
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

}

struct KeyRow: View {
    let record: KeyRecord
    @Binding var exportContext: KeysView.ExportContext?
    @Binding var qrRecord: KeyRecord?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(record.primaryUser.name.isEmpty ? "(unnamed)" : record.primaryUser.name)
                        .font(.body.weight(.semibold))
                    if !record.primaryUser.email.isEmpty {
                        Text(record.primaryUser.email)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                HStack(spacing: 4) {
                    if record.hasPublic { Badge(text: "Public", color: .blue) }
                    if record.hasPrivate { Badge(text: "Private", color: .green) }
                }
            }

            Text(record.prettyFingerprint)
                .font(.caption2.monospaced())
                .foregroundStyle(.tertiary)

            HStack(spacing: 8) {
                if record.hasPublic {
                    Button {
                        exportContext = .init(record: record, kind: .publicKey)
                    } label: {
                        Label("Public", systemImage: "square.and.arrow.up")
                            .lineLimit(1)
                            .fixedSize()
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                }
                if record.hasPrivate {
                    Button {
                        exportContext = .init(record: record, kind: .privateKey)
                    } label: {
                        Label("Private", systemImage: "square.and.arrow.up")
                            .lineLimit(1)
                            .fixedSize()
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                }
                if record.hasPublic {
                    Button {
                        qrRecord = record
                    } label: {
                        Label("QR", systemImage: "qrcode")
                            .lineLimit(1)
                            .fixedSize()
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                }
                Spacer()
            }
            .padding(.top, 4)
        }
        .padding(.vertical, 4)
    }
}

struct Badge: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text.uppercased())
            .font(.caption2.weight(.bold))
            .tracking(0.4)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(color.opacity(0.15), in: Capsule())
            .foregroundStyle(color)
    }
}
