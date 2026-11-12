 <div align="center">

<img src="https://capsule-render.vercel.app/api?type=venom&height=230&color=0:080808,50:24004f,100:050505&text=VANTA&fontColor=ffffff&fontSize=85&fontAlignY=45&desc=SMS%20BRIDGE%20%E2%80%A2%20ANDROID%20%E2%80%A2%20TELEGRAM&descAlignY=68&descSize=16&animation=fadeIn&stroke=8b5cf6&strokeWidth=1" width="100%" />

### Your phone. Your SIM. Your Telegram.

A self-hosted Android SMS-to-Telegram bridge. Turn a spare Android phone into your personal SMS gateway.

**Not a vibe-coded template.** VANTA is a purpose-built Android application developed with Kotlin and Jetpack Compose.

<br/>

<a href="https://github.com/Sefeshki/VANTA/releases"><img src="https://img.shields.io/github/v/release/Sefeshki/VANTA?style=for-the-badge&color=8b5cf6&label=RELEASE" /></a>
<a href="https://github.com/Sefeshki/VANTA"><img src="https://img.shields.io/github/stars/Sefeshki/VANTA?style=for-the-badge&color=06b6d4" /></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/LICENSE-MIT-8b5cf6?style=for-the-badge" /></a>
<img src="https://img.shields.io/badge/PLATFORM-ANDROID-22c55e?style=for-the-badge" />

<br/><br/>

**[ DOWNLOAD ](https://github.com/Sefeshki/VANTA/releases/latest) · [ SOURCE CODE ](https://github.com/Sefeshki/VANTA) · [ DEVELOPER ](https://github.com/Sefeshki)**

</div>

---

<div align="center">

### ◈ WHAT'S VANTA?

</div>

Got an old Android phone sitting around? Give it a job.

VANTA connects your Android device to your own Telegram bot and forwards incoming SMS messages directly to your Telegram account.

No separate server to maintain. No third-party forwarding platform. Your phone handles the bridge.

### ✦ WHAT'S INSIDE?

<table>
<tr>
<td width="50%">

<h3>◉ SMS BRIDGE</h3>

- Automatic incoming SMS forwarding
- Dual-SIM identification
- SIM1 / SIM2 / Both filtering
- Duplicate message protection
- Background forwarding service

</td>
<td width="50%">

<h3>◈ TELEGRAM</h3>

- Your own Telegram bot
- Direct Telegram Bot API connection
- Secure bot pairing

</td>
</tr>
<tr>
<td width="50%">

<h3>⌘ PRIVACY</h3>

- Android Keystore encryption
- AES-256-GCM credential protection
- Optional OTP / verification-code filtering
- Authorized Telegram user checks
- Credential cleanup on disconnect

</td>
<td width="50%">

<h3>⚡ SYSTEM</h3>

- Live service monitoring
- Connection diagnostics
- Automatic startup option
- Battery optimization guidance
- Interactive first-launch tutorial

</td>
</tr>
</table>

### ◈ YOUR PHONE DOES THE WORK

VANTA runs on your Android device and communicates directly with the official Telegram Bot API. It does not require a separate forwarding server.

**A little network reality:** SMS reception happens on the phone, but delivering messages to Telegram requires an internet connection. Local settings and parts of the application remain usable offline.

---

<br/>

<div align="center">

### ◈ SECURITY IS NOT AN AFTERTHOUGHT

</div>

- Bot credentials are protected using Android Keystore-backed AES-256-GCM encryption.
- Telegram communication uses HTTPS.
- Telegram commands are restricted to authorized users.
- OTP filtering is optional. You can enable it to help prevent sensitive verification messages from being forwarded, or leave it disabled if you want those messages delivered normally.
- VANTA does not provide remote shell execution.

Your SMS messages are intentionally transmitted to the Telegram destination you configure. Keep your bot token private and only authorize people you trust.

---

<br/><br/>

<div align="center">

### ◈ GETTING STARTED

</div>

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

<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" /> <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" /> <img src="https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white" /> <img src="https://img.shields.io/badge/Telegram_Bot_API-26A5E4?style=flat-square&logo=telegram&logoColor=white" />

### ◈ LICENSE

VANTA is released under the **MIT License**.

See LICENSE for the full license text.

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&height=160&color=0:050505,25:17002f,50:4c1d95,75:17002f,100:050505&text=BEYOND%20THE%20ORDINARY.&fontColor=ffffff&fontSize=28&fontAlignY=55&animation=twinkling" width="100%" />

**MADE BY [SEFESHKI](https://github.com/Sefeshki)**

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases)

<sub>VANTA — Your device. Your connection. Your control.</sub>

</div>
