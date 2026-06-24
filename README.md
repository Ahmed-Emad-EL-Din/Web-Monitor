# 🌐 Web Monitor & AI Tracker

A powerful, automated Android application designed to track website changes, summarize content updates using **Gemini AI**, and deliver real-time alerts via **System Notifications** and **Telegram**.

## ✨ Key Features

- **🔄 Automated Tracking:** Periodically monitors targeted websites and endpoints for structural or content changes in the background.
- **🧠 AI-Powered Summaries:** Integrates with Google's Gemini AI to intelligently compare old and new website content, generating concise 1-sentence summaries of what actually changed.
- **📱 Multi-Channel Alerts:** 
  - Push Notifications directly to your Android device.
  - **Telegram Bot Integration:** Broadcast alerts directly to personal chats or community channels.
- **⚡ Quick Settings Tile:** Instantly toggle tracking or monitor status directly from the Android Quick Settings panel.
- **🛡️ Smart Failure Handling:** Detects broken layouts or expired sessions (e.g., login requirements) and automatically suspends tracking to save resources, notifying you immediately.
- **🎯 Custom AI Conditions:** Set specific prompt conditions for when an alert should trigger, minimizing noise and saving AI tokens.

## 🚀 Getting Started

### Prerequisites
- Android Studio / JDK 21
- Google Gemini API Key
- Telegram Bot Token (Optional, for Telegram alerts)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/web-monitor.git
   ```
2. Open the project in Android Studio.
3. Securely provide your API keys in the app's settings/preferences.
4. Build and run the application on your device.

## 🤖 Telegram Integration

To enable Telegram alerts:
1. Message `@BotFather` on Telegram to create a new bot and obtain your **Bot Token**.
2. Add the bot to your desired channel or chat.
3. Input the token and the target Chat ID within the app's settings.

## ⚙️ CI/CD

This project includes a GitHub Actions workflow (`.github/workflows/android.yml`) that automatically compiles the APK and runs unit tests on every push or pull request to the `main` branch. 

## ⚖️ License & Usage

**Non-Commercial / Proprietary License**

This project is strictly for **personal, non-commercial use only**. You may not use this software for business, monetization, or integrate it into a commercial product without explicit written permission and a negotiated revenue-sharing agreement.

Please review the full [LICENSE](LICENSE) file for complete details. If you are interested in utilizing this software for commercial purposes, please contact the developer to arrange a commercial license.
