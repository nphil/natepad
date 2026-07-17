import SwiftUI
import UIKit
import AVFoundation
import CoreImage.CIFilterBuiltins

// MARK: - QR generation

enum QRCode {
    /// Practical payload ceiling. QR v40 at error-correction L holds 2953 bytes,
    /// but codes that dense barely scan from a phone screen — stay under it.
    static let maxPayloadBytes = 2500

    /// Standard URI scheme for OpenPGP fingerprints (OpenKeychain-compatible).
    static let fingerprintScheme = "OPENPGP4FPR:"

    static func generate(from string: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "L"
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 12, y: 12))
        let context = CIContext()
        guard let cg = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

// MARK: - Show a key as QR

struct KeyQRSheet: View {
    let record: KeyRecord
    @EnvironmentObject var theme: ThemeManager
    @Environment(\.dismiss) var dismiss

    private enum Mode: String, CaseIterable, Identifiable {
        case fingerprint = "Fingerprint"
        case publicKey = "Public Key"
        var id: String { rawValue }
    }

    @State private var mode: Mode = .fingerprint
    @State private var image: UIImage? = nil

    private var keyFits: Bool {
        guard let armored = record.armoredPublic else { return false }
        return armored.utf8.count <= QRCode.maxPayloadBytes
    }

    private var payload: String? {
        switch mode {
        case .fingerprint:
            return QRCode.fingerprintScheme + record.fingerprint
        case .publicKey:
            return keyFits ? record.armoredPublic : nil
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Picker("Content", selection: $mode) {
                    ForEach(Mode.allCases) { m in Text(m.rawValue).tag(m) }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)

                if let image {
                    Image(uiImage: image)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: 320, maxHeight: 320)
                        .padding(12)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 16))
                } else if mode == .publicKey && !keyFits {
                    VStack(spacing: 10) {
                        Image(systemName: "qrcode")
                            .font(.system(size: 44))
                            .foregroundStyle(.secondary)
                        Text("This public key is too large for a reliable QR code (RSA-4096 keys usually are). Share the fingerprint QR to verify in person, and send the key itself as text or a file.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }
                    .frame(maxWidth: .infinity, minHeight: 280)
                } else {
                    ProgressView()
                        .frame(maxWidth: .infinity, minHeight: 280)
                }

                VStack(spacing: 4) {
                    Text(record.label)
                        .font(.callout.weight(.medium))
                        .lineLimit(1)
                    Text(record.prettyFingerprint)
                        .font(.caption2.monospaced())
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal, 16)

                if mode == .fingerprint {
                    Text("Scanning this verifies the key's fingerprint — it does not transfer the key.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }

                Spacer()
            }
            .padding(.top, 16)
            .background(theme.current.backgroundColor.ignoresSafeArea())
            .navigationTitle("Key QR Code")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task(id: mode) {
                image = nil
                guard let payload else { return }
                let generated = await Task.detached(priority: .userInitiated) {
                    QRCode.generate(from: payload)
                }.value
                image = generated
            }
        }
        .presentationDetents([.large])
        .presentationBackground(.regularMaterial)
    }
}

// MARK: - Camera scanner (AVFoundation)

final class ScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onCode: ((String) -> Void)?
    var onPermissionDenied: (() -> Void)?

    private let session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var configured = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureAndStart()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted { self?.configureAndStart() } else { self?.onPermissionDenied?() }
                }
            }
        default:
            onPermissionDenied?()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if session.isRunning { session.stopRunning() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    /// Re-arms the scanner after a result was handled.
    func resumeScanning() {
        guard configured, !session.isRunning else { return }
        DispatchQueue.global(qos: .userInitiated).async { [session] in
            session.startRunning()
        }
    }

    private func configureAndStart() {
        if !configured {
            guard let device = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: device),
                  session.canAddInput(input) else { return }
            session.addInput(input)

            let output = AVCaptureMetadataOutput()
            guard session.canAddOutput(output) else { return }
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.qr]

            let layer = AVCaptureVideoPreviewLayer(session: session)
            layer.videoGravity = .resizeAspectFill
            layer.frame = view.bounds
            view.layer.addSublayer(layer)
            previewLayer = layer
            configured = true
        }
        resumeScanning()
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              obj.type == .qr,
              let value = obj.stringValue else { return }
        session.stopRunning()
        onCode?(value)
    }
}

