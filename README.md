# 🚦 SmartRoad System

> **A Smart Road Hazard Reporting & Monitoring System for Communities**

---

# 📱 Overview

SmartRoad System is a community-driven road hazard reporting platform designed to bridge the communication gap between road users and local authorities. The system enables users to report road hazards directly from their mobile devices while allowing administrators to monitor, verify, and manage reports through a centralized web dashboard.

This project consists of two integrated platforms:

- 📱 **Android Mobile Application** – Enables users to report road hazards with GPS location, photos, and detailed information.
- 💻 **Web-based Administration Dashboard** – Allows administrators to monitor reports, verify submissions, update hazard statuses, and manage users.

Together, these platforms provide a complete end-to-end solution for improving road safety through community participation and real-time data management.

---

# ✨ System Architecture

The SmartRoad System is built using a cloud-based architecture that synchronizes data between the Android application and the web dashboard in real time.

- **📱 Mobile Application:** Android (Java), Material Design, Google Maps SDK
- **💻 Web Dashboard:** HTML5, CSS3, JavaScript, Bootstrap
- **☁️ Backend:** Firebase Authentication, Cloud Firestore, Firebase Storage
- **🗺️ Maps:** Google Maps SDK & Google Maps API

---

# 🚀 Key Features

## 📱 Android Mobile Application

- User Authentication (Email & Google Sign-In)
- Real-time Road Hazard Reporting
- GPS Location Detection
- Google Maps Integration
- Select Hazard Location on Map
- Capture & Upload Hazard Photos
- View Existing Hazard Markers
- User Profile Management
- Firebase Cloud Firestore Integration

---

## 💻 Web Administration Dashboard

- Administrator Login
- Dashboard Analytics
- Manage Road Hazard Reports
- View Report Details & Evidence Photos
- Update Hazard Status
- Delete Reports
- Member Management
- Interactive Google Maps
- System Settings

---

# 🛠 Technology Stack

| Platform | Technologies |
|----------|--------------|
| 📱 Mobile | Java, Android Studio, Material Design |
| 💻 Web | HTML5, CSS3, JavaScript, Bootstrap |
| ☁️ Backend | Firebase Authentication, Cloud Firestore, Firebase Storage |
| 🗺️ Maps | Google Maps SDK, Google Maps API |

---

# 📂 Project Structure

```text
SmartRoad-System/
├── app/                    # Android Source Code (Java)
├── assets/                 # Images & UI Resources
├── Web-Dashboard/          # Web-based Admin Dashboard
├── .gitignore
└── README.md
```

# 🚀 Getting Started

## 📱 Android Application

```bash
git clone https://github.com/ammarsyaathir/SMARTROAD.git
```

1. Open the project using **Android Studio**.
2. Add your `google-services.json` file into the `app/` directory.
3. Sync Gradle dependencies.
4. Run the application on an Android emulator or physical device.

---

## 💻 Web Administration Dashboard

1. Open the `Web-Dashboard` folder.
2. Launch `index.html` in your web browser.


