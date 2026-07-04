import SwiftUI

private enum RoutineStepType {
    case photo, pain, vitals, done
}

struct DailyRoutineScreen: View {
    let followUpId: String
    let followUpTitle: String
    let rules: FollowUpRules?
    let onBack: () -> Void
    let onComplete: () -> Void

    @State private var steps: [RoutineStepType] = []
    @State private var stepIndex = 0

    // Collected measurements passed between steps
    @State private var painLevel: Double = 0
    @State private var tempValue: String = ""
    @State private var bpValue: String = ""
    @State private var hrValue: String = ""

    @State private var isSaving = false
    @State private var saveError: String? = nil

    var body: some View {
        VStack(spacing: 0) {
            LpmTopBar(title: followUpTitle, onBack: onBack)

            if !steps.isEmpty {
                VStack(spacing: 0) {
                    LpmStepIndicator(currentStep: stepIndex + 1, totalSteps: steps.count)
                        .padding(.top, 16)

                    Spacer().frame(height: 16)

                    if stepIndex < steps.count {
                        switch steps[stepIndex] {
                        case .photo:
                            PhotoStep(onPhotoTaken: { stepIndex += 1 })
                        case .pain:
                            PainStep(painLevel: $painLevel, onContinue: { stepIndex += 1 })
                        case .vitals:
                            VitalsStep(
                                rules: rules,
                                tempValue: $tempValue,
                                bpValue: $bpValue,
                                hrValue: $hrValue,
                                onContinue: { stepIndex += 1 }
                            )
                        case .done:
                            DoneStep(isSaving: isSaving, saveError: saveError, onFinish: onComplete)
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
            Spacer()
        }
        .background(Color.white)
        .onAppear {
            var list: [RoutineStepType] = []
            if rules?.photos == true { list.append(.photo) }
            if rules?.pain != false { list.append(.pain) }
            if rules?.temperature == true || rules?.blood_pressure == true || rules?.smartwatch == true {
                list.append(.vitals)
            }
            list.append(.done)
            self.steps = list
        }
        .onChange(of: stepIndex) { newIndex in
            // When we reach the "done" step, post to API
            if newIndex < steps.count && steps[newIndex] == .done {
                saveToTimeline()
            }
        }
    }

    private func saveToTimeline() {
        isSaving = true
        saveError = nil

        Task {
            var lines: [String] = ["Routine Check-in:"]
            if steps.contains(.pain) {
                lines.append("• Pain Level: \(Int(painLevel))/10")
            }
            if !tempValue.isEmpty {
                lines.append("• Temperature: \(tempValue) °C")
            }
            if !bpValue.isEmpty {
                lines.append("• Blood Pressure: \(bpValue) mmHg")
            }
            if !hrValue.isEmpty {
                lines.append("• Heart Rate: \(hrValue) bpm")
            }
            if steps.contains(.photo) {
                lines.append("• Photo: taken")
            }

            let content = lines.joined(separator: "\n")
            let dateLabel = "Routine"

        Task {
            _ = TimelineRepository.addEvent(
                subscriptionId: followUpId,
                request: TimelineEventRequest(
                    content: content,
                    date_label: dateLabel,
                    effective_date: nil
                )
            )
            await MainActor.run { isSaving = false }
        }
        }
    }
}

// MARK: - Steps

private struct PhotoStep: View {
    let onPhotoTaken: () -> Void
    @State private var showCamera = false
    @State private var capturedImage: UIImage? = nil

    var body: some View {
        VStack(spacing: 20) {
            Text("Place the area to track inside the frame.")
                .font(.body)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)

            if let img = capturedImage {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
                    .frame(maxWidth: .infinity, maxHeight: 300)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color(UIColor.systemGray6))
                        .frame(maxWidth: .infinity, maxHeight: 300)

                    VStack {
                        Image(systemName: "camera.viewfinder")
                            .font(.system(size: 48))
                            .foregroundColor(.gray)
                        Text("Tap to open camera")
                            .font(.callout)
                            .foregroundColor(.gray)
                            .padding(.top, 8)
                    }
                }
                .onTapGesture {
                    showCamera = true
                }
            }

            Spacer()

            LpmPrimaryButton(text: capturedImage == nil ? "Skip" : "Continue", action: onPhotoTaken)
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraView(selectedImage: $capturedImage)
                .edgesIgnoringSafeArea(.all)
        }
    }
}

private struct PainStep: View {
    @Binding var painLevel: Double
    let onContinue: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Text("Pain Assessment")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.2)
                .foregroundColor(.gray)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text("Pain level today")
                .font(.title3)
                .fontWeight(.semibold)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack {
                Text("0").foregroundColor(.gray).font(.caption)
                Spacer()
                Text("10").foregroundColor(.gray).font(.caption)
            }

