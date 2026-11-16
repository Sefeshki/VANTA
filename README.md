<div align="center">

<img src="https://capsule-render.vercel.app/api?type=venom&height=230&color=0:000000,50:101010,100:000000&text=VANTA&fontColor=ffffff&fontSize=85&fontAlignY=45&desc=SMS%20BRIDGE%20%E2%80%A2%20ANDROID%20%E2%80%A2%20TELEGRAM&descAlignY=68&descSize=16&animation=fadeIn&stroke=ffffff&strokeWidth=1" width="100%" />

**Your phone. Your SIM. Your Telegram.**

An Android app that turns a spare phone into your personal SMS gateway. Incoming messages are forwarded straight to your own Telegram bot.

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

VANTA runs directly on your phone, connects to your own Telegram bot, and forwards incoming SMS messages to you. No separate server or third-party forwarding service is needed.

It supports dual-SIM devices, with a dedicated section for managing each SIM independently.

I've also put a lot of work into making VANTA run well on older and newer Android phones. It's designed to stay lightweight, use as little RAM and battery as possible, and keep running in the background without demanding too much from your device.

VANTA isn't a vibe-coded project. I've spent a lot of time building it, testing its features, fixing problems, and working on the details. Security has been a major priority throughout development, and I've done my best to make the application as secure as possible.

---

### Features

<table>
<tr>
<td width="50%" valign="top">

#### SMS & SIM Management

- Automatic incoming SMS forwarding
- Dual-SIM support
- Dedicated SIM management
- Individual forwarding controls
- SIM and carrier identification
- Duplicate message protection
- Reply to and forward SMS from Telegram

</td>
<td width="50%" valign="top">

#### Telegram

- Connect your own Telegram bot
- Bot token verification and secure pairing
- Interactive buttons to control VANTA
- Check SIM, service, and forwarding status
- Reply to or forward incoming messages
- Built-in BotFather setup guidance

</td>
</tr>
<tr>
<td width="50%" valign="top">

#### Monitoring & Widgets

- Live internet and Telegram connection status
- Live forwarding status
- Notifications when something goes wrong or recovers
- SIM and permission problem notifications
- Four home-screen widgets with live status
- Automatic startup on boot
- Connection checks and recovery

</td>
<td width="50%" valign="top">

#### Performance & Security

- Optimized for older and newer Android phones
- Low RAM and battery usage
- Lightweight background operation
- Android Keystore-backed AES-256-GCM encryption
- HTTPS communication with Telegram
- Authorized Telegram users only
- Optional OTP protection
- Secure credential cleanup
- Purple, Red, Blue, and Cyan themes

</td>
</tr>
</table>

When you receive an SMS, you can reply to it or forward it directly from Telegram. Your messages go straight from your phone to your own Telegram bot, without passing through another forwarding service.

VANTA also comes with home-screen widgets that show its live status, so you can check what's happening without opening the app.

---

### How it works

It's pretty straightforward. Your phone receives the SMS and VANTA sends it directly to your Telegram bot using the official Telegram Bot API.

There is no separate server involved. VANTA keeps track of your internet connection, Telegram connection, and forwarding service separately, so you can see whether everything is actually working.

When a connection drops, VANTA attempts to restore it automatically.

---

### Security

I've spent a lot of time working on VANTA's security. Since the app handles personal messages and Telegram bot credentials, this was never something I wanted to overlook.

Bot credentials are encrypted using Android Keystore-backed AES-256-GCM encryption. All communication with Telegram uses HTTPS, and only authorized Telegram users can interact with the bot.

OTP protection is optional and turned off by default. Credentials are securely cleared when you disconnect your bot.

VANTA does not provide remote shell access or remote command execution. Your SMS messages are forwarded to the Telegram destination you configure. Keep your bot token private and only authorize people you trust.

---

### Getting started

**1. Create your bot**

Open [@BotFather](https://t.me/BotFather), send `/newbot`, choose a name and username, and copy your API token.

**2. Connect VANTA**

Install the app, open Telegram Setup, paste your token, and verify it. Generate a pairing code, open your bot, and send:

`/start YOUR_CODE`

**3. Grant permissions**

Open the Permission Center and grant the required permissions. Configure your SIMs and check the battery optimization guidance.

**4. Turn it on**

Go back to the Dashboard and enable forwarding.

That's it. Your phone is now your SMS bridge.

---

### Built with

<img src="https://img.shields.io/badge/Kotlin-ffffff?style=flat-square&logo=kotlin&logoColor=000000" /> <img src="https://img.shields.io/badge/Jetpack_Compose-ffffff?style=flat-square&logo=jetpackcompose&logoColor=000000" /> <img src="https://img.shields.io/badge/Android-ffffff?style=flat-square&logo=android&logoColor=000000" /> <img src="https://img.shields.io/badge/Telegram_Bot_API-ffffff?style=flat-square&logo=telegram&logoColor=000000" />

---

### Updates

VANTA checks GitHub for new releases when the app starts. When an update is available, you'll see an update option in the app.

You can also find the latest version on the [Releases page](https://github.com/Sefeshki/VANTA/releases).

---

### License

VANTA is source-available under the [PolyForm Noncommercial License 1.0.0](https://github.com/Sefeshki/VANTA/blob/main/LICENSE).

Free for noncommercial use. Commercial use requires permission from the author.

See [LICENSE](https://github.com/Sefeshki/VANTA/blob/main/LICENSE) for details.

---

Found a bug, ran into a problem, or have a suggestion? Feel free to open an issue on [GitHub](https://github.com/Sefeshki/VANTA/issues) or message me directly on [Telegram](https://t.me/sefeshki).

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer&color=0:000000,50:101010,100:000000&text=BEYOND%20THE%20ORDINARY.&fontColor=ffffff&fontSize=25&fontAlignY=50&animation=fadeIn" width="100%" />

Made by [Sefeshki](https://github.com/Sefeshki)

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases)

<sub>VANTA — Your device. Your connection. Your control.</sub>

</div>
