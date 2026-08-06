# Architecture

The system has three components, deliberately decoupled:

1. The mobile application (data collection, on-device agent).
2. The personal backend (self-deployed storage, one instance per user).
3. The cloud analysis agents (Gemini-based, specialized, ephemeral access).

```
+---------------------+         +----------------------------+
|  Mobile app (native) | ------> |  Personal backend (user-   |
|  Android + iOS,      |  sync   |  owned VPS or self-hosted) |
|  camera, sensors,   |         |  SQL + object storage      |
|  check-ins          |         +-------------+--------------+
+---------------------+                       |
                                    scoped, consented,
                                    time-limited access
                                              |
                                +-------------v--------------+
                                |  Cloud analysis agents     |
                                |  (Gemini + post-prompts)   |
                                |  micro-reports, graphs,    |
                                |  physician briefing        |
                                +----------------------------+
```

## 1. Mobile application

### Stack: Kotlin Multiplatform, Android-first

We use Kotlin Multiplatform (KMP) with Compose Multiplatform for UI.

Why KMP is the right call here:

- The collection logic, scheduling, data models, sync protocol and report assembly live once in `commonMain` and are shared across platforms.
- The parts that genuinely need native depth, camera capture, runtime permissions, background scheduling (WorkManager), Health Connect / HealthKit, are exactly the parts KMP lets us implement natively per platform via `expect/actual`, instead of fighting a cross-platform abstraction like React Native or Flutter plugins.
- Gemini SDKs and the whole Google stack are first-class on Kotlin/Android, which matters for this hackathon.

One honest caveat: "multiplatform" does not mean iOS comes for free. Permissions, background execution and health data APIs must be implemented twice. Our delivery strategy is therefore Android-first, shipping a complete Android app during the hackathon, with the shared module kept iOS-clean so an iOS target is an addition, not a rewrite.

Key modules:

| Module | Platform | Role |
| --- | --- | --- |
| `shared/core` | common | Data models, follow-up plans, scheduling rules, encryption, sync client |
| `shared/agent` | common | The on-device agent: decides what to ask, when to capture, what to pull |
| `app/android` | Android | Camera (CameraX), permissions, WorkManager jobs, Health Connect |
| `app/ios` | iOS (post-hackathon) | AVFoundation capture, HealthKit, BGTaskScheduler |

### Strategic Update (MVP Phase vs Scaling Phase)

While Kotlin Multiplatform (KMP) was the original architectural vision (as outlined above), **for the MVP phase, we chose to build two completely native applications (Kotlin for Android, Swift for iOS).** 

**Why this pivot for the MVP?**
1. **Speed & Deep Native Integration:** The core features of Pre-Appointment 1 rely heavily on complex native capabilities (Camera overlays, Health Connect / HealthKit, background schedulers). Building these natively and mirroring the logic proved faster than setting up complex `expect/actual` bindings and KMP iOS integrations during a time-constrained hackathon.
2. **Parallel Velocity:** Two pure native codebases allowed parallel development without blocking on shared module compilations.

**Future Scaling Strategy:**
If the product evolves to require deeply shared rendering logic, or if the team structure expands, the architecture will be re-evaluated. Depending on the team's affinities, we may migrate to **Flutter, React Native/Expo, or return to Kotlin Multiplatform**. However, to ensure rapid delivery and maximum stability during the MVP phase, maintaining a strictly mirrored native approach was the most pragmatic and efficient choice.

**Repository layout (MVP):**

| Path | Stack | Role |
| --- | --- | --- |
| `androidp1/` | Kotlin + Compose | Primary shipping target |
| `iosp1/` | SwiftUI | Feature-parity clone |
| `shared-bodymap/` | Three.js (vendored r149) | Offline 3D body map, embedded in both apps |
| `global-app-backend/` | Go + PostgreSQL on Cloud Run | Shared MVP API (auth, timeline, Gemini) |

### The 3D body map (shared-bodymap/)

Pain tracking follows the Privacy Friendly Pain Diary model — locate, rate, qualify — with the location step done on a 3D mannequin instead of a 2D silhouette.

- **One codebase, two platforms.** The module is plain JS + vendored Three.js, built by `build.sh` into a single self-contained `bodymap.html` (no CDN, no network, works in airplane mode — consistent with the privacy model). It is shipped as an Android asset (`androidp1/app/src/main/assets/bodymap/`) and an iOS bundle resource (`iosp1/P1/P1/Resources/`, auto-included by the Xcode synchronized folder).
- **Procedural monochrome mannequin.** ~25 meshes (spheres, half-sphere shells, capsules) form a stylized clinical body in the app's strict black-and-white palette; selected regions turn black. Front and back of the torso (chest/upper back, abdomen/lower back, pelvis/buttocks) are separate shells, so each side is selectable independently. No external 3D asset is needed, which avoids licensing and binary-size issues.
- **Interaction.** Drag rotates the body (with a native front/back flip button), tap raycasts to a region and toggles it. If WebGL is unavailable, the module degrades to an accessible list of region buttons — the feature never breaks.
- **Bridge.** JS → native: `AndroidBodyMap.onEvent(json)` on Android, `webkit.messageHandlers.bodymap` on iOS (`ready` + `selection` events). Native → JS: `bodymapSetRegions`, `bodymapReset`, `bodymapSetView`, `bodymapGetRegions`. Kotlin side: `ui/components/BodyMapView.kt`; Swift side: `UI/Components/BodyMapView.swift`.
- **Data.** The check-in (`PainDiaryStep` on both platforms) collects zones, intensity 0–10 and qualities; they are written into the timeline event (`Pain Level` / `Pain Areas` / `Pain Type` lines) and therefore flow into the physician briefing with no backend change.

