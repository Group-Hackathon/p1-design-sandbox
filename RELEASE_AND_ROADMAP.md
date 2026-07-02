# Pre-Appointment 1 — Release notes & roadmap

## Version 1.0.7 (current)

- **Splash version label** on Android and iOS launch screens (`v1.0.7` from build metadata).
- **Documentation sync** — README, architecture notes, and backend docs aligned with the native dual-codebase MVP.
- **iOS parity** — `local_time` + `timezone` sent to Gemini recommend endpoint (same as Android).
- **PDF footer** uses dynamic app version instead of hardcoded `v1.0`.

---

## Version 1.0.6

- **Notification deep links** — tap opens the correct Journey check-in (`schedule_key`).
- **Home bell** reflects real pending check-ins from timeline, not just time windows.
- **Single API fetch** in `MainActivity` (Dashboard no longer duplicates subscriptions call).
- **Onboarding** — visible errors on failure; manual setup sends `rules` to backend.
- **Reminders** scheduled for all active follow-ups.

---

## Version 1.0.5

- Smart schedule: first check-in adapted to launch time via Gemini + `ScheduleLogic`.
- Editable reminder times from Journey and Notifications screens.
- Onboarding date picker fixes; smartwatch / BP greyed as “coming soon”.
- Backend: `PATCH /subscriptions/{id}` accepts `parameters.schedule`; Gemini receives `local_time` + `timezone`.

---

## Version 1.0.3–1.0.4 (archive)

- Dynamic onboarding pricing model; manual protocol fallback.
- PDF charts (temperature & pain evolution).
- Local push notifications via `AlarmManager` (Android).

---

## Roadmap

### Near term
- Google Play Billing (real SKUs instead of test flow).
- iOS notification deep links and schedule parity with Android.
- Photo upload to user VPC when private backend pairing ships.

### Medium term
- Firebase / Apple Sign-In for account recovery.
- Health Connect (Android) and HealthKit (iOS) integrations.
- Production migration from shared Cloud Run MVP to per-user self-hosted nodes.
