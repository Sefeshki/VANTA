<div align="center">
<img src="https://capsule-render.vercel.app/api?type=venom&height=230&color=0:000000,50:101010,100:000000&text=VANTA&fontColor=ffffff&fontSize=85&fontAlignY=45&desc=SMS%20BRIDGE%20%E2%80%A2%20ANDROID%20%E2%80%A2%20TELEGRAM&descAlignY=68&descSize=16&animation=fadeIn&stroke=ffffff&strokeWidth=1" width="100%" />

**Your phone. Your SIM. Your Telegram.**

A simple Android app that turns a spare phone into your personal SMS gateway.  
Messages arrive on your device and get forwarded straight to Telegram — no extra servers, no third-party services.

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

Got an old Android phone collecting dust? Give it a useful job.

VANTA runs on your device, connects to a Telegram bot you control, and quietly forwards incoming SMS messages to you. Everything stays on your phone and your Telegram account — nothing in between.

---

### Features

<table>
<tr>
<td width="50%">

#### SMS Bridge
- Automatic forwarding of incoming SMS
- Dual-SIM support with clear identification
- Filter by SIM1, SIM2, or both
- Individual SIM forwarding controls
- Duplicate message protection
- Reply to SMS directly from Telegram
- Forward messages to other Telegram chats
- Runs reliably in the background

</td>
<td width="50%">

#### Telegram
- Works with your own Telegram bot
- Direct connection to the official Bot API
- Simple and secure bot pairing
- Interactive inline keyboards
- Reply and forward actions right from the message
- Live SIM and service status

</td>
</tr>
<tr>
<td width="50%">

#### Privacy
- Credentials protected with Android Keystore (AES-256-GCM)
- HTTPS for all Telegram communication
- Only authorized Telegram users can interact
- Optional OTP filtering
- Credentials cleared when you disconnect

</td>
<td width="50%">

#### System
- Live service monitoring
- Connection diagnostics
- Automatic start on boot (optional)
- Battery optimization guidance
- SIM and carrier information
- Service failure & recovery notifications
- Customizable home screen widgets
- Friendly first-launch tutorial

</td>
</tr>
</table>

---

### Telegram Interaction

Once connected, you can talk to your phone through the bot.

Use the buttons to check service status, view SIM details, or manage forwarding. When an SMS arrives, you can reply using the original SIM or forward the message to another Telegram chat you choose. Replies appear under the original message so conversations stay easy to follow.

---

### How it works

VANTA lives on your Android phone and talks directly to the official Telegram Bot API.  
There’s no separate server to set up or maintain.

Your phone receives the SMS. As long as it has internet, the message is delivered to Telegram. Most settings and diagnostics still work offline.

---

### Security notes

Bot credentials are encrypted with Android Keystore (AES-256-GCM).  
All communication with Telegram uses HTTPS.  
Only the Telegram users you authorize can send commands.  
OTP filtering is available if you want it.

VANTA does not give remote shell access or anything like that.  
Your SMS messages go only to the Telegram destination you configure — so keep your bot token private and only authorize people you trust.

---

### Getting started

**1. Create your bot**  
Open [@BotFather](https://t.me/BotFather) in Telegram.  
Send `/newbot`, pick a name and username, then copy the API token.

**2. Connect VANTA**  
Install the app on your Android phone.  
Go to Telegram Setup → paste your bot token → verify it.  
Generate a pairing code, open your bot, and send:  
`/start YOUR_CODE`

**3. Grant permissions**  
Open the Permission Center and allow SMS, notifications, and phone state access.  
Set your SIM filters and check the battery optimization tips.

**4. Turn it on**  
Go back to the Dashboard and enable forwarding.

That’s it. Your phone is now your SMS bridge.

---

### Built with

<img src="https://img.shields.io/badge/Kotlin-ffffff?style=flat-square&logo=kotlin&logoColor=000000" /> <img src="https://img.shields.io/badge/Jetpack_Compose-ffffff?style=flat-square&logo=jetpackcompose&logoColor=000000" /> <img src="https://img.shields.io/badge/Android-ffffff?style=flat-square&logo=android&logoColor=000000" /> <img src="https://img.shields.io/badge/Telegram_Bot_API-ffffff?style=flat-square&logo=telegram&logoColor=000000" />

---

### License

VANTA is source-available under the [PolyForm Noncommercial License 1.0.0](https://github.com/Sefeshki/VANTA/blob/main/LICENSE).  

Free for non-commercial use. Commercial use needs permission from the author.

---

<div align="center">
<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer&color=0:000000,50:101010,100:000000&text=BEYOND%20THE%20ORDINARY.&fontColor=ffffff&fontSize=25&fontAlignY=50&animation=fadeIn" width="100%" />

Made by [Sefeshki](https://github.com/Sefeshki)

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases)

<sub>VANTA — Your device. Your connection. Your control.</sub>
</div>