            Slider(value: $painLevel, in: 0...10, step: 1)
                .tint(.black)

            Text("\(Int(painLevel)) / 10")
                .font(.title)
                .fontWeight(.bold)

            Spacer()
            LpmPrimaryButton(text: "Save", action: onContinue)
        }
    }
}

private struct VitalsStep: View {
    let rules: FollowUpRules?
    @Binding var tempValue: String
    @Binding var bpValue: String
    @Binding var hrValue: String
    let onContinue: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Vitals Check-in")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.2)
                    .foregroundColor(.gray)

                if rules?.temperature == true {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Quick Select Temperature")
                            .font(.caption)
                            .foregroundColor(.gray)

                        HStack {
                            ForEach(["36.5", "37.0", "37.5", "38.0"], id: \.self) { temp in
                                Button(action: { tempValue = temp }) {
                                    Text("\(temp)°")
                                        .font(.subheadline)
                                        .padding(.vertical, 8)
                                        .padding(.horizontal, 16)
                                        .background(tempValue == temp ? Color.black : Color(UIColor.systemGray6))
                                        .foregroundColor(tempValue == temp ? .white : .black)
                                        .cornerRadius(20)
                                }
                            }
                        }

                        TextField("Or enter specific (°C)", text: $tempValue)
                            .keyboardType(.decimalPad)
                            .padding()
                            .background(Color(UIColor.systemGray6))
                            .cornerRadius(8)
                    }
                }

                if rules?.blood_pressure == true {
                    TextField("Blood Pressure (mmHg)", text: $bpValue)
                        .padding()
                        .background(Color(UIColor.systemGray6))
                        .cornerRadius(8)
                }

                if rules?.smartwatch == true {
                    TextField("Heart Rate (bpm)", text: $hrValue)
                        .keyboardType(.numberPad)
                        .padding()
                        .background(Color(UIColor.systemGray6))
                        .cornerRadius(8)
                }

                Spacer().frame(height: 40)
                LpmPrimaryButton(text: "Save", action: onContinue)
            }
        }
    }
}

private struct DoneStep: View {
    let isSaving: Bool
    let saveError: String?
    let onFinish: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            if isSaving {
                ProgressView()
                    .scaleEffect(1.5)
                    .padding(.bottom, 8)
                Text("Saving your data...")
                    .font(.body)
                    .foregroundColor(.gray)
            } else {
                Circle()
                    .fill(Color.black)
                    .frame(width: 80, height: 80)
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(.white)
                    )

                Text("Routine saved")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.2)
                    .foregroundColor(.gray)
                    .padding(.top, 16)

                if let err = saveError {
                    Text(err)
                        .font(.caption)
                        .foregroundColor(.orange)
                        .multilineTextAlignment(.center)
                }

                Text("Today's data has been saved. Come back tomorrow for your next routine.")
                    .font(.body)
                    .foregroundColor(.black)
                    .multilineTextAlignment(.center)
            }

            Spacer()

            LpmPrimaryButton(text: "Back to home", action: onFinish)
                .disabled(isSaving)
                .opacity(isSaving ? 0.5 : 1)
            Spacer()
        }
    }
}