### The on-device agent

The autonomous agent on the phone is a deterministic scheduler plus a lightweight LLM layer, not a free-running model:

- A follow-up plan (chosen when the user activates an analysis agent) defines what must be collected and at what cadence: for example "photo of the wound every morning, pain score twice a day, temperature every 4 hours".
- The agent executes the plan: notifications, guided photo capture with framing overlay so day-12 photos are comparable to day-1 photos, short adaptive questionnaires.
- Adaptive questions (rephrasing, follow-up questions based on previous answers) use Gemini Nano on-device where available, with a static questionnaire fallback. Nothing medical is decided on-device.

All captured data is encrypted on the device before leaving it, and is sent exclusively to the user's personal backend.

## 2. Personal backend: the self-deployed model

This is the core privacy decision of the project, modeled on the approach proven in [Garletz/gafam](https://github.com/Garletz/gafam): users do not send their data to us, they deploy their own backend.

### Why

Medical data is maximally sensitive. Any startup that aggregates patient records in a central database becomes, on day one, a breach target, a regulatory liability (HIPAA, GDPR article 9), and a single point of trust failure. We remove the problem instead of managing it:

- Each user runs one small backend instance, on a 5 USD VPS, on any cloud provider, or on a machine at home behind their own router.
- The mobile app pairs with that instance and syncs only to it.
- The instance holds the SQL database and object storage (photos) for that user alone.
- We, the company, never see, route, or hold medical data. There is no master database. There is nothing to leak.

This also makes the legal position clean: each user is the data controller of their own records. We ship software; they own data.

### How

- The backend is a single Docker Compose stack: API server (Go or Ktor), PostgreSQL, object storage (MinIO or filesystem), automatic TLS (Caddy).
- A one-command deploy script (and, stretch goal, a guided in-app deploy flow that provisions a VPS through provider APIs) brings an instance up in minutes, exactly like the `deploy-vpc.sh` pattern in gafam.
- Pairing app to backend is done with a QR code containing the instance URL and a bootstrap key.
- Data is encrypted at rest; transport is TLS; photos are encrypted client-side with a key that never leaves the user's devices, so even a compromised VPS leaks ciphertext.
- Encrypted off-site backup is the user's choice (export file or their own cloud bucket).

### Doctor access

The physician briefing is shared from the user's backend through a time-limited, read-only share link (or QR code shown in the consultation room). No doctor account on our side is required for v1.

## 3. Cloud analysis agents

Analysis is performed by specialized Gemini-based agents, purchased per follow-up (see `manifest/business-model.md`).

### What an agent is, technically

An analysis agent is deliberately simple: a Gemini model plus a versioned specialization layer.

- A system post-prompt encoding the medical domain of the follow-up (wound healing, prolonged fever, post-surgery recovery, ...): what to look at, what trends matter, what the briefing must contain.
- A collection plan template that the on-device agent executes (cadence, photo protocols, questionnaires, sensor pulls).
- An output schema: daily micro-report JSON, time-series definitions for graphs, and the final physician briefing structure.

Creating a new agent for a new pathology category is therefore content work, not engineering work. This is what makes the catalog scalable during the hackathon.

### Data flow and consent

1. User activates an agent for a follow-up period (for example 20 days before a dermatology appointment).
2. The user's backend issues the agent a scoped, time-limited access token: only the data streams the plan requires, only for the follow-up window.
3. Each day, the agent runs (Cloud Run job), reads the new data from the personal backend, calls Gemini (multimodal: photos, time series, text answers), and writes the micro-report back to the user's backend.
4. At the end of the period, the agent compiles all micro-reports, generates graphs and the physician briefing, writes them back, and its token expires.

Micro-reports and the briefing are stored on the user's backend, not ours. The agent infrastructure is stateless: it processes and forgets. Inference goes through Vertex AI with no data retention for training.

### Cloud stack

| Concern | Choice |
| --- | --- |
| Agent runtime | Cloud Run jobs (scheduled per active follow-up) |
| Models | Gemini (multimodal) via Vertex AI; Gemini Nano on-device |
| Agent catalog and purchases | Small stateless API + Stripe (holds zero medical data) |
| CI/CD | GitHub Actions on this repository, deploy to Cloud Run |

The only centralized services we operate are the agent catalog, billing, and the stateless agent runtime. None of them store patient data.

## Security summary

- No central medical database exists anywhere in the system.
- Client-side encryption for photos; key never leaves the user's devices.
- Agent access is scoped, consented, time-limited, and revocable by the user at any time.
- The patient-facing app never produces medical conclusions; the only medical-facing output is the physician briefing, read by a professional.
