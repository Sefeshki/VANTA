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

VANTA turns any Android phone with a SIM into a personal SMS bridge. Incoming messages get forwarded to a Telegram bot that you control. Everything runs on the phone itself — no separate server, no third-party service.

It supports dual-SIM devices, stays reliable in the background, and was optimized for low RAM and battery usage on both old and new phones.

---

### Features

<table>
<tr>
<td width="50%">

#### SMS Bridge
- Automatic incoming SMS forwarding
- Full dual-SIM support
- Individual SIM controls
- Reply to SMS from Telegram
- Forward messages to other chats
- Duplicate protection
- Reliable background service

</td>
<td width="50%">

#### Telegram
- Your own bot (official Bot API)
- Secure pairing & token verification
- Interactive inline keyboards
- Service status & SIM info
- Reply and Forward actions
- Live connection monitoring

</td>
</tr>
<tr>
<td width="50%">

#### Privacy & Security
- Android Keystore + AES-256-GCM
- HTTPS only
- Authorized users only
- Optional OTP filtering (off by default)
- Clean credential removal
- No remote access

</td>
<td width="50%">

#### System
- Live service monitoring
- Connection diagnostics
- Auto-start on boot (optional)
- Battery optimization tips
- Failure & recovery alerts
- Four home-screen widgets
- Low RAM & battery usage

</td>
</tr>
</table>

---

### SIM Management

VANTA has a dedicated interactive section where you can manage all settings for each SIM independently — including which ones forward messages, carrier info, and current status.

---

### Telegram Interaction

Most control happens through buttons, not commands.

You can check status, view SIM details, manage forwarding, and reply or forward incoming SMS directly from Telegram. Replies are sent through the original SIM and appear under the message for easy tracking. Forwarding lets you send the message to another configured Telegram destination.

---

### Widgets & Monitoring

Four home-screen widgets give you a quick overview of SIMs, connectivity, and forwarding status without opening the app.

The service also watches itself and sends clear notifications when something important happens (connection lost/restored, SIM problems, forwarding failures, or recovery). It keeps internet, Telegram reachability, and actual forwarding status separate so you always know what’s going on.

---

### How it works

Everything runs on your phone and talks directly to the official Telegram Bot API. No extra server needed.

Your phone receives the SMS. With an internet connection it gets delivered to Telegram. Most settings still work offline.

The background service was carefully optimized for low RAM and battery so it runs cleanly on both older and newer Android devices.

---

### Security

A lot of care went into keeping this secure.

Credentials are protected with Android Keystore (AES-256-GCM). Communication is HTTPS only. Only authorized Telegram users can interact. OTP filtering is optional and stays off by default. Credentials are cleaned up when you disconnect. No remote shell or extra access is provided.

Remember: the purpose of the app is to send your SMS messages to the Telegram destination you choose. Keep your bot token private and only authorize people you trust.

---

### Settings & Updates

Manage per-SIM forwarding, OTP protection, and choose from several accent themes (Purple, Red, Blue, Cyan).

VANTA checks GitHub for new releases and lets you know when an update is available. It never installs anything automatically.

---

### Getting started

**1. Create your bot**  
Open [@BotFather](https://t.me/BotFather) → send `/newbot` → copy the token.

**2. Connect VANTA**  
Install the app → open Telegram Setup → paste token → verify → generate pairing code → send `/start YOUR_CODE` to your bot.

**3. Permissions**  
Open Permission Center and grant the required SMS, notification, and phone-state permissions. Set your SIM preferences and check battery tips.

**4. Activate**  
Go to the Dashboard and enable forwarding.

That’s it.

---

### Built with

<img src="https://img.shields.io/badge/Kotlin-ffffff?style=flat-square&logo=kotlin&logoColor=000000" /> <img src="https://img.shields.io/badge/Jetpack_Compose-ffffff?style=flat-square&logo=jetpackcompose&logoColor=000000" /> <img src="https://img.shields.io/badge/Android-ffffff?style=flat-square&logo=android&logoColor=000000" /> <img src="https://img.shields.io/badge/Telegram_Bot_API-ffffff?style=flat-square&logo=telegram&logoColor=000000" />

---

### License

VANTA is source-available under the PolyForm Noncommercial License 1.0.0.  
Free for noncommercial use. Commercial use requires permission from the author.

See [LICENSE](https://github.com/Sefeshki/VANTA/blob/main/LICENSE) for the full text.

---

Found a bug or have a suggestion? Open an issue on GitHub or message me on Telegram: [t.me/sefeshki](https://t.me/sefeshki)

---

<div align="center">
<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer&color=0:000000,50:101010,100:000000&text=BEYOND%20THE%20ORDINARY.&fontColor=ffffff&fontSize=25&fontAlignY=50&animation=fadeIn" width="100%" />

Made by [Sefeshki](https://github.com/Sefeshki)

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases)

<sub>VANTA — Your device. Your connection. Your control.</sub>
</div>
