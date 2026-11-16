<div align="center">
<img src="https://capsule-render.vercel.app/api?type=venom&height=230&color=0:000000,50:101010,100:000000&text=VANTA&fontColor=ffffff&fontSize=85&fontAlignY=45&desc=SMS%20BRIDGE%20%E2%80%A2%20ANDROID%20%E2%80%A2%20TELEGRAM&descAlignY=68&descSize=16&animation=fadeIn&stroke=ffffff&strokeWidth=1" width="100%" />

**Your phone. Your SIM. Your Telegram.**

A self-hosted Android SMS-to-Telegram bridge.  
Turn a spare Android phone into your personal SMS gateway.

Purpose-built Android application developed with Kotlin and Jetpack Compose.

<br/>

<a href="https://github.com/Sefeshki/VANTA/releases"><img src="https://img.shields.io/github/v/release/Sefeshki/VANTA?style=for-the-badge&color=ffffff&label=RELEASE&labelColor=000000" /></a>
<a href="https://github.com/Sefeshki/VANTA"><img src="https://img.shields.io/github/stars/Sefeshki/VANTA?style=for-the-badge&color=ffffff&labelColor=000000" /></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/LICENSE-PolyForm_Noncommercial-ffffff?style=for-the-badge&labelColor=000000" /></a>
<img src="https://img.shields.io/badge/PLATFORM-ANDROID-ffffff?style=for-the-badge&labelColor=000000" />

<br/><br/>

