import SwiftUI

/// Pain qualities proposed after zone + intensity, PainDiary-style.
let PAIN_QUALITIES = [
    "Burning", "Stabbing", "Throbbing", "Dull ache",
    "Pressing", "Tingling", "Cramping", "Shooting"
]

/// Three-part pain check-in inspired by Privacy Friendly Pain Diary:
/// 1. locate pain on the 3D body map, 2. rate intensity, 3. qualify the pain.
struct PainDiaryStep: View {
    let submitLabel: String
    let onSubmit: (_ level: Int, _ zoneLabels: [String], _ qualities: [String]) -> Void

    @State private var subStep = 0
    @State private var zoneLabels: [String] = []
    @State private var level: Double = 0
    @State private var qualities: Set<String> = []
    @State private var viewSide = "front"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                switch subStep {
                case 0:
                    zonesStep
                case 1:
                    intensityStep
                default:
                    qualitiesStep
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - 1. Body zones

    private var zonesStep: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Where is the pain?")
                .font(.title3)
                .fontWeight(.bold)
            Text("Drag to rotate — tap the areas that hurt")
                .font(.caption)
                .foregroundColor(.gray)

            ZStack(alignment: .topTrailing) {
                BodyMapView(viewSide: viewSide) { _, labels in
                    zoneLabels = labels
                }
                .frame(maxWidth: .infinity)
                .frame(height: 320)

                Button(action: { viewSide = viewSide == "front" ? "back" : "front" }) {
                    Text(viewSide == "front" ? "Show back" : "Show front")
                        .font(.caption)
                        .foregroundColor(.black)
                        .padding(.vertical, 6)
                        .padding(.horizontal, 10)
                        .background(Color.white.opacity(0.9))
                        .cornerRadius(6)
                }
                .padding(8)
            }

            if zoneLabels.isEmpty {
                Text("No area selected — pain is general")
                    .font(.caption)
                    .foregroundColor(Color(UIColor.systemGray3))
            } else {
                FlexibleChipRow(labels: zoneLabels)
            }

            LpmPrimaryButton(text: "Next") { subStep = 1 }
                .padding(.top, 8)
        }
    }

    // MARK: - 2. Intensity

    private var intensityStep: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("How strong is the pain?")
                .font(.title3)
                .fontWeight(.bold)

            HStack {
                Text("0").foregroundColor(.gray).font(.caption)
                Spacer()
                Text("10").foregroundColor(.gray).font(.caption)
            }
            Slider(value: $level, in: 0...10, step: 1)
                .tint(.black)
            Text("\(Int(level)) / 10")
                .font(.title)
                .fontWeight(.bold)
                .frame(maxWidth: .infinity)

            LpmPrimaryButton(text: "Next") { subStep = 2 }
                .padding(.top, 8)
            Button("Back") { subStep = 0 }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
        }
    }

    // MARK: - 3. Qualities

    private var qualitiesStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("What does it feel like?")
                .font(.title3)
                .fontWeight(.bold)
            Text("Optional — pick all that apply")
                .font(.caption)
                .foregroundColor(.gray)

            FlowLayout(spacing: 8) {
                ForEach(PAIN_QUALITIES, id: \.self) { quality in
                    let isSelected = qualities.contains(quality)
                    Button(action: {
                        if isSelected { qualities.remove(quality) }
                        else { qualities.insert(quality) }
                    }) {
                        Text(quality)
                            .font(.subheadline)
                            .padding(.vertical, 8)
                            .padding(.horizontal, 16)
                            .background(isSelected ? Color.black : Color(UIColor.systemGray6))
                            .foregroundColor(isSelected ? .white : .black)
                            .cornerRadius(20)
                    }
                }
            }

            LpmPrimaryButton(text: submitLabel) {
                let ordered = PAIN_QUALITIES.filter { qualities.contains($0) }
                onSubmit(Int(level), zoneLabels, ordered)
            }
            .padding(.top, 8)
            Button("Back") { subStep = 1 }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
        }
    }
}

/// Selected zones as monochrome chips.
private struct FlexibleChipRow: View {
    let labels: [String]

    var body: some View {
        FlowLayout(spacing: 6) {
            ForEach(labels, id: \.self) { label in
                Text(label)
                    .font(.caption)
                    .padding(.vertical, 6)
                    .padding(.horizontal, 12)
                    .background(Color.black)
                    .foregroundColor(.white)
                    .cornerRadius(16)
            }
        }
    }
}

/// Minimal wrapping layout (no external dependency).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var maxWidth: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > width, x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            maxWidth = max(maxWidth, x)
        }
        return CGSize(width: min(maxWidth, width), height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
