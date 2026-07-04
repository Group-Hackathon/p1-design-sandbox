import SwiftUI

struct JourneyScreen: View {
    let followUp: FollowUpUi
    let onOpenDrawer: () -> Void
    let onOpenReport: () -> Void
    let onStartRoutine: () -> Void

    @State private var events: [TimelineEventResponse] = []
    @State private var chatText = ""
    @State private var isSending = false
    @State private var currentFollowUp: FollowUpUi
    @State private var isLoading = true
    @State private var isMeasurementWindow = false
    @State private var activePeriodName = ""
    
    // Menu & dialogs
    @State private var showDatePicker = false
    @State private var showDeleteConfirm = false
    @State private var pickedDate: Date = Date()
    
    // Delete event
    @State private var eventToDelete: TimelineEventResponse? = nil
    @State private var showDeleteEventConfirm = false
    
    // Missed measurement
    @State private var showMissedForm = false
    @State private var missedEffectiveDate: Date? = nil

    init(followUp: FollowUpUi, onOpenDrawer: @escaping () -> Void, onOpenReport: @escaping () -> Void, onStartRoutine: @escaping () -> Void) {
        self.followUp = followUp
        self.onOpenDrawer = onOpenDrawer
        self.onOpenReport = onOpenReport
        self.onStartRoutine = onStartRoutine
        _currentFollowUp = State(initialValue: followUp)
    }

    // Compute % complete from real events
    private var completionPercent: Int {
        let expected = max(1, currentFollowUp.totalDays)
        let actual = events.filter { $0.type == "user" && !$0.date_label.contains("Question") }.count
        return min(Int((Float(actual) / Float(expected)) * 100), 100)
    }
    
