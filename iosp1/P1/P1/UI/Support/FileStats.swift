import Foundation

struct FileReadiness {
    let readinessPercent: Int
    let measurementCount: Int
    let photoCount: Int
    let noteCount: Int
    let expectedMeasurements: Int
    let dayStreak: Int
    let missingHint: String?
    let tagline: String
}

enum FileStats {
    static func compute(followUp: FollowUpUi, events: [TimelineEventResponse]) -> FileReadiness {
        let userEvents = events.filter { $0.type == "user" }
        let measurements = userEvents.filter { !isNoteEvent($0) }
        let measurementCount = measurements.count
        let photoCount = measurements.filter { $0.content.localizedCaseInsensitiveContains("Photo:") }.count
        let noteCount = userEvents.filter { isNoteEvent($0) }.count

        let slotsPerDay = max(followUp.schedule?.count ?? 1, 1)
        let expectedMeasurements = max(followUp.totalDays * slotsPerDay, 1)
        let readinessPercent = min(Int((Float(measurementCount) / Float(expectedMeasurements)) * 100), 100)

        let dayStreak = computeStreak(measurements: measurements)
        let missingHint = buildMissingHint(
            readiness: readinessPercent,
            daysRemaining: followUp.daysRemaining,
            actual: measurementCount,
            expected: expectedMeasurements
        )
        let tagline = dashboardTagline(daysRemaining: followUp.daysRemaining, readiness: readinessPercent)

        return FileReadiness(
            readinessPercent: readinessPercent,
            measurementCount: measurementCount,
            photoCount: photoCount,
            noteCount: noteCount,
            expectedMeasurements: expectedMeasurements,
            dayStreak: dayStreak,
            missingHint: missingHint,
            tagline: tagline
        )
    }

    static func greetingPrefix() -> String {
        let hour = Calendar.current.component(.hour, from: Date())
        if hour < 12 { return "Good morning" }
        if hour < 17 { return "Good afternoon" }
        return "Good evening"
    }

    static func dashboardTagline(daysRemaining: Int, readiness: Int) -> String {
        if daysRemaining == 0 {
            return "Appointment day — your file is ready to share with your doctor."
        }
        if daysRemaining <= 3 && readiness >= 70 {
            return "Almost there. Your doctor will have a clear picture."
        }
        if daysRemaining <= 3 {
            return "Every entry counts — \(daysRemaining) days until your appointment."
        }
        if readiness == 0 {
            return "Start today — your future self will thank you at the appointment."
        }
        if readiness < 40 {
            return "You're building something useful. Keep adding a little each day."
        }
        if readiness < 80 {
            return "Your file is taking shape. Steady progress."
        }
        return "Strong file so far. Stay consistent until appointment day."
    }

    private static func isNoteEvent(_ event: TimelineEventResponse) -> Bool {
        event.date_label.caseInsensitiveCompare("Question") == .orderedSame
    }

    private static func buildMissingHint(
        readiness: Int,
        daysRemaining: Int,
        actual: Int,
        expected: Int
    ) -> String? {
        let remaining = expected - actual
        if daysRemaining == 0 && readiness >= 80 { return nil }
        if daysRemaining == 0 { return "A few more entries will strengthen your file for today." }
        if remaining <= 0 { return nil }
        if readiness >= 90 { return "Almost complete — keep going." }
        if remaining == 1 { return "One more check-in fills a gap in your file." }
        return "\(remaining) check-ins still to capture before your appointment."
    }

    private static func computeStreak(measurements: [TimelineEventResponse]) -> Int {
        guard !measurements.isEmpty else { return 0 }
        let calendar = Calendar.current
        let dates = Set(measurements.compactMap { parseEventDate($0).map { calendar.startOfDay(for: $0) } })
        var streak = 0
        var day = calendar.startOfDay(for: Date())
        while dates.contains(day) {
            streak += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { break }
            day = previous
        }
        return streak
    }

    private static func parseEventDate(_ event: TimelineEventResponse) -> Date? {
        let raw = event.effective_at ?? event.created_at
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: raw) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: raw)
    }
}
