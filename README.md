 <div align="center">

<img src="https://capsule-render.vercel.app/api?type=venom&height=230&color=0:000000,50:101010,100:000000&text=VANTA&fontColor=ffffff&fontSize=85&fontAlignY=45&desc=SMS%20BRIDGE%20%E2%80%A2%20ANDROID%20%E2%80%A2%20TELEGRAM&descAlignY=68&descSize=16&animation=fadeIn&stroke=ffffff&strokeWidth=1" width="100%" />

### Your phone. Your SIM. Your Telegram.

A self-hosted Android SMS-to-Telegram bridge. Turn a spare Android phone into your personal SMS gateway.

**Purpose-built Android application developed with Kotlin and Jetpack Compose.**

<br/>

<a href="https://github.com/Sefeshki/VANTA/releases"><img src="https://img.shields.io/github/v/release/Sefeshki/VANTA?style=for-the-badge&color=ffffff&label=RELEASE&labelColor=000000" /></a>
<a href="https://github.com/Sefeshki/VANTA"><img src="https://img.shields.io/github/stars/Sefeshki/VANTA?style=for-the-badge&color=ffffff&labelColor=000000" /></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/LICENSE-PolyForm_Noncommercial-ffffff?style=for-the-badge&labelColor=000000" /></a>
<img src="https://img.shields.io/badge/PLATFORM-ANDROID-ffffff?style=for-the-badge&labelColor=000000" />

<br/><br/>

**[DOWNLOAD](https://github.com/Sefeshki/VANTA/releases/latest) · [SOURCE CODE](https://github.com/Sefeshki/VANTA) · [DEVELOPER](https://github.com/Sefeshki)**

</div>

---

<div align="center">

### ◈ WHAT IS VANTA?

</div>

Got an old Android phone sitting around? Give it a job.

VANTA connects your Android device to your own Telegram bot and forwards incoming SMS messages directly to your Telegram account.

No separate server to maintain. No third-party forwarding platform. Your phone handles the bridge.

### ◈ FEATURES

<table>
<tr>
<td width="50%">

<h3>SMS BRIDGE</h3>

- Automatic incoming SMS forwarding
- Dual-SIM identification
- SIM1 / SIM2 / Both filtering
- Duplicate message protection
- Background forwarding service

</td>
<td width="50%">

<h3>TELEGRAM</h3>

- Your own Telegram bot
- Direct Telegram Bot API connection
- Secure bot pairing
- Telegram message delivery

</td>
</tr>
<tr>
<td width="50%">

<h3>PRIVACY</h3>

- Android Keystore encryption
- AES-256-GCM credential protection
- Optional OTP filtering
- Authorized Telegram user checks
- Credential cleanup on disconnect

</td>
<td width="50%">

<h3>SYSTEM</h3>

- Live service monitoring
- Connection diagnostics
- Automatic startup option
- Battery optimization guidance
- Interactive first-launch tutorial

</td>
</tr>
</table>

### ◈ YOUR PHONE DOES THE WORK

VANTA runs directly on your Android device and communicates with the official Telegram Bot API.

No separate forwarding server required.

SMS reception happens on your phone, but delivering messages to Telegram requires an internet connection. Local settings and parts of the application remain usable offline.

---

<div align="center">

### ◈ SECURITY

</div>

- Bot credentials are protected using Android Keystore-backed AES-256-GCM encryption.
- Telegram communication uses HTTPS.
- Telegram commands are restricted to authorized users.
- OTP filtering is optional.
- VANTA does not provide remote shell execution.

Your SMS messages are intentionally transmitted to the Telegram destination you configure. Keep your bot token private and only authorize people you trust.

---

### ◈ GETTING STARTED

**01 — Create your bot**

Open [@BotFather](https://t.me/BotFather) in Telegram.

Send `/newbot`, choose a name and username, then copy your API token.

**02 — Connect VANTA**

- Install VANTA on your Android phone.
- Open Telegram Setup.
- Paste your bot token.
- Verify the token.
- Generate your pairing code.
- Open your bot and send `/start YOUR_CODE`.

**03 — Give VANTA permission**

Open Permission Center and grant the required SMS, notification, and phone-state permissions.

Configure your SIM filtering and battery settings.

**04 — Activate**

Return to the Dashboard and enable forwarding.

That's it. Your phone is now your SMS bridge.

---

### ◈ BUILT WITH

<img src="https://img.shields.io/badge/Kotlin-ffffff?style=flat-square&logo=kotlin&logoColor=000000" /> <img src="https://img.shields.io/badge/Jetpack_Compose-ffffff?style=flat-square&logo=jetpackcompose&logoColor=000000" /> <img src="https://img.shields.io/badge/Android-ffffff?style=flat-square&logo=android&logoColor=000000" /> <img src="https://img.shields.io/badge/Telegram_Bot_API-ffffff?style=flat-square&logo=telegram&logoColor=000000" />

### ◈ LICENSE

VANTA is **Source-Available** under the **PolyForm Noncommercial License 1.0.0**.

Free for noncommercial use. Commercial use requires permission from the author.

See [LICENSE](https://github.com/Sefeshki/VANTA/blob/main/LICENSE) for the full license text.

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer&color=0:000000,50:101010,100:000000&text=BEYOND%20THE%20ORDINARY.&fontColor=ffffff&fontSize=25&fontAlignY=50&animation=fadeIn" width="100%" />

**MADE BY [SEFESHKI](https://github.com/Sefeshki)**

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases)

<sub>VANTA — Your device. Your connection. Your control.</sub>

</div>