    private var appointmentDateString: String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = formatter.date(from: currentFollowUp.expiresAt) {
            let df = DateFormatter()
            df.dateFormat = "d MMM"
            return df.string(from: d)
        }
        return "—"
    }
    
    private var startDate: Date {
        let fmt = ISO8601DateFormatter()
        fmt.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return fmt.date(from: currentFollowUp.startsAt) ?? Date()
    }
    
    // Build mixed timeline: past events + future/past day placeholders
    private enum TimelineItem: Identifiable {
        case event(TimelineEventResponse)
        case futureDay(dayNumber: Int, date: Date, isPast: Bool)
        var id: String {
            switch self {
            case .event(let e): return "event-\(e.id)"
            case .futureDay(let d, _, _): return "day-\(d)"
            }
        }
    }
    
    private var timelineItems: [TimelineItem] {
        var items: [TimelineItem] = []
        // Past events (user + ai pairs)
        var i = 0
        let sorted = events.sorted { ($0.effective_at ?? $0.created_at) < ($1.effective_at ?? $1.created_at) }
        while i < sorted.count {
            items.append(.event(sorted[i]))
            i += 1
        }
        // Future & past days without data
        let daysDone = currentFollowUp.totalDays - currentFollowUp.daysRemaining
        let startFuture = max(daysDone + 1, 1)
        let cal = Calendar.current
        for d in startFuture...max(startFuture, currentFollowUp.totalDays) {
            let dayDate = cal.date(byAdding: .day, value: d - 1, to: startDate) ?? Date()
            let isPast = dayDate < Date()
            items.append(.futureDay(dayNumber: d, date: dayDate, isPast: isPast))
        }
        return items
    }

    var body: some View {
        ZStack {
            Color.white.edgesIgnoringSafeArea(.all)

            VStack(spacing: 0) {
                // Top Bar
                HStack {
                    Button(action: onOpenDrawer) {
                        Image(systemName: "line.3.horizontal")
                            .font(.title2)
                            .foregroundColor(.black)
                    }
                    Spacer()
                    Text(currentFollowUp.title)
                        .font(.headline)
                        .fontWeight(.bold)
                    Spacer()
                    // Report + overflow menu
                    HStack(spacing: 4) {
                        Button(action: onOpenReport) {
                            Image(systemName: "doc.text")
                                .font(.title3)
                                .foregroundColor(.black)
                        }
                        Menu {
                            Button(String(localized: "menu_change_appt_date")) {
                                // Init picker to current expiry
                                let fmt = ISO8601DateFormatter()
                                fmt.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                                pickedDate = fmt.date(from: currentFollowUp.expiresAt) ?? Date()
                                showDatePicker = true
                            }
                            Button(role: .destructive) {
                                showDeleteConfirm = true
                            } label: {
                                Label(String(localized: "menu_delete_tracking"), systemImage: "trash")
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                                .font(.title3)
                                .foregroundColor(.black)
                                .padding(.leading, 4)
                        }
                    }
                }
                .padding()
                .background(Color.white)

                ScrollView {
                    VStack(spacing: 0) {
                        // Summary Card (dynamic)
                        JourneySummaryCard(
                            followUp: currentFollowUp,
                            events: events,
                            appointmentDateStr: appointmentDateString
                        )
                        .padding(.vertical, 16)

                        // Timeline: events + future/past day rows
                        if timelineItems.isEmpty {
                            EmptyStateTimeline()
                        } else {
                            VStack(spacing: 0) {
                                ForEach(timelineItems) { item in
                                    switch item {
                                    case .event(let event):
                                        TimelineEventRow(
                                            event: event,
                                            isLast: false,
                                            onLongPress: {
                                                if event.type == "user" {
                                                    eventToDelete = event
                                                    showDeleteEventConfirm = true
                                                }
                                            }
                                        )
                                    case .futureDay(let day, let date, let isPast):
                                        FutureDayRow(
                                            dayNumber: day,
                                            date: date,
                                            isPast: isPast,
                                            onAddMissed: isPast ? {
                                                missedEffectiveDate = date
                                                withAnimation { showMissedForm = true }
                                            } : nil
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                BottomInput(
                    text: $chatText,
                    isSending: isSending,
                    isMeasurementWindow: isMeasurementWindow,
                    isLoading: isLoading,
                    periodName: activePeriodName,
                    events: events,
                    onStartRoutine: onStartRoutine,
                    onSendQuestion: {
                        guard !chatText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                        Task { await sendQuestion() }
                    }
                )
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            
            // Missed Measurement bottom sheet
            if showMissedForm {
                Color.black.opacity(0.4)
                    .edgesIgnoringSafeArea(.all)
                    .onTapGesture { showMissedForm = false }
                
                VStack {
                    Spacer()
                    MissedMeasurementForm(
                        effectiveDate: missedEffectiveDate ?? Date(),
                        followUpId: currentFollowUp.id,
                        onClose: {
                            showMissedForm = false
                            Task { await reloadTimeline() }
                        }
                    )
                }
                .edgesIgnoringSafeArea(.bottom)
                .transition(.move(edge: .bottom))
                .animation(.spring(response: 0.35), value: showMissedForm)
            }
        }
        .onChange(of: followUp) { newValue in
            currentFollowUp = newValue
            Task { await reloadTimeline() }
        }
        .task {
            await MainActor.run {
                events = TimelineRepository.getEvents(subscriptionId: currentFollowUp.id)
                isLoading = false
            }
            await reloadTimeline()
        }
        // Auto-refresh every 3s
        .task {
            do {
                while !Task.isCancelled {
                    try await Task.sleep(nanoseconds: 3_000_000_000)
                    if Task.isCancelled { break }
                    await reloadTimeline()
                }
            } catch {}
        }
        // Time evaluation loop for milestones
        .task {
            do {
                while !Task.isCancelled {
                    updateMeasurementWindow()
                    try await Task.sleep(nanoseconds: 1_000_000_000)
                }
            } catch {}
        }
        // Date picker sheet
        .sheet(isPresented: $showDatePicker) {
            NavigationView {
                VStack {
                    DatePicker(
                        "New appointment date",
                        selection: $pickedDate,
                        displayedComponents: [.date]
                    )
                    .datePickerStyle(.graphical)
                    .tint(.black)
                    .padding()
                    Spacer()
                }
                .navigationTitle("Change appointment date")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showDatePicker = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Confirm") {
                            showDatePicker = false
                            Task { await changeAppointmentDate(to: pickedDate) }
                        }
                        .foregroundColor(.black)
                        .fontWeight(.bold)
                    }
                }
            }
        }
        // Delete tracking confirm
        .alert(String(localized: "dialog_delete_tracking_title"), isPresented: $showDeleteConfirm) {
            Button("Delete", role: .destructive) {
                Task { await deleteTracking() }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(String(localized: "dialog_delete_tracking_desc"))
        }
        // Delete event confirm
        .alert(String(localized: "dialog_delete_record_title"), isPresented: $showDeleteEventConfirm) {
            Button("Delete", role: .destructive) {
                if let ev = eventToDelete {
                    Task { await deleteEvent(ev.id) }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(String(localized: "dialog_delete_record_desc"))
        }
    }

    private func reloadTimeline() async {
        _ = await TimelineRepository.refreshFromRemote(subscriptionId: currentFollowUp.id)
        await MainActor.run {
            events = TimelineRepository.getEvents(subscriptionId: currentFollowUp.id)
            isLoading = false
        }
    }

    private func sendQuestion() async {
        isSending = true
        let request = TimelineEventRequest(content: chatText, date_label: "Question", effective_date: nil)
        _ = TimelineRepository.addEvent(subscriptionId: currentFollowUp.id, request: request)
        await MainActor.run {
            events = TimelineRepository.getEvents(subscriptionId: currentFollowUp.id)
            chatText = ""
        }
        isSending = false
    }
    
    private func deleteEvent(_ id: String) async {
        await TimelineRepository.deleteEvent(subscriptionId: currentFollowUp.id, eventId: id)
        await MainActor.run {
            events = TimelineRepository.getEvents(subscriptionId: currentFollowUp.id)
        }
    }
    
    private func changeAppointmentDate(to date: Date) async {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        let dateStr = fmt.string(from: date)
        do {
            let updated = try await ApiService.shared.patchSubscription(
                id: currentFollowUp.id,
                request: UpdateSubscriptionRequest(expires_at: dateStr)
            )
            FollowUpRepository.saveFromRemote(updated)
            let agents = (try? await ApiService.shared.getAgents()) ?? []
            await MainActor.run {
                currentFollowUp = FollowUpRepository.followUpUi(from: updated, agents: agents)
            }
        } catch {
            print("Change appointment failed: \(error)")
        }
    }
    
    private func deleteTracking() async {
        do {
            try await ApiService.shared.deleteSubscription(id: currentFollowUp.id)
        } catch {
            print("Delete tracking remote failed: \(error)")
        }
        FollowUpRepository.deleteLocal(id: currentFollowUp.id)
        await MainActor.run { onOpenDrawer() }
    }

    private func updateMeasurementWindow() {
        guard let schedule = currentFollowUp.schedule else {
            if isMeasurementWindow != false { isMeasurementWindow = false }
            return
        }
        
        let now = Date()
        let cal = Calendar.current
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "HH:mm"
        
        let currentString = timeFormatter.string(from: now)
        guard let currentTime = timeFormatter.date(from: currentString) else { return }
        
        var isWindow = false
        var activeName = ""
        
        for (period, times) in schedule {
            for timeStr in times {
                guard let time = timeFormatter.date(from: timeStr) else { continue }
                
                let windowEnd = cal.date(byAdding: .hour, value: 4, to: time)!
                
                let crossesMidnight = windowEnd < time
                if crossesMidnight {
                    if currentTime >= time || currentTime < windowEnd {
                        isWindow = true
                        activeName = period
                        break
                    }
                } else {
                    if currentTime >= time && currentTime < windowEnd {
                        isWindow = true
                        activeName = period
                        break
                    }
                }
            }
            if isWindow { break }
        }
        
        if isMeasurementWindow != isWindow {
            isMeasurementWindow = isWindow
        }
        if activePeriodName != activeName {
            activePeriodName = activeName
        }
    }
}

// MARK: - Future / Past Day Row

private struct FutureDayRow: View {
    let dayNumber: Int
    let date: Date
    let isPast: Bool
    let onAddMissed: (() -> Void)?

    private var dateLabel: String {
        let df = DateFormatter()
        df.dateFormat = "EEEE, MMM d"
        return df.string(from: date)
    }

    var body: some View {
        HStack(spacing: 12) {
            // Central dot on line
            VStack(spacing: 0) {
                Rectangle()
                    .fill(Color(UIColor.systemGray4))
                    .frame(width: 2, height: 20)
                Circle()
                    .strokeBorder(Color(UIColor.systemGray4), lineWidth: 2)
                    .frame(width: 10, height: 10)
                Rectangle()
                    .fill(Color(UIColor.systemGray4))
                    .frame(width: 2, height: 20)
            }
            .padding(.leading, 35)

            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Day \(dayNumber) — \(dateLabel)")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(isPast ? Color(UIColor.systemGray) : Color(UIColor.systemGray3))
                    Text("Scheduled tracking")
                        .font(.caption2)
                        .foregroundColor(Color(UIColor.systemGray4))
                }
                Spacer()
                if isPast, let onAdd = onAddMissed {
                    Button(action: onAdd) {
                        Text("+ Add")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.black)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color(UIColor.systemGray6))
                            .cornerRadius(8)
                    }
                }
            }
            .padding(.vertical, 8)
            .padding(.trailing, 20)
            .opacity(isPast ? 0.7 : 0.4)
        }
    }
}

// MARK: - Summary Card (dynamic)


private struct JourneySummaryCard: View {
    let followUp: FollowUpUi
    let events: [TimelineEventResponse]
    let appointmentDateStr: String

    private var readiness: FileReadiness {
        FileStats.compute(followUp: followUp, events: events)
    }

    var body: some View {
        LpmCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(readiness.tagline)
                    .font(.caption)
                    .foregroundColor(Color(UIColor.systemGray))
                    .fixedSize(horizontal: false, vertical: true)

                HStack {
                    VStack {
                        Text(String(localized: "summary_appt_date"))
                            .font(.caption)
                            .foregroundColor(.gray)
                        Text(appointmentDateStr)
                            .font(.title3)
                            .fontWeight(.bold)
                    }
                    Spacer()
                    VStack {
                        Text(String(localized: "summary_days_left"))
                            .font(.caption)
                            .foregroundColor(.gray)
                        Text("\(followUp.daysRemaining)")
                            .font(.title3)
                            .fontWeight(.bold)
                    }
                    Spacer()
                    VStack {
                        Text(String(localized: "summary_complete"))
                            .font(.caption)
                            .foregroundColor(.gray)
                        Text("\(readiness.readinessPercent)%")
                            .font(.title3)
                            .fontWeight(.bold)
                    }
                }

                ProgressView(value: Double(readiness.readinessPercent), total: 100)
                    .tint(.black)

                Text(String(
                    format: String(localized: "summary_file_contents"),
                    readiness.measurementCount,
                    readiness.photoCount,
                    readiness.noteCount
                ))
                .font(.caption)
                .foregroundColor(Color(UIColor.systemGray))

                if readiness.dayStreak >= 2 {
                    Text(String(format: String(localized: "file_streak"), readiness.dayStreak))
                        .font(.caption)
                        .fontWeight(.semibold)
                }

                if let hint = readiness.missingHint {
                    Text(hint)
                        .font(.caption)
                        .fontWeight(.medium)
                }
            }
            .padding()
        }
        .padding(.horizontal, 20)
    }
}

private struct EmptyStateTimeline: View {
    var body: some View {
        Text(String(localized: "empty_state_welcome"))
            .font(.body)
            .foregroundColor(.gray)
            .multilineTextAlignment(.center)
            .padding()
            .background(Color.white)
            .cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(UIColor.systemGray5), lineWidth: 1))
            .padding(.horizontal, 20)
            .padding(.top, 24)
    }
}

