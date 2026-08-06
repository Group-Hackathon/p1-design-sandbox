# Pre-Appointment 1

**P1** is a mobile app agent that helps you track your symptoms before your appointment — in small, simple daily steps — so you don't walk in unprepared.

Think of it as building a **"file for your doctor"** between today and appointment day.

And if memory gaps, stress, or a physical or mental condition make it hard to recall symptoms accurately, P1 keeps a reliable, day-by-day record — so your doctor gets the facts.

**Current release:** [v1.0.7](https://github.com/Group-Hackathon/p1/releases/latest) · Android (primary) · iOS (TestFlight-ready clone)

---

## The problem

When a patient finally sits in front of a doctor, most of the useful information is already gone. Symptoms that appeared three weeks ago are half-forgotten, the evolution of a wound or a rash exists only in memory, fever curves were never written down, and the few minutes of consultation are spent reconstructing history instead of making decisions.

Healthcare data is fragmented, and the most valuable part of it — what happens to the patient between two appointments — is almost never captured.

---

## The mental health benefit

Pre-Appointment 1 (P1) turns your daily symptoms into undeniable, medically useful facts. By tracking your condition in small, simple steps each day, it builds a foolproof record for your next visit.

Don't just describe your symptoms from memory. Walk in with a clear, objective timeline so your doctor gets the full story, takes your pain seriously, and gives you the exact care you need.

---

## What we are building


**Pre-Appointment 1** is driven by an on-device protocol agent that collects medically useful data in the period before a medical appointment, or during a doctor-prescribed follow-up.

Every day, the agent:

- Prompts the patient for short, targeted check-ins (symptoms, pain level, mood, sleep).
- Captures photos of evolving conditions (wounds, skin, swelling) on a fixed schedule.
- Pulls available data from the phone and connected sources (steps, heart rate, sleep, temperature entries).
- Produces a daily micro-report.

These micro-reports are not shown to the patient as medical conclusions. The application never diagnoses and never alarms. Instead, all collected data is compiled, graphed over time, and summarized into a single structured briefing intended for a real physician, delivered at the time of the appointment.

The doctor opens one page and sees: what happened, when it started, how it evolved, with photos, curves and patient-reported context.

## How data is stored

Medical data is the most sensitive data there is. Our long-term vision is a **self-deployed backend** per user (see `deploy-your-own-backend/`).

**MVP (current):** a centralized Go API on **Google Cloud Run** (`global-app-backend/`) handles auth, subscriptions, timelines and Gemini plan generation. Patient photos stay on-device as filenames in the timeline until private VPC sync ships.

See `ARCHITECTURE.md` for the full technical picture.

## How analysis works

Raw data alone is not a briefing. Analysis is performed by specialized cloud analysis agents (Gemini-based), each customized for a type of medical follow-up. The agent reads subscription parameters and timeline events, then produces check-in schedules and physician briefings.

The application is free to try; premium analysis agents are purchased per follow-up period. See `manifest/business-model.md`.

## What this is not

- It is not a diagnostic tool. It never tells the patient what they have.
- It is not a replacement for a doctor. Its only output is a better-informed consultation.
- It is not a data company by design — the architecture moves toward user-owned storage.

## Repository structure

| Path | Content |
| --- | --- |
| `androidp1/` | **Primary** — Kotlin + Jetpack Compose Android app |
| `iosp1/` | Native SwiftUI iOS app (feature parity in progress) |
| `shared-bodymap/` | 3D body map module (Three.js, offline) shared by both apps |
| `global-app-backend/` | Go API on Cloud Run (auth, subscriptions, Gemini, timeline) |
| `deploy-your-own-backend/` | Docker Compose template for a private user-owned node |
| `web-privacy-policy/` | Public privacy policy page |
| `store-assets/` | Play Store / App Store graphics |
| `manifest/` | Product principles, agent templates, business model |
| `ARCHITECTURE.md` | Technical stack and system design |
| `RELEASE_AND_ROADMAP.md` | Changelog and roadmap |

## Pain check-in: the 3D body map

Pain entries follow the PainDiary model (locate → rate → qualify), with a 3D twist: a monochrome 3D mannequin (Three.js, fully offline, no network permission) lets the patient rotate the body and tap the areas that hurt — front and back — then rate intensity (0–10) and pick pain qualities (burning, stabbing, throbbing…). Zones and qualities land in the timeline and in the physician briefing.

The 3D module lives once in `shared-bodymap/` and is embedded in both apps via a WebView bridge (Android WebView / WKWebView). Run `shared-bodymap/build.sh` after editing it to rebuild and redistribute the single-file HTML.

## Recent releases (Android)

| Version | Highlights |
| --- | --- |
| **1.0.7** | Version label on splash screen, doc sync across repo |
| **1.0.6** | Notification deep links to the right check-in slot, timeline-aware home badge, single API fetch |
| **1.0.5** | Smart schedule (first check-in from launch time), editable reminders, Gemini local time |

Download APKs from [GitHub Releases](https://github.com/Group-Hackathon/p1/releases).

## Status

Hackathon project for **XPRIZE Gemini**, category Professional Services Access. Android closed testing on Google Play; iOS TestFlight build in progress.

---
