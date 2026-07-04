import SwiftUI
import Combine
struct OnboardingScreen: View {
    let onBack: () -> Void
    let onFollowUpCreated: (String) -> Void
    
    @State private var step = 1
    @State private var symptomText = ""
    @State private var appointmentDate = Calendar.current.date(byAdding: .day, value: 14, to: Date()) ?? Date()
    
    // Tracking rules
    @State private var ruleTemperature = false
    @State private var rulePain = true
    @State private var rulePhotos = true
    @State private var ruleSmartwatch = false
    @State private var ruleBp = false
    
    @State private var isAnalyzing = false
    @State private var generatedTitle = ""
    @State private var generatedPlan = ""
    
    var body: some View {
        VStack(spacing: 0) {
            LpmTopBar(
                title: step == 6 ? "Manual Setup" : "New Tracking",
                onBack: {
                    if step > 1 && step != 4 && step != 5 && step != 6 {
                        withAnimation { step -= 1 }
                    } else if step == 5 || step == 6 {
                        withAnimation { step = 3 }
                    } else {
                        onBack()
                    }
                }
            )
            
            VStack(spacing: 0) {
                if step <= 3 {
                    LpmStepIndicator(currentStep: step, totalSteps: 3)
                        .padding(.top, 16)
                        .padding(.bottom, 28)
                }
                
                // Content Switcher
                Group {
                    switch step {
                    case 1:
                        DescribeStep(
                            symptomText: $symptomText,
                            onNext: { withAnimation { step = 2 } }
                        )
                    case 2:
                        DateStep(
                            date: $appointmentDate,
                            onNext: { withAnimation { step = 3 } }
                        )
                    case 3:
                        RulesStep(
                            ruleTemperature: $ruleTemperature,
                            rulePain: $rulePain,
                            rulePhotos: $rulePhotos,
                            ruleSmartwatch: $ruleSmartwatch,
                            ruleBp: $ruleBp,
                            isLoading: isAnalyzing,
                            onGeneratePlan: {
                                isAnalyzing = true
                                Task {
                                    do {
                                        // Fake analysis delay for the prototype if backend is not linked yet
                                        // Or call ApiService.shared.recommendAgent
                                        let formatter = DateFormatter()
                                        formatter.dateFormat = "HH:mm"
                                        let localTime = formatter.string(from: Date())
                                        let request = RecommendRequest(
                                            symptoms: symptomText,
                                            appointment_date: ISO8601DateFormatter().string(from: appointmentDate),
                                            rules: TrackingRulesDto(
                                                temperature: ruleTemperature,
                                                pain: rulePain,
                                                photos: rulePhotos,
                                                smartwatch: ruleSmartwatch,
                                                blood_pressure: ruleBp,
                                                custom: ""
                                            ),
                                            local_time: localTime,
                                            timezone: TimeZone.current.identifier
                                        )
                                        let response = try await ApiService.shared.recommendAgent(request: request)
                                        
                                        await MainActor.run {
                                            generatedTitle = response.name
                                            generatedPlan = response.description
                                            withAnimation { step = 4 }
                                            isAnalyzing = false
                                        }
                                    } catch {
                                        print("Error generating plan: \(error)")
                                        await MainActor.run {
                                            isAnalyzing = false
                                            withAnimation { step = 5 } // Fallback
                                        }
                                    }
                                }
                            }
                        )
                    case 4:
                        PremiumPreviewStep(
                            title: generatedTitle,
                            planText: generatedPlan,
                            appointmentDate: appointmentDate,
                            isLoading: isAnalyzing,
                            onConfirm: {
                                isAnalyzing = true
                                Task {
                                    do {
                                        let duration = Calendar.current.dateComponents([.day], from: Date(), to: appointmentDate).day ?? 14
                                        let params = SubscriptionRequestParams(
                                            title: generatedTitle,
                                            symptoms: symptomText,
                                            next_appointment: ISO8601DateFormatter().string(from: appointmentDate),
                                            plan: generatedPlan,
                                            rules: TrackingRulesDto(
                                                temperature: ruleTemperature,
                                                pain: rulePain,
                                                photos: rulePhotos,
                                                smartwatch: ruleSmartwatch,
                                                blood_pressure: ruleBp,
                                                custom: ""
                                            )
                                        )
                                        let req = SubscriptionRequest(
                                            profile_id: SessionManager.shared.getProfileId() ?? "",
                                            agent_id: "dynamic-plan",
                                            duration_days: max(1, duration),
                                            private_backend_url: nil,
                                            parameters: params
                                        )
                                        let sub = try await ApiService.shared.createSubscription(request: req)
                                        FollowUpRepository.saveFromRemote(sub)
                                        await MainActor.run {
                                            isAnalyzing = false
                                            onFollowUpCreated(sub.id)
                                        }
                                    } catch {
                                        print("Create subscription failed: \(error)")
                                        await MainActor.run { isAnalyzing = false }
                                    }
                                }
                            },
                            onManualSetup: { withAnimation { step = 6 } }
                        )
                    case 5:
                        OfflineFallbackStep(
                            onRetry: { withAnimation { step = 3 } },
                            onManualSetup: { withAnimation { step = 6 } }
                        )
                    case 6:
                        ManualEntryStep(
                            appointmentDate: appointmentDate,
                            isLoading: isAnalyzing,
                            onConfirm: { title, schedule in
                                isAnalyzing = true
                                Task {
                                    do {
                                        let duration = Calendar.current.dateComponents([.day], from: Date(), to: appointmentDate).day ?? 14
                                        let params = SubscriptionRequestParams(
                                            title: title,
                                            symptoms: symptomText,
                                            next_appointment: ISO8601DateFormatter().string(from: appointmentDate),
                                            plan: "Manual custom tracking",
                                            schedule: schedule
                                        )
                                        let req = SubscriptionRequest(
                                            profile_id: SessionManager.shared.getProfileId() ?? "",
                                            agent_id: "dynamic-plan",
                                            duration_days: max(1, duration),
                                            private_backend_url: nil,
                                            parameters: params
                                        )
                                        let sub = try await ApiService.shared.createSubscription(request: req)
                                        FollowUpRepository.saveFromRemote(sub)
                                        await MainActor.run {
                                            isAnalyzing = false
                                            onFollowUpCreated(sub.id)
                                        }
                                    } catch {
                                        print("Create manual tracking failed: \(error)")
                                        await MainActor.run { isAnalyzing = false }
                                    }
                                }
                            }
                        )
                    default:
                        EmptyView()
                    }
                }
                .transition(.asymmetric(
                    insertion: .opacity.combined(with: .move(edge: .trailing)),
                    removal: .opacity.combined(with: .move(edge: .leading))
                ))
            }
            .padding(.horizontal, 20)
        }
        .background(Color.white)
    }
}

