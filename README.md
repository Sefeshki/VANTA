 <div align="center">

<img src="https://capsule-render.vercel.app/api?type=venom&height=230&color=0:000000,50:101010,100:000000&text=VANTA&fontColor=ffffff&fontSize=85&fontAlignY=45&desc=SMS%20BRIDGE%20%E2%80%A2%20ANDROID%20%E2%80%A2%20TELEGRAM&descAlignY=68&descSize=16&animation=fadeIn&stroke=ffffff&strokeWidth=1" width="100%" />

**Your phone. Your SIM. Your Telegram.**

A simple Android app that turns a spare phone into your personal SMS gateway. Messages arrive on your device and get forwarded straight to Telegram.

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

Got an old Android phone? Give it a job.

VANTA runs on your phone, connects to your own Telegram bot, and forwards incoming SMS messages to you. No extra servers or third-party forwarding services.

It supports dual-SIM devices and is designed for reliable background operation on both older and newer Android phones.

---

### Features

<table>
<tr>
<td width="50%">

#### SMS & SIM Management

- Automatic incoming SMS forwarding
- Dual-SIM support with dedicated SIM management
- Individual forwarding controls for each SIM
- Accurate SIM identification and carrier information
- Reply to SMS through Telegram using the original SIM
- Forward messages to other configured Telegram destinations
- Duplicate message protection

</td>
<td width="50%">

#### Telegram

- Connect your own Telegram bot
- Secure bot setup, verification, and pairing
- Interactive inline keyboard controls
- View service status, SIM information, and forwarding status
- Reply and forward directly from incoming messages
- Built-in BotFather setup guidance

</td>
</tr>
<tr>
<td width="50%">

#### Monitoring & Widgets

- Live internet, Telegram, and forwarding status
- Connection diagnostics and automatic recovery
- Notifications for connection, SIM, permission, and forwarding issues
- Recovery notifications when problems are resolved
- Four Android home-screen widgets for quick status checks
- Optional automatic startup on boot
- Battery optimization guidance

</td>
<td width="50%">

#### Privacy & Settings

- Android Keystore-backed AES-256-GCM credential encryption
- HTTPS communication with Telegram
- Authorized Telegram user checks
- Optional OTP protection, disabled by default
- Secure credential cleanup on disconnect
- Customizable accent themes
- Permission Center and service settings
- No remote shell execution

</td>
</tr>
</table>

Incoming messages show which SIM received them. When replying, VANTA uses that same SIM. The bot lets you enter a reply or cancel it, and a successful reply is added beneath the original Telegram message. Forwarding sends the message to another configured Telegram destination.

The widgets provide different layouts for checking your device, SIMs, connectivity, and forwarding status without opening the app. Tapping one opens VANTA.

---

### How it works

Everything runs on your phone and communicates directly with the official Telegram Bot API. No separate server is needed.

Your phone receives the SMS and sends it to Telegram when an internet connection is available. VANTA monitors internet connectivity separately from Telegram availability and forwarding status, so being online doesn't automatically mean the service is working.

---

### Security

Credentials are protected using Android Keystore-backed AES-256-GCM encryption. All communication with Telegram uses HTTPS, and only authorized users can interact with your bot.

OTP protection is optional and disabled by default. Credentials are cleared when you disconnect.

Your SMS messages are sent to the Telegram destination you configure. Keep your bot token private and only authorize people you trust.

---

### Getting started

**1. Create your bot**

Open [@BotFather](https://t.me/BotFather), send `/newbot`, choose a name and username, then copy the API token.

**2. Connect VANTA**

Install the app, open Telegram Setup, paste your token, and verify it. Generate a pairing code, open your bot, and send:

`/start YOUR_CODE`

**3. Grant permissions**

Open the Permission Center and grant the required SMS, notification, and phone-state permissions. Configure your SIMs and check the battery optimization guidance.

**4. Turn it on**

Return to the Dashboard and enable forwarding.

That's it. Your phone is now your SMS bridge.

---

### Built with

<img src="https://img.shields.io/badge/Kotlin-ffffff?style=flat-square&logo=kotlin&logoColor=000000" /> <img src="https://img.shields.io/badge/Jetpack_Compose-ffffff?style=flat-square&logo=jetpackcompose&logoColor=000000" /> <img src="https://img.shields.io/badge/Android-ffffff?style=flat-square&logo=android&logoColor=000000" /> <img src="https://img.shields.io/badge/Telegram_Bot_API-ffffff?style=flat-square&logo=telegram&logoColor=000000" />

---

### License

Source-available under the [PolyForm Noncommercial License 1.0.0](https://github.com/Sefeshki/VANTA/blob/main/LICENSE).

Free for noncommercial use. Commercial use requires permission from the author.

See [LICENSE](https://github.com/Sefeshki/VANTA/blob/main/LICENSE) for details.

---

Found a bug or have a suggestion? Open an issue on GitHub or message me: [t.me/sefeshki](https://t.me/sefeshki)

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer&color=0:000000,50:101010,100:000000&text=BEYOND%20THE%20ORDINARY.&fontColor=ffffff&fontSize=25&fontAlignY=50&animation=fadeIn" width="100%" />

Made by [Sefeshki](https://github.com/Sefeshki)

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases)

<sub>VANTA — Your device. Your connection. Your control.</sub>
</div>
