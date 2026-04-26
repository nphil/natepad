import SwiftUI

struct KeysView: View {
    @EnvironmentObject var store: KeyStore

    @State private var showGenerate = false
    @State private var showImport = false
    @State private var exportContext: ExportContext? = nil

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
            .background(ThemeBackground().ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) { BrandMark() }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Menu {
                        Button { showImport = true } label: {
                            Label("Import…", systemImage: "square.and.arrow.down")
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
            .sheet(item: $exportContext) { ctx in
                ExportKeySheet(record: ctx.record, kind: ctx.kind)
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: "key.fill")
                .font(.system(size: 56))
                .foregroundStyle(.indigo.gradient)
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
                KeyRow(record: rec, exportContext: $exportContext)
                    .listRowBackground(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(.ultraThinMaterial)
                            .padding(.vertical, 4)
                    )
                    .listRowSeparator(.hidden)
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            confirmDelete(rec)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private func confirmDelete(_ rec: KeyRecord) {
        // SwiftUI's confirmationDialog requires hoisting state; do it inline via a UIAlert-like approach.
        // For simplicity, just remove. The destructive swipe action already requires intent.
        store.remove(id: rec.id)
    }
}

struct KeyRow: View {
    let record: KeyRecord
    @Binding var exportContext: KeysView.ExportContext?

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
                            .font(.caption)
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                }
                if record.hasPrivate {
                    Button {
                        exportContext = .init(record: record, kind: .privateKey)
                    } label: {
                        Label("Private", systemImage: "square.and.arrow.up")
                            .font(.caption)
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                }
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
