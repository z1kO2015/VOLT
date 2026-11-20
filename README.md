# ⚡ VOLT — Battery & Power Utility for Meta Quest 2

**VOLT** is a lightweight, fast, and distraction-free Android utility application built with Kotlin specifically for the **Meta Quest 2** standalone VR headset. It helps players monitor real-time battery percentage, charging state, and power behavior during gaming sessions without heavy background services.

---

## ✨ Features

- **Real-Time Battery Monitoring** — Instant overview of current charge level and charging status.
- **Lightweight Architecture** — Written in native Kotlin with minimal RAM and CPU overhead so it never impacts VR performance.
- **VR-Friendly Interface** — Clean, high-contrast layout designed to be easily readable inside the headset.
- **Cloud-Built Releases** — Automated `.apk` builds powered by GitHub Actions.

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **Target Platform:** Android / Meta Quest OS (Horizon OS)
- **Target SDK:** Android API 34
- **Build System:** Gradle & GitHub Actions CI/CD

---

## 📥 Installation (Sideloading)

1. Download the latest `.apk` archive from the **Actions** tab (or **Releases** section).
2. Extract `app-debug.apk` from the downloaded ZIP archive.
3. Connect your **Meta Quest 2** to a PC or smartphone via USB (with Developer Mode enabled).
4. Install the `.apk` using **SideQuest** or **ADB**:
   ```bash
   adb install app-debug.apk