struct QRScannerRepresentable: UIViewControllerRepresentable {
    let onCode: (String) -> Void
    let onPermissionDenied: () -> Void
    @Binding var resumeToken: Int

    func makeUIViewController(context: Context) -> ScannerViewController {
        let vc = ScannerViewController()
        vc.onCode = onCode
        vc.onPermissionDenied = onPermissionDenied
        return vc
    }

    func updateUIViewController(_ vc: ScannerViewController, context: Context) {
        if context.coordinator.lastResumeToken != resumeToken {
            context.coordinator.lastResumeToken = resumeToken
            vc.resumeScanning()
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }
    final class Coordinator { var lastResumeToken = 0 }
}

// MARK: - Scan sheet with result handling

struct QRScanSheet: View {
    @EnvironmentObject var store: KeyStore
    @EnvironmentObject var theme: ThemeManager
    @Environment(\.dismiss) var dismiss

    private enum ScanOutcome {
        case fingerprintMatch(KeyRecord)
        case fingerprintUnknown(String)
        case imported([KeyRecord])
        case importFailed(String)
        case unsupported
    }

    @State private var outcome: ScanOutcome? = nil
    @State private var permissionDenied = false
    @State private var resumeToken = 0

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if permissionDenied {
                    VStack(spacing: 14) {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 44))
                            .foregroundStyle(.secondary)
                        Text("Camera access is off")
                            .font(.headline)
                        Text("Allow camera access in Settings to scan key QR codes.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            Link("Open Settings", destination: url)
                                .buttonStyle(.glassProminent)
                        }
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    QRScannerRepresentable(
                        onCode: { handle($0) },
                        onPermissionDenied: { permissionDenied = true },
                        resumeToken: $resumeToken
                    )
                    .ignoresSafeArea(edges: .bottom)
                    .overlay(alignment: .bottom) {
                        resultOverlay
                    }
                }
            }
            .background(theme.current.backgroundColor.ignoresSafeArea())
            .navigationTitle("Scan Key QR")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.large])
    }

    @ViewBuilder
    private var resultOverlay: some View {
        if let outcome {
            VStack(spacing: 10) {
                switch outcome {
                case .fingerprintMatch(let rec):
                    StatusBanner(kind: .success,
                                 title: "Fingerprint verified",
                                 detail: "Matches \(rec.label)")
                case .fingerprintUnknown(let fp):
                    StatusBanner(kind: .warn,
                                 title: "No matching key",
                                 detail: "Fingerprint \(fp.prefix(16))… is not in your keyring. Ask the sender for their full key.")
                case .imported(let recs):
                    StatusBanner(kind: .success,
                                 title: "Imported \(recs.count) key\(recs.count == 1 ? "" : "s")",
                                 detail: recs.first.map { $0.label })
                case .importFailed(let why):
                    StatusBanner(kind: .error, title: "Import failed", detail: why)
                case .unsupported:
                    StatusBanner(kind: .warn,
                                 title: "Not a key QR",
                                 detail: "This QR holds no PGP key or fingerprint.")
                }
                Button {
                    self.outcome = nil
                    resumeToken += 1
                } label: {
                    Label("Scan Again", systemImage: "qrcode.viewfinder")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glassProminent)
            }
            .padding(16)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
            .padding(16)
        }
    }

    private func handle(_ value: String) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.uppercased().hasPrefix(QRCode.fingerprintScheme) {
            let fp = trimmed.dropFirst(QRCode.fingerprintScheme.count)
                .replacingOccurrences(of: " ", with: "")
                .uppercased()
            if let match = store.keys.first(where: { $0.fingerprint == fp }) {
                outcome = .fingerprintMatch(match)
            } else {
                outcome = .fingerprintUnknown(fp)
            }
            return
        }
        switch PGPContentKind.detect(in: trimmed) {
        case .publicKey, .privateKey:
            do {
                let parsed = try PGPService.parseKeys(from: trimmed)
                guard !parsed.isEmpty else {
                    outcome = .importFailed("No keys found in the QR payload.")
                    return
                }
                for rec in parsed { store.add(rec) }
                outcome = .imported(parsed)
            } catch {
                outcome = .importFailed(error.localizedDescription)
            }
        default:
            outcome = .unsupported
        }
    }
}