private struct TimelineEventRow: View {
    let event: TimelineEventResponse
    let isLast: Bool
    let onLongPress: () -> Void

    private var timeLabel: String {
        let fmt = ISO8601DateFormatter()
        fmt.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = fmt.date(from: event.effective_at ?? event.created_at) {
            let df = DateFormatter()
            df.dateFormat = "HH:mm"
            return df.string(from: d)
        }
        return ""
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(spacing: 0) {
                ZStack {
                    Circle()
                        .fill(event.type == "ai" ? Color.blue : Color.black)
                        .frame(width: 32, height: 32)
                    Image(systemName: event.type == "ai" ? "sparkles" : "person.fill")
                        .foregroundColor(.white)
                        .font(.system(size: 14, weight: .bold))
                }
                if !isLast {
                    Rectangle()
                        .fill(Color(UIColor.systemGray4))
                        .frame(width: 2)
                        .padding(.vertical, 4)
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                let label = event.date_label.uppercased()
                Text(timeLabel.isEmpty ? label : "\(label) • \(timeLabel)")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(.gray)

                Text(event.content)
                    .font(.subheadline)
                    .padding(14)
                    .background(event.type == "ai" ? Color.blue.opacity(0.1) : Color(UIColor.systemGray6))
                    .foregroundColor(.black)
                    .clipShape(
                        UnevenRoundedRectangle(
                            topLeadingRadius: event.type == "ai" ? 4 : 16,
                            bottomLeadingRadius: 16,
                            bottomTrailingRadius: 16,
                            topTrailingRadius: event.type == "ai" ? 16 : 4
                        )
                    )
                    .onLongPressGesture { onLongPress() }
            }
            .padding(.bottom, isLast ? 24 : 16)

            Spacer()
        }
        .padding(.horizontal, 20)
    }
}