// MARK: - Sub Steps

private struct DescribeStep: View {
    @Binding var symptomText: String
    let onNext: () -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            LpmSectionTitle(title: "What would you like to track?")
                .padding(.bottom, 8)
            LpmBodyText(text: "Describe your situation in a few sentences.")
                .padding(.bottom, 16)
            
            TextEditor(text: $symptomText)
                .frame(minHeight: 160)
                .padding(8)
                .background(Color.white)
                .cornerRadius(4)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(symptomText.isEmpty ? Color(UIColor.systemGray4) : Color.black, lineWidth: 1)
                )
            
            Spacer()
            
            LpmPrimaryButton(text: "Continue", action: onNext)
                .disabled(symptomText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .opacity(symptomText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.5 : 1)
                .padding(.bottom, 24)
        }
    }
}

private struct DateStep: View {
    @Binding var date: Date
    let onNext: () -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            LpmSectionTitle(title: "When is your appointment?")
                .padding(.bottom, 8)
            LpmBodyText(text: "Select the exact date of your next medical checkup.")
                .padding(.bottom, 32)
            
            DatePicker(
                "Appointment Date",
                selection: $date,
                displayedComponents: [.date]
            )
            .datePickerStyle(.graphical)
            .tint(.black)
            .padding()
            .background(Color.white)
            .cornerRadius(12)
            .shadow(color: Color.black.opacity(0.05), radius: 10, x: 0, y: 5)
            
            Spacer()
            
            LpmPrimaryButton(text: "Continue", action: onNext)
                .padding(.bottom, 24)
        }
    }
}

private struct RulesStep: View {
    @Binding var ruleTemperature: Bool
    @Binding var rulePain: Bool
    @Binding var rulePhotos: Bool
    @Binding var ruleSmartwatch: Bool
    @Binding var ruleBp: Bool
    let isLoading: Bool
    let onGeneratePlan: () -> Void
    
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                LpmSectionTitle(title: "Your tracking rules")
                    .padding(.bottom, 8)
                LpmBodyText(text: "Choose the health metrics you want to monitor.")
                    .padding(.bottom, 24)
                