[DOWNLOAD](https://github.com/Sefeshki/VANTA/releases/latest) · [SOURCE CODE](https://github.com/Sefeshki/VANTA) · [DEVELOPER](https://github.com/Sefeshki)
</div>

---

### What is VANTA?

Got an old Android phone sitting around? Give it a real job.

VANTA turns any Android phone with a SIM into a personal SMS bridge. Incoming messages get forwarded to a Telegram bot that you control. The whole thing runs on the phone itself — no separate server, no third-party forwarding service.

It supports dual-SIM devices properly, keeps running in the background, and was built to stay reliable on both older and newer phones while using as little RAM and battery as possible.

---

### Features

<table>
<tr>
<td width="50%">

#### SMS Bridge
- Automatic forwarding of incoming SMS
- Proper dual-SIM support
- Individual SIM forwarding controls
- Duplicate message protection
- Reply to SMS directly from Telegram
- Forward messages to other Telegram destinations
- Background service that stays reliable

</td>
<td width="50%">

#### Telegram
- Your own Telegram bot (official Bot API)
- Secure token verification and pairing
- Interactive inline keyboard controls
- Service status, SIM info, and forwarding controls
- Message-level Reply and Forward actions
- Live connection and service information

</td>
</tr>
<tr>
<td width="50%">

#### Privacy & Security
- Android Keystore + AES-256-GCM for credentials
- HTTPS only for Telegram communication
- Authorized Telegram users only
- Optional OTP filtering (off by default)
- Clean credential removal on disconnect
- No remote shell or extra access

</td>
<td width="50%">

#### System
- Live service monitoring
- Connection diagnostics
- Automatic start on boot (optional)
- Battery optimization guidance
- Service failure & recovery notifications
- Four home-screen widgets
- Light RAM and battery usage across devices

</td>
</tr>
</table>

---

### SIM Management

VANTA has a real SIM management section — it’s not just a simple SIM1 / SIM2 / Both filter.

It uses Android’s actual subscription information to identify SIMs correctly. It knows which physical SIM received a message and which SIM is used when you send a reply. Slot and carrier details are shown where the system provides them, and the same information stays consistent both in the app and in Telegram.

You can enable or disable forwarding per SIM independently. If a SIM is unavailable or disconnected, the service status reflects that.

---

### Telegram Interaction

Once the bot is connected, most control happens through interactive buttons rather than slash commands.

You can check service status, view SIM information, manage forwarding, and see connection details directly from Telegram. There’s also built-in guidance that walks you through creating the bot with BotFather if you need it.

When an SMS arrives:

**Reply**  
Press Reply, type your message, and send it. A Cancel button is available while the reply is pending. The reply goes out through the same SIM that received the original message. Once Android confirms it was sent, VANTA appends the reply details under the original Telegram message so the conversation stays clear.

**Forward**  
You can forward the incoming SMS to another Telegram destination you’ve configured. This is Telegram-to-Telegram forwarding of the message content — it doesn’t turn Telegram into a full native SMS client or give access to every private chat.

---

### Home Screen Widgets

VANTA includes four different home-screen widgets. They give you a quick look at device, SIM, connectivity, and forwarding status without opening the app. Tapping any of them opens VANTA.

---

### Service Monitoring & Notifications

The service watches itself and can notify you about things that actually matter:

- Telegram connection lost or restored  
- SIM availability problems  
- SMS forwarding failures or interruptions  
- Permission issues  
- Service recovery

These are purposeful alerts, not constant noise.

VANTA also keeps a clear distinction between three different states: whether the phone has internet, whether the Telegram API is reachable, and whether SMS forwarding is actually active. Having internet does not automatically mean Telegram is working or that messages are being forwarded.

---

### How it works

Everything runs on your Android phone and talks directly to the official Telegram Bot API. No extra server to maintain.

Your phone receives the SMS. As long as it has a working internet connection, the message is delivered to Telegram. Most settings and diagnostics remain usable offline.

The background service was deliberately optimized for low RAM usage and battery life so it can run cleanly on both older and newer Android devices. Automatic start on boot is available if you want it. Recovery and retry logic help when connectivity drops.

---

### Security

A lot of care went into keeping this as secure as possible.

Bot credentials are protected with Android Keystore-backed AES-256-GCM encryption. All communication with Telegram uses HTTPS. Only the Telegram users you authorize can interact with the bot. OTP filtering is available as an option and stays off by default.

When you disconnect, credentials are cleaned up. VANTA does not provide any remote shell or extra device access.

One honest note: the whole point of the app is to send your SMS messages to the Telegram destination you configure. Keep your bot token private and only authorize people you actually trust.

---

### Settings & Customization

You can manage forwarding per SIM, turn OTP protection on or off, and choose from several accent themes (Purple, Red, Blue, Cyan). There are also clear settings for service behavior and the permissions the app needs.

---

### Updates

VANTA checks GitHub releases for newer versions. When an update is available it lets you know so you can go grab it. It does not download or install anything automatically.

Repository: https://github.com/Sefeshki/VANTA

---

### Getting started

**1. Create your bot**  
Open [@BotFather](https://t.me/BotFather) in Telegram.  
Send `/newbot`, choose a name and username, then copy the API token.

**2. Connect VANTA**  
Install the app on your Android phone.  
Open Telegram Setup, paste your bot token, and verify it.  
Generate the pairing code, open your bot, and send:  
`/start YOUR_CODE`

**3. Give permissions**  
Open the Permission Center and grant the required SMS, notification, and phone-state permissions.  
Configure your SIM forwarding preferences and check the battery optimization tips.

**4. Activate**  
Return to the Dashboard and enable forwarding.

That’s it. Your phone is now your SMS bridge.

---

### Built with

<img src="https://img.shields.io/badge/Kotlin-ffffff?style=flat-square&logo=kotlin&logoColor=000000" /> <img src="https://img.shields.io/badge/Jetpack_Compose-ffffff?style=flat-square&logo=jetpackcompose&logoColor=000000" /> <img src="https://img.shields.io/badge/Android-ffffff?style=flat-square&logo=android&logoColor=000000" /> <img src="https://img.shields.io/badge/Telegram_Bot_API-ffffff?style=flat-square&logo=telegram&logoColor=000000" />

---

### License

VANTA is source-available under the PolyForm Noncommercial License 1.0.0.  
Free for noncommercial use. Commercial use requires permission from the author.

See [LICENSE](https://github.com/Sefeshki/VANTA/blob/main/LICENSE) for the full license text.

---

If you run into any issues or have suggestions, feel free to open an issue on GitHub or message me directly on Telegram: [t.me/sefeshki](https://t.me/sefeshki)

---

<div align="center">
<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer&color=0:000000,50:101010,100:000000&text=BEYOND%20THE%20ORDINARY.&fontColor=ffffff&fontSize=25&fontAlignY=50&animation=fadeIn" width="100%" />

Made by [Sefeshki](https://github.com/Sefeshki)

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases)

<sub>VANTA — Your device. Your connection. Your control.</sub>
</div>