struct BottomInput: View {
    @Binding var text: String
    let isSending: Bool
    let isMeasurementWindow: Bool
    let isLoading: Bool
    let periodName: String
    let events: [TimelineEventResponse]
    let onStartRoutine: () -> Void
    let onSendQuestion: () -> Void

    var body: some View {
        VStack {
            let isInitial = !isLoading && events.isEmpty
            if isInitial || isMeasurementWindow {
                Button(action: onStartRoutine) {
                    Text(String(localized: "btn_fill_measurements") + (periodName.isEmpty ? "" : " (\(periodName))"))
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.black)
                        .cornerRadius(12)
                }
                .padding(.horizontal)
                .padding(.bottom, 8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.easeInOut, value: isMeasurementWindow)
            }

            HStack {
                TextField(String(localized: "chat_placeholder"), text: $text)
                    .padding()
                    .background(Color(UIColor.systemGray6))
                    .cornerRadius(24)

                Button(action: onSendQuestion) {
                    if isSending {
                        ProgressView()
                    } else {
                        Image(systemName: "paperplane.fill")
                            .foregroundColor(.black)
                    }
                }
                .padding(.horizontal, 8)
            }
            .padding(.horizontal)
            .padding(.bottom, 16)
        }
        .background(Color.white.shadow(radius: 10))
    }
}

