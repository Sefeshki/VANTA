# VANTA — SMS-to-Telegram Bridge

![VANTA Cyberpunk SMS Bridge](app/src/main/res/drawable/vanta_icon.png)

**VANTA** is a production-oriented, open-source Android SMS-to-Telegram bridge designed and created by **[Sefeshki](https://github.com/Sefeshki/)**.

VANTA allows you to leave a secondary Android phone powered on with your SIM cards, transparently forwarding all incoming SMS messages directly to your personal Telegram bot with military-grade privacy, dual-SIM filtering, real-time cyber telemetry, and zero covert surveillance.

---

## ⚡ Features

- **Cyberpunk Dark Aesthetic**: Technical VPN-style dashboard, glowing controls, real-time monospace telemetry, and 4 complete color themes:
  - 🟣 **Black / Neon Purple** (Default)
  - 🔴 **Black / Crimson Red**
  - 🔵 **Black / Sapphire Blue**
  - 🟢 **Black / Cyber Cyan**
- **Dual-SIM Identification & Filtering**:
  - Independent hardware slot inspection via Android `SubscriptionManager`.
  - Filter modes: **Both SIMs**, **SIM1 Only**, or **SIM2 Only**.
  - Respects Android carrier privacy when phone numbers are masked.
- **Direct Telegram Bot API Integration**:
  - Direct HTTPS communication via OkHttp with `api.telegram.org` (no third-party proxy or middleman servers).
  - Telegram `custom_emoji` support with exact UTF-16 entity calculations.
  - Interactive inline GitHub URL button under all notifications.
- **Hardware-Backed Credential Security**:
  - Bot token stored encrypted with Android Keystore AES-256-GCM.
  - Complete credential purging on disconnect.
  - No plaintext tokens or SMS bodies ever written to logs or storage.
- **Sensitive OTP & 2FA Protection**:
  - Built-in heuristic filter detecting authentication tokens, one-time passwords, and verification codes in English, Persian, and Arabic.
  - Blocks sensitive credentials from forwarding by default unless explicitly toggled off.
- **Robust Background Operation**:
  - **Stay Alive**: Persistent Android foreground service holding a wake lock during processing.
  - **Auto Start**: Optional boot recovery (`ACTION_BOOT_COMPLETED`) resuming forwarding after device reboot.
  - Exponential backoff delivery queue with duplicate suppression (SHA-256 message hashing).
- **10-Step Interactive Spotlight Tour**:
  - First-launch interactive walkthrough explaining every feature and control.
  - Replayable on demand from Settings.

---

## 📱 Telegram Message Format

### Incoming SMS Notification
```text
⚡ New Message (SIM1):
By: +12025550198

⚡ context:
Your order has shipped with tracking number AB123456789.

━━━━━━━━━━━━━━━━━━━━
[ GitHub Button ]
```

### `/start` Live Status Response
```text
Activation: ⚡ ONLINE

Active Numbers:
⚡ SIM1 (T-Mobile): +12025550198
⚡ SIM2 (Irancell): NUMBER UNAVAILABLE

━━━━━━━━━━━━━━━━━━━━
[ GitHub Button ]
```

---

## 🚀 Quick Start & Setup Guide

### 1. Create your Telegram Bot
1. Open Telegram and search for `@BotFather`.
2. Send `/newbot` and follow the prompts to choose a display name and username.
3. Copy your HTTP API token (e.g. `123456789:ABCdefGhIJKlmNoPQRstuVWXyz`).

### 2. Configure VANTA on your Secondary Phone
1. Launch **VANTA**.
2. Open **TELEGRAM SETUP**.
3. Paste your Bot Token and tap **VERIFY TOKEN**.
4. Tap **GENERATE CODE** to produce a secure 6-digit expiring pairing code.
5. In Telegram, open your bot chat and send:
   ```text
   /start <YOUR_6_DIGIT_CODE>
   ```
6. VANTA will automatically detect the authorized pairing and verify the connection.

### 3. Grant Permissions & Enable Forwarding
1. Navigate to **PERMISSION CENTER** and grant:
   - **SMS Reception** (`RECEIVE_SMS`)
   - **Notifications** (`POST_NOTIFICATIONS`)
   - **Phone State** (`READ_PHONE_STATE` for dual-SIM carrier names)
   - **Battery Optimization** (Whitelisted for 24/7 background operation)
2. Return to the dashboard and tap the master **ACTIVE** toggle.

---

## 🛡️ Security Architecture & Threat Model

1. **Android Keystore AES-256-GCM**: Credentials never reside in plaintext on disk.
2. **Direct HTTPS Endpoint**: All requests communicate directly with official Telegram Bot API servers over TLS 1.3/1.2.
3. **Authorized User Gate**: Unauthorized Telegram users sending commands receive an authorization error and never receive device information or SMS contents.
4. **No Arbitrary Remote Execution**: VANTA does not execute shell commands or remote scripts from Telegram.
5. **Transparency**: All system actions appear in the Cyber Terminal.

---

## 🧪 Testing

VANTA includes Robolectric and local JVM unit tests covering:
- Telegram Custom Emoji UTF-16 entity offset calculations
- Dual-SIM slot parsing and `/start` status formatting
- Multi-language SMS content preservation (Persian, Arabic, English, RTL, LTR)
- OTP and authentication code detection patterns
- In-memory event log sanitization (credential and OTP redaction)

Run the test suite with:
```bash
gradle :app:testDebugUnitTest
```

---

## 📄 License & Attribution

Created by **Sefeshki**  
GitHub: [https://github.com/Sefeshki/](https://github.com/Sefeshki/)  
Licensed under the **MIT License**. See `LICENSE` for details.
