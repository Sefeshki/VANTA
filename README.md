<div align="center">
<img src="https://capsule-render.vercel.app/api?type=venom&height=230&color=0:000000,50:101010,100:000000&text=VANTA&fontColor=ffffff&fontSize=85&fontAlignY=45&desc=SMS%20BRIDGE%20%E2%80%A2%20ANDROID%20%E2%80%A2%20TELEGRAM&descAlignY=68&descSize=16&animation=fadeIn&stroke=ffffff&strokeWidth=1" width="100%" />

**Your phone. Your SIM. Your Telegram.**

A simple Android app that turns a spare phone into your personal SMS gateway.  
Messages arrive on your device and get forwarded straight to Telegram.

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

VANTA runs on your phone, connects to your own Telegram bot, and forwards incoming SMS messages to you. No extra servers. No third-party services. Just your phone and your Telegram.

It supports dual-SIM, works reliably in the background, and was optimized for low RAM and battery usage on both old and new phones.

---

### Features

<table>
<tr>
<td width="50%">

#### SMS Bridge
- Automatic SMS forwarding
- Dual-SIM support with individual controls
- Reply to SMS from Telegram
- Forward messages to other chats
- Duplicate protection
- Reliable background service

</td>
<td width="50%">

#### Telegram
- Your own bot
- Interactive buttons for everything
- Check status, SIMs, and forwarding
- Reply and forward right from the message

</td>
</tr>
<tr>
<td width="50%">

#### Privacy
- Credentials encrypted with Android Keystore
- HTTPS only
- Only authorized users can control it
- Optional OTP filtering
- Clean disconnect

</td>
<td width="50%">

#### System
- Live service monitoring
- Widgets for quick status
- Auto-start on boot
- Battery optimization tips
- Clear alerts when something goes wrong

</td>
</tr>
</table>

---

### SIM Management

There’s a simple section in the app where you can manage each SIM separately — turn forwarding on/off, see carrier info, and check status.

---

### Telegram Controls

Most things are controlled with buttons inside the bot.

When an SMS arrives you can reply using the same SIM or forward it to another Telegram chat. Replies show up under the original message so it’s easy to follow.

---

### Widgets & Monitoring

VANTA has widgets so you can see the live status without opening the app.

It also watches the service and notifies you if the connection drops, a SIM has problems, or forwarding fails — then tells you when it’s recovered.

---

### How it works

Everything runs on your phone and talks directly to Telegram.  
No extra server needed.

Your phone receives the SMS. With internet it gets sent to Telegram. Most settings still work offline.

---

### Security

Credentials are protected with Android Keystore encryption.  
Communication is HTTPS only.  
Only people you authorize can use the bot.  
OTP filtering is optional and stays off by default.

The whole point of the app is to send your SMS to the Telegram destination you choose. Keep your bot token private.

---

### Getting started

**1. Create your bot**  
Open [@BotFather](https://t.me/BotFather) → `/newbot` → copy the token.

**2. Connect VANTA**  
Install the app → paste the token → verify → generate pairing code → send `/start YOUR_CODE` to your bot.

**3. Permissions**  
Grant SMS, notification, and phone-state permissions. Set your SIM preferences.

**4. Turn it on**  
Enable forwarding on the Dashboard.

Done.

---

### Built with

<img src="https://img.shields.io/badge/Kotlin-ffffff?style=flat-square&logo=kotlin&logoColor=000000" /> <img src="https://img.shields.io/badge/Jetpack_Compose-ffffff?style=flat-square&logo=jetpackcompose&logoColor=000000" /> <img src="https://img.shields.io/badge/Android-ffffff?style=flat-square&logo=android&logoColor=000000" /> <img src="https://img.shields.io/badge/Telegram_Bot_API-ffffff?style=flat-square&logo=telegram&logoColor=000000" />

---

### License

Source-available under the PolyForm Noncommercial License 1.0.0.  
Free for noncommercial use. Commercial use needs permission.

See [LICENSE](https://github.com/Sefeshki/VANTA/blob/main/LICENSE) for details.

---

Found a bug or have a suggestion?  
Open an issue on GitHub or message me: [t.me/sefeshki](https://t.me/sefeshki)

---

<div align="center">
<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer&color=0:000000,50:101010,100:000000&text=BEYOND%20THE%20ORDINARY.&fontColor=ffffff&fontSize=25&fontAlignY=50&animation=fadeIn" width="100%" />

Made by [Sefeshki](https://github.com/Sefeshki)

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases)

<sub>VANTA — Your device. Your connection. Your control.</sub>
</div>
