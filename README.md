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

I've also put a lot of work into making VANTA run well on both older and newer Android phones. It's designed to stay lightweight, use as little RAM and battery as possible, and keep running in the background without demanding too much from your device.

This isn't a quick app thrown together with a few prompts. I've spent a lot of time building it, working through the details, and improving how it behaves. Security in particular has been a major priority throughout development.

---

### Features

<table>
<tr>
<td width="50%">

#### SMS & SIM Management

- Automatic incoming SMS forwarding
- Dual-SIM support with dedicated SIM management
- Independent forwarding controls for each SIM
- Accurate SIM and carrier identification
- Reply to SMS through the original receiving SIM
- Cancel pending replies
- Forward messages to other configured Telegram destinations
- Duplicate message protection

</td>
<td width="50%">

#### Telegram

- Connect your own Telegram bot
- Secure token verification and bot pairing
- Interactive inline keyboard controls
- Check service, SIM, and forwarding status
- Reply and forward directly from incoming messages
- Built-in BotFather setup guidance

</td>
</tr>
<tr>
<td width="50%">

#### Monitoring & Performance

- Live internet, Telegram, and forwarding status
- Connection checks and automatic recovery
- Notifications for connection, SIM, permission, and forwarding problems
- Recovery notifications when problems are resolved
- Four Android home-screen widgets
- Automatic startup on boot
- Low RAM and battery usage
- Support for older and newer Android phones

</td>
<td width="50%">

#### Security & Settings

- Android Keystore-backed AES-256-GCM encryption
- HTTPS communication with Telegram
- Only authorized Telegram users can interact with the bot
- Optional OTP protection, off by default
- Secure credential cleanup on disconnect
- Purple, Red, Blue, and Cyan themes
- Permission Center and service settings
- No remote shell execution

</td>
</tr>
</table>

When you receive an SMS, you can reply directly from Telegram. VANTA asks you to enter your reply and provides a Cancel option. The message is sent through the SIM that originally received the SMS. Once Android confirms it was sent successfully, the reply is added beneath the original Telegram message.

You can also forward an SMS to another configured Telegram destination. This is Telegram forwarding, not a replacement for the phone's native SMS conversations.

The four home-screen widgets give you different ways to check your device, SIMs, and service status without opening the app. Tapping a widget opens VANTA.

---

### How it works

Everything runs on your phone and communicates directly with the official Telegram Bot API.

Your phone receives the SMS and sends it to Telegram over the internet. VANTA also keeps track of internet connectivity, Telegram availability, and forwarding status separately. Being connected to the internet doesn't necessarily mean Telegram is reachable or that forwarding is active.

Most application settings and diagnostics remain available offline. When the connection is unavailable, VANTA works to restore communication automatically.

---

### Security

Security is something I've spent a lot of time working on. Since VANTA handles personal SMS messages and Telegram bot credentials, I didn't want to treat it as an afterthought.

Bot credentials are encrypted using Android Keystore-backed AES-256-GCM encryption. Communication with Telegram uses HTTPS, and only authorized Telegram users can interact with the bot.

OTP protection is optional and disabled by default. Credentials are securely cleared when you disconnect your bot.

VANTA does not provide remote shell access or remote command execution. Your SMS messages are sent to the Telegram destination you configure, so keep your bot token private and only authorize people you trust.

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

### Updates

VANTA checks GitHub for a newer release when the app starts. When an update is available, you'll see an update option in the app.

You can also find the latest version on the [Releases page](https://github.com/Sefeshki/VANTA/releases).

---

### License

Source-available under the [PolyForm Noncommercial License 1.0.0](https://github.com/Sefeshki/VANTA/blob/main/LICENSE).

Free for noncommercial use. Commercial use requires permission from the author.

See [LICENSE](https://github.com/Sefeshki/VANTA/blob/main/LICENSE) for details.

---

Found a bug, ran into an issue, or have an idea for VANTA? Feel free to report it through [GitHub Issues](https://github.com/Sefeshki/VANTA/issues) or message me directly on [Telegram](https://t.me/sefeshki).

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer&color=0:000000,50:101010,100:000000&text=BEYOND%20THE%20ORDINARY.&fontColor=ffffff&fontSize=25&fontAlignY=50&animation=fadeIn" width="100%" />

Made by [Sefeshki](https://github.com/Sefeshki)

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases)

<sub>VANTA — Your device. Your connection. Your control.</sub>

</div>