// MARK: - Missed Measurement Form

private struct MissedMeasurementForm: View {
    let effectiveDate: Date
    let followUpId: String
    let onClose: () -> Void
    
    @State private var painLevel: Double = 5
    @State private var tempValue = ""
    @State private var isSaving = false
    
    private var dateLabel: String {
        let df = DateFormatter()
        df.dateFormat = "EEEE, MMM d"
        return "Retroactive - \(df.string(from: effectiveDate))"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            // Handle
            RoundedRectangle(cornerRadius: 3)
                .fill(Color(UIColor.systemGray4))
                .frame(width: 40, height: 5)
                .frame(maxWidth: .infinity)
                .padding(.top, 12)
            
            Text("Add missed measurement")
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, 24)
            
            Text(dateLabel)
                .font(.subheadline)
                .foregroundColor(.gray)
                .padding(.horizontal, 24)
            
            VStack(alignment: .leading, spacing: 16) {
                Text("Pain level: \(Int(painLevel))/10")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                Slider(value: $painLevel, in: 0...10, step: 1).tint(.black)
                
                TextField("Temperature (°C) - optional", text: $tempValue)
                    .keyboardType(.decimalPad)
                    .padding()
                    .background(Color(UIColor.systemGray6))
                    .cornerRadius(8)
            }
            .padding(.horizontal, 24)
            
            if isSaving {
                ProgressView().frame(maxWidth: .infinity).padding()
            } else {
                Button(action: saveMissed) {
                    Text("Save")
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.black)
                        .cornerRadius(12)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }
        }
        .background(Color.white)
        .cornerRadius(24, corners: [.topLeft, .topRight])
    }
    
    private func saveMissed() {
        isSaving = true
        var lines = ["Routine Check-in (\(dateLabel)):"]
        lines.append("• Pain Level: \(Int(painLevel))/10")
        if !tempValue.isEmpty { lines.append("• Temperature: \(tempValue) °C") }
        let content = lines.joined(separator: "\n")
        
        Task {
            let df = DateFormatter()
            df.dateFormat = "yyyy-MM-dd"
            let effectiveDateStr = df.string(from: effectiveDate)
            _ = TimelineRepository.addEvent(
                subscriptionId: followUpId,
                request: TimelineEventRequest(
                    content: content,
                    date_label: dateLabel,
                    effective_date: effectiveDateStr
                )
            )
            await MainActor.run { onClose() }
        }
    }
}

// Helper extension for corner radius on specific corners
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}