                VStack(spacing: 12) {
                    RuleToggle(title: "Temperature", subtitle: "Manual entries", isOn: $ruleTemperature)
                    RuleToggle(title: "Pain tracking", subtitle: "Daily pain level", isOn: $rulePain)
                    RuleToggle(title: "Daily photos", subtitle: "Guided photo capture", isOn: $rulePhotos)
                    RuleToggle(title: "Smartwatch data", subtitle: "Heart rate, steps", isOn: $ruleSmartwatch)
                    RuleToggle(title: "Blood pressure", subtitle: "Manual or connected", isOn: $ruleBp)
                }
                
                Spacer().frame(height: 32)
                
                if isLoading {
                    ProgressView()
                        .padding()
                } else {
                    LpmPrimaryButton(text: "Prepare my protocol", action: onGeneratePlan)
                }
                Spacer().frame(height: 24)
            }
        }
    }
}

private struct RuleToggle: View {
    let title: String
    let subtitle: String
    @Binding var isOn: Bool
    
    var body: some View {
        LpmCard {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                        .fontWeight(.bold)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundColor(Color(UIColor.systemGray))
                }
                Spacer()
                Toggle("", isOn: $isOn)
                    .tint(.black)
            }
            .padding(16)
        }
    }
}

private struct PremiumPreviewStep: View {
    let title: String
    let planText: String
    let appointmentDate: Date
    let isLoading: Bool
    let onConfirm: () -> Void
    let onManualSetup: () -> Void
    
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                    Text("Analyzed from thousands of medical records")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.green)
                }
                .padding(.bottom, 12)
                
                Text(title.isEmpty ? "Your Personal Plan" : title)
                    .font(.title)
                    .fontWeight(.black)
                    .padding(.bottom, 8)
                
                Text("Here is the optimal tracking program until your appointment.")
                    .font(.body)
                    .foregroundColor(Color(UIColor.systemGray))
                    .padding(.bottom, 24)
                
                // Plan display
                VStack(alignment: .leading, spacing: 8) {
                    let lines = planText.components(separatedBy: "\n").filter { !$0.isEmpty }
                    ForEach(lines, id: \.self) { line in
                        let cleanLine = line.replacingOccurrences(of: "- ", with: "").replacingOccurrences(of: "* ", with: "")
                        HStack(alignment: .top) {
                            Text("•")
                                .fontWeight(.bold)
                            Text(cleanLine)
                                .font(.body)
                                .foregroundColor(Color(UIColor.systemGray))
                        }
                    }
                }
                .padding(20)
                .background(Color(UIColor.systemGray6))
                .cornerRadius(12)
                
                Spacer().frame(height: 32)
                
                LpmPrimaryButton(text: "Start Tracking (Free Beta)", action: onConfirm)
                
                Spacer().frame(height: 16)
                
                Button(action: onManualSetup) {
                    Text("Or create my planning manually")
                        .font(.callout)
                        .foregroundColor(Color(UIColor.systemGray))
                        .frame(maxWidth: .infinity)
                }
                .padding(.bottom, 24)
            }
        }
    }
}

private struct OfflineFallbackStep: View {
    let onRetry: () -> Void
    let onManualSetup: () -> Void
    
    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "exclamationmark.triangle.fill")
                .resizable()
                .frame(width: 64, height: 64)
                .foregroundColor(.orange)
            
            Text("Connection Unavailable")
                .font(.title2)
                .fontWeight(.bold)
            
            Text("We cannot analyze your symptoms at the moment to propose the optimal tracking protocol.")
                .font(.body)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            
            Spacer()
            
            LpmSecondaryButton(text: "Try again", action: onRetry)
            LpmPrimaryButton(text: "Proceed with manual setup", action: onManualSetup)
        }
        .padding(.bottom, 24)
    }
}

private struct ManualCheckbox: View {
    let label: String
    @Binding var isChecked: Bool
    
    var body: some View {
        HStack {
            Button(action: { isChecked.toggle() }) {
                Image(systemName: isChecked ? "checkmark.square.fill" : "square")
                    .foregroundColor(isChecked ? .black : .gray)
            }
            Text(label)
                .font(.caption)
                .foregroundColor(.gray)
        }
    }
}

class DayConfig: ObservableObject, Identifiable {
    let id = UUID()
    @Published var mornPain = false
    @Published var mornTemp = false
    @Published var mornPhoto = false
    
    @Published var noonPain = false
    @Published var noonTemp = false
    @Published var noonPhoto = false
    
