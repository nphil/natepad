import SwiftUI

// MARK: - App themes

enum AppTheme: String, CaseIterable, Identifiable {
    case midnight, ocean, forest, dusk, obsidian

    var id: String { rawValue }

    var name: String {
        switch self {
        case .midnight: return "Midnight"
        case .ocean:    return "Ocean"
        case .forest:   return "Forest"
        case .dusk:     return "Dusk"
        case .obsidian: return "Obsidian"
        }
    }

    var gradientColors: [Color] {
        switch self {
        case .midnight: return [Color(hex: "0a0e1a"), Color(hex: "1e1b4b")]
        case .ocean:    return [Color(hex: "071828"), Color(hex: "0c3a50")]
        case .forest:   return [Color(hex: "080d0a"), Color(hex: "0d2018")]
        case .dusk:     return [Color(hex: "180a1e"), Color(hex: "2e0d38")]
        case .obsidian: return [Color(hex: "0a0a0f"), Color(hex: "14142a")]
        }
    }

    var accentColor: Color {
        switch self {
        case .midnight: return .indigo
        case .ocean:    return Color(hex: "38bdf8")
        case .forest:   return Color(hex: "4ade80")
        case .dusk:     return Color(hex: "d946ef")
        case .obsidian: return Color(hex: "94a3b8")
        }
    }

    var swatchColors: [Color] {
        switch self {
        case .midnight: return [Color(hex: "4338ca"), Color(hex: "0f0a2e")]
        case .ocean:    return [Color(hex: "0369a1"), Color(hex: "071828")]
        case .forest:   return [Color(hex: "16a34a"), Color(hex: "052e16")]
        case .dusk:     return [Color(hex: "a21caf"), Color(hex: "2e0d38")]
        case .obsidian: return [Color(hex: "475569"), Color(hex: "0a0a0f")]
        }
    }
}

extension Color {
    init(hex: String) {
        var rgb: UInt64 = 0
        Scanner(string: hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))).scanHexInt64(&rgb)
        self.init(
            red:   Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >>  8) & 0xFF) / 255,
            blue:  Double( rgb        & 0xFF) / 255
        )
    }
}

// MARK: - Theme manager

@MainActor
final class ThemeManager: ObservableObject {
    @Published private(set) var current: AppTheme {
        didSet { UserDefaults.standard.set(current.rawValue, forKey: "natepad.theme") }
    }

    init() {
        if let raw = UserDefaults.standard.string(forKey: "natepad.theme"),
           let saved = AppTheme(rawValue: raw) {
            current = saved
        } else {
            current = .midnight
        }
    }

    func set(_ theme: AppTheme) { current = theme }
}

// MARK: - Atmospheric background

/// Full-screen background with blurred colour orbs on black — used inside each tab's NavigationStack.
struct ThemeBackground: View {
    @EnvironmentObject var theme: ThemeManager

    var body: some View {
        ZStack {
            Color.black
            Circle()
                .fill(theme.current.accentColor.opacity(0.60))
                .frame(width: 380)
                .blur(radius: 120)
                .offset(x: -90, y: -220)
            Circle()
                .fill(theme.current.accentColor.opacity(0.40))
                .frame(width: 340)
                .blur(radius: 100)
                .offset(x: 150, y: 320)
        }
        .ignoresSafeArea()
    }
}

// MARK: - Theme swatch

struct ThemeSwatch: View {
    let theme: AppTheme
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 6) {
                ZStack {
                    LinearGradient(
                        colors: theme.swatchColors,
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .clipShape(Circle())
                    .frame(width: 52, height: 52)

                    if isSelected {
                        Image(systemName: "checkmark")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(.white)
                    }
                }
                .overlay(
                    Circle().stroke(
                        isSelected ? theme.accentColor : Color.white.opacity(0.15),
                        lineWidth: isSelected ? 2.5 : 1
                    )
                )

                Text(theme.name)
                    .font(.caption2)
                    .foregroundStyle(isSelected ? .primary : .secondary)
            }
        }
        .buttonStyle(.plain)
    }
}
