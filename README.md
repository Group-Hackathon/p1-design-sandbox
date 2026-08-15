# 🌿 P1 — Pre-Appointment 1 & Living Patient Memory

<div align="center">
  <img src="docs/images/p1_logo_official_final.png" alt="P1 Official App Logo" width="220" style="border-radius: 40px; box-shadow: 0 16px 36px rgba(45, 90, 71, 0.2);" />
  
  <h3><strong>Next-Gen AI Pre-Appointment & Clinical Memory Mobile Companion</strong></h3>
  <p><em>Prepare crystal-clear, structured clinical briefings for your doctor — in under 2 minutes a day.</em></p>

  <p>
    <img src="https://img.shields.io/badge/Platform-Android_Jetpack_Compose-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
    <img src="https://img.shields.io/badge/Theme-Stitch_Sage_%26_Mint-2D5A47?style=for-the-badge" />
    <img src="https://img.shields.io/badge/AI-Gemini_Clinical_Synthesis-4285F4?style=for-the-badge&logo=google" />
    <img src="https://img.shields.io/badge/Storage-Offline_First_Room_DB-007ACC?style=for-the-badge" />
  </p>
</div>

---

## 🎨 Official P1 Logo (Sage HUD & Biometric Viewfinder)

The official brand identity of **P1** embodies modern clinical precision:
- **Monogram**: Deep **Sage Green (`#2D5A47`)** bold italic **P1** emblem.
- **Biometric Telemetry**: High-precision circular gauge dial with active P1 signal bars, QR identification, and telemetry barcode.
- **Reticle HUD**: 4 rounded framing brackets representing continuous focus on the patient's living clinical memory.

<div align="center">
  <img src="docs/images/p1_logo_official_final.png" width="280" style="border-radius: 36px; box-shadow: 0 12px 28px rgba(0,0,0,0.08);" />
  <br /><br />
  <em>Official P1 Application Launcher & Brand Asset</em>
</div>

---

## 📱 Complete User Journey & Verified Live Screenshots

<table>
  <tr>
    <th align="center">🏠 1. Home Screen (Patient 1)</th>
    <th align="center">🎙️ 2. Voice Check-in Sheet</th>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/images/screen_home.png" width="300" style="border-radius: 20px; border: 1px solid #E5ECE7;" />
      <br /><br />
      <strong>Real-Time Home Hub</strong>
      <br />
      <em>Personalized greeting (Patient 1), micro-sentiment buttons (Better / Same / Worse), one-tap voice logger, and active file readiness.</em>
    </td>
    <td align="center">
      <img src="docs/images/screen_voice.png" width="300" style="border-radius: 20px; border: 1px solid #E5ECE7;" />
      <br /><br />
      <strong>Hands-Free Speech Logger</strong>
      <br />
      <em>Real-time speech-to-text listener with live transcript preview and direct one-tap commitment to the patient timeline.</em>
    </td>
  </tr>
  <tr>
    <th align="center">📊 3. Timeline & Chronology</th>
    <th align="center">📑 4. Official Doctor Briefing</th>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/images/screen_timeline.png" width="300" style="border-radius: 20px; border: 1px solid #E5ECE7;" />
      <br /><br />
      <strong>Real Patient Data & Countdown</strong>
      <br />
      <em>Chronological nodes showing status updates, countdown to next scheduled check-in, quick notes, and 3D body measurements.</em>
    </td>
    <td align="center">
      <img src="docs/images/screen_report.png" width="300" width="300" style="border-radius: 20px; border: 1px solid #E5ECE7;" />
      <br /><br />
      <strong>Clinical Synthesis PDF</strong>
      <br />
      <em>Automatically generated medical report featuring temperature curves, pain trends, and structured logs ready for consultation.</em>
    </td>
  </tr>
  <tr>
    <th align="center">📁 5. Preparation Folder</th>
    <th align="center">📂 6. Side Navigation Drawer</th>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/images/screen_prep.png" width="300" style="border-radius: 20px; border: 1px solid #E5ECE7;" />
      <br /><br />
      <strong>Secure On-Device Storage</strong>
      <br />
      <em>Consolidated preparation folder for prescriptions, lab PDF results, and symptom photos stored securely on device.</em>
    </td>
    <td align="center">
      <img src="docs/images/screen_drawer.png" width="300" style="border-radius: 20px; border: 1px solid #E5ECE7;" />
      <br /><br />
      <strong>Drawer & Reminder Management</strong>
      <br />
      <em>Full file switcher, new appointment creation flow, and protocol reminder configuration.</em>
    </td>
  </tr>
</table>

---

## ⚡ Key Highlights & Architecture

### 🎙️ 1. Connected Voice Check-in
- **Direct Timeline Integration**: Voice transcripts are automatically committed to the local `TimelineRepository` and synchronized to the active file.
- **Smart AI Summaries**: Symptoms and context are extracted without requiring manual form entry.

### 📈 2. 100% Real Patient Data & Dynamics
- **Zero Hardcoded Dummy Graphs**: The 7-day wellbeing trend card computes spline curves dynamically from recorded pain scores, vitals, and daily sentiment logs.
- **Readiness Metric**: Calculates real progress toward appointment day based on completed protocol tasks.

### 🌐 3. Full i18n Localization (English & French)
- Complete semantic string separation in `res/values/strings.xml` and `res/values-fr/strings.xml`.
- Automatically adapts to system locale with zero missing keys.

### 🧩 4. Clean Modular Architecture
- **`AppNavigation.kt`**: Modular navigation router handling bottom navigation persistence, deep links, and screen transitions (`Crossfade`).
- **`LocalRepositories.kt`**: Offline-first Room database repository with background synchronization (`SyncManager`).
- **`Theme.kt`**: Curated Stitch design tokens (`SagePrimary`, `MintBadge`, `CanvasBackground`, `CardBackground`).

---

## 🛠️ Build & Installation

```bash
# Navigate to the Android project directory
cd androidp1

# Compile Debug APK
./gradlew assembleDebug

# Install directly to connected ADB device
./gradlew installDebug

# Launch the app on phone
adb shell am start -n com.preappointment1.designsandbox/com.preappointment1.app.MainActivity
```

---

<div align="center">
  <sub>Developed with Google Stitch & Jetpack Compose. Built for the Hackathon.</sub>
</div>