    @Published var evePain = false
    @Published var eveTemp = false
    @Published var evePhoto = false
}

private struct ManualEntryStep: View {
    let appointmentDate: Date
    let isLoading: Bool
    let onConfirm: (String, [String: [String]]) -> Void
    
    @State private var trackingTitle = ""
    @State private var dayConfigs: [DayConfig] = []
    
    init(appointmentDate: Date, isLoading: Bool, onConfirm: @escaping (String, [String: [String]]) -> Void) {
        self.appointmentDate = appointmentDate
        self.isLoading = isLoading
        self.onConfirm = onConfirm
        
        let days = Calendar.current.dateComponents([.day], from: Date(), to: appointmentDate).day ?? 14
        let duration = max(1, days)
        var configs: [DayConfig] = []
        for _ in 0..<duration {
            configs.append(DayConfig())
        }
        self._dayConfigs = State(initialValue: configs)
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Manual Tracking Setup")
                .font(.title)
                .fontWeight(.black)
                .padding(.bottom, 8)
            
            Text("Please manually configure your tracking requirements for every single day until your appointment (\(dayConfigs.count) days).")
                .font(.body)
                .foregroundColor(.gray)
                .padding(.bottom, 16)
            
            TextField("Tracking Name (e.g. My Custom Tracking)", text: $trackingTitle)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .padding(.bottom, 16)
            
            ScrollView {
                LazyVStack(spacing: 16) {
                    ForEach(Array(dayConfigs.enumerated()), id: \.element.id) { index, config in
                        DayConfigCard(index: index, config: config)
                    }
                }
            }
            
            LpmPrimaryButton(text: "Create Free Tracking", action: {
                var schedule = [String: [String]]()
                var mornSet = Set<String>()
                var noonSet = Set<String>()
                var eveSet = Set<String>()
                
                for config in dayConfigs {
                    if config.mornPain { mornSet.insert("pain") }
                    if config.mornTemp { mornSet.insert("temperature") }
                    if config.mornPhoto { mornSet.insert("photo") }
                    
                    if config.noonPain { noonSet.insert("pain") }
                    if config.noonTemp { noonSet.insert("temperature") }
                    if config.noonPhoto { noonSet.insert("photo") }
                    
                    if config.evePain { eveSet.insert("pain") }
                    if config.eveTemp { eveSet.insert("temperature") }
                    if config.evePhoto { eveSet.insert("photo") }
                }
                
                if !mornSet.isEmpty { schedule["08:00"] = Array(mornSet) }
                if !noonSet.isEmpty { schedule["12:00"] = Array(noonSet) }
                if !eveSet.isEmpty { schedule["20:00"] = Array(eveSet) }
                
                onConfirm(trackingTitle, schedule)
            })
            .disabled(trackingTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isLoading)
            .padding(.vertical, 16)
        }
    }
}

private struct DayConfigCard: View {
    let index: Int
    @ObservedObject var config: DayConfig
    
    var body: some View {
        let dayDate = Calendar.current.date(byAdding: .day, value: index, to: Date()) ?? Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE, MMM d"
        formatter.locale = Locale(identifier: "en_US")
        let formattedDate = formatter.string(from: dayDate)
        
        return LpmCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Day \(index + 1) — \(formattedDate)")
                    .font(.headline)
                    .fontWeight(.bold)
                
                Divider()
                
                Text("Morning (08:00)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                HStack {
                    ManualCheckbox(label: "Pain", isChecked: $config.mornPain)
                    Spacer()
                    ManualCheckbox(label: "Temp", isChecked: $config.mornTemp)
                    Spacer()
                    ManualCheckbox(label: "Photo", isChecked: $config.mornPhoto)
                }
                
                Text("Noon (12:00)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .padding(.top, 4)
                HStack {
                    ManualCheckbox(label: "Pain", isChecked: $config.noonPain)
                    Spacer()
                    ManualCheckbox(label: "Temp", isChecked: $config.noonTemp)
                    Spacer()
                    ManualCheckbox(label: "Photo", isChecked: $config.noonPhoto)
                }
                
                Text("Evening (20:00)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .padding(.top, 4)
                HStack {
                    ManualCheckbox(label: "Pain", isChecked: $config.evePain)
                    Spacer()
                    ManualCheckbox(label: "Temp", isChecked: $config.eveTemp)
                    Spacer()
                    ManualCheckbox(label: "Photo", isChecked: $config.evePhoto)
                }
            }
            .padding(16)
        }
    }
}
