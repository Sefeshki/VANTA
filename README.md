 <div align="center">

<img src="https://capsule-render.vercel.app/api?type=venom&height=230&color=0:000000,50:101010,100:000000&text=VANTA&fontColor=ffffff&fontSize=85&fontAlignY=45&desc=ANDROID%20SMS%20BRIDGE%20%E2%80%A2%20TELEGRAM&descAlignY=68&descSize=16&animation=fadeIn&stroke=ffffff&strokeWidth=1" width="100%" />

### Your phone. Your SIM. Your Telegram.

A personal Android SMS-to-Telegram bridge. Turn a spare Android phone into a remotely accessible SMS gateway.

**Built with Kotlin and Jetpack Compose.**

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

### ◈ OVERVIEW

</div>

VANTA turns an Android phone with an active SIM into an SMS gateway connected to your own Telegram bot.

Incoming messages are received by the phone and delivered to your configured Telegram destination. You can monitor SIM availability, manage forwarding, reply to SMS messages, and interact with your device through Telegram.

VANTA runs its bridge directly on Android. It does not require a separate forwarding server.

### ◈ FEATURES

<table>
<tr>
<td width="50%">

<h3>SMS MANAGEMENT</h3>

- Automatic incoming SMS forwarding
- Individual SIM identification
- Dual-SIM and single-SIM support
- SIM-specific forwarding controls
- SMS filtering and duplicate protection
- OTP forwarding and protection settings
- SMS replies directly through Telegram
- Forward SMS messages to configured Telegram destinations

</td>
<td width="50%">

<h3>TELEGRAM INTEGRATION</h3>

- Personal Telegram bot integration
- Interactive inline keyboard navigation
- Live service and SIM status
- SMS Reply and Cancel interactions
- Forward action on SMS notifications
- Reply confirmation attached to original messages
- Authorized Telegram user verification
- Bot pairing and connection management

</td>
</tr>
<tr>
<td width="50%">

<h3>SIM INTELLIGENCE</h3>

- Accurate Android subscription identification
- SIM1 / SIM2 detection
- Carrier information where available
- Incoming and outgoing SIM attribution
- SIM availability monitoring
- Independent per-SIM configuration
- Support for SIM changes and disconnections

</td>
<td width="50%">

<h3>SYSTEM MONITORING</h3>

- Live Internet connectivity status
- Telegram API reachability monitoring
- Forwarding service status
- SIM availability monitoring
- Service failure notifications
- Connection recovery notifications
- Permission status monitoring
- Automatic startup support

</td>
</tr>
<tr>
<td width="50%">

<h3>HOME SCREEN WIDGETS</h3>

- Four dedicated Android widget designs
- Compact connectivity widget
- Dual-SIM status widget
- Extended status panel
- Portrait-oriented full overview
- Adaptive layout and resizing
- Automatic status updates
- No unnecessary operational controls

</td>
<td width="50%">

<h3>PRIVACY & SECURITY</h3>

- Android Keystore-backed encryption
- AES-256-GCM credential protection
- HTTPS Telegram communication
- Authorized Telegram user checks
- Optional OTP protection
- Secure credential handling
- No remote shell execution

</td>
</tr>
</table>

### ◈ FOUR WIDGET DESIGNS

VANTA includes four dedicated Android home-screen widgets. Each has its own layout rather than relying on one interface stretched across different dimensions.

| Widget | Size | Information |
|:--|:--:|:--|
| Compact | 2×1 | Internet and Telegram connectivity |
| SIM Status | 2×2 | SIM availability and forwarding |
| Status Panel | 4×2 | Connectivity, service, and both SIMs |
| Full Overview | 2×4 | Detailed portrait-oriented system overview |

Widgets display actual VANTA state and update as relevant system conditions change.

### ◈ TELEGRAM INTERACTION

VANTA uses interactive Telegram buttons to make device management easier without relying on slash commands for everyday operation.

**Message actions**

- **Reply** — Send an SMS through the Android device using its originating SIM.
- **Forward** — Forward an SMS to another configured Telegram destination.
- **Cancel** — Cancel an active reply session without sending anything.

Successful replies are displayed beneath the original forwarded SMS, keeping the received message and response together.

**Management interface**

Use inline keyboard navigation to access available actions, including:

- Overview and live status
- SIM information
- Service status
- Forwarding status
- Available service controls

### ◈ HOW IT WORKS

1. An SMS arrives on the Android device.
2. Android identifies the receiving SIM and message timestamp.
3. VANTA processes the message according to the configured forwarding and privacy settings.
4. The Telegram Bot API delivers the message to the authorized destination.
5. You can reply, forward the message, or access available management actions through Telegram.

SMS reception happens locally on the Android device. Telegram delivery and remote interaction require an Internet connection.

### ◈ GETTING STARTED

**01 — Create a Telegram bot**

Open [@BotFather](https://t.me/BotFather) in Telegram.

Send `/newbot`, choose a bot name and username, and copy the generated API token.

**02 — Connect VANTA**

- Install VANTA on your Android device.
- Open Telegram Setup.
- Enter your bot token.
- Verify the connection.
- Generate your pairing code.
- Open your bot and complete the pairing process using the displayed instructions.

**03 — Configure permissions**

Open Permission Center and grant the permissions required for your selected features.

Configure SMS access, notifications, phone-state access, and any additional permissions requested by enabled functionality.

**04 — Configure SIM Control**

Choose which SIMs should be active and configure their individual forwarding options.

Review OTP protection and other privacy settings.

**05 — Activate**

Return to the Dashboard and enable the forwarding service.

Your Android device is now connected to your Telegram bot.

### ◈ SYSTEM REQUIREMENTS

- Android 10 or newer, subject to device and manufacturer restrictions.
- An Android device with an active SIM for SMS reception.
- Internet connectivity for Telegram communication.
- A Telegram account and a bot created through BotFather.
- Required Android permissions for the features you enable.

Some functionality depends on Android version, manufacturer restrictions, carrier support, and available device hardware.

### ◈ SECURITY & PRIVACY

- Bot credentials are protected using Android Keystore-backed AES-256-GCM encryption.
- Telegram communication uses HTTPS.
- Bot interactions are restricted to authorized users.
- OTP filtering and protection are configurable.
- Credentials are handled securely and are not intended to appear in application logs.

**Important:** SMS messages forwarded to Telegram leave the device and are delivered to the Telegram destinations you configure. Only authorize destinations and users you trust. VANTA does not make Telegram a private or end-to-end encrypted SMS storage system.

### ◈ BUILT WITH

<img src="https://img.shields.io/badge/Kotlin-ffffff?style=flat-square&logo=kotlin&logoColor=000000" /> <img src="https://img.shields.io/badge/Jetpack_Compose-ffffff?style=flat-square&logo=jetpackcompose&logoColor=000000" /> <img src="https://img.shields.io/badge/Android-ffffff?style=flat-square&logo=android&logoColor=000000" /> <img src="https://img.shields.io/badge/Telegram_Bot_API-ffffff?style=flat-square&logo=telegram&logoColor=000000" />

### ◈ LICENSE

VANTA is **Source-Available** under the **PolyForm Noncommercial License 1.0.0**.

Noncommercial use is permitted under the license terms. Commercial use requires separate permission from the author.

See [LICENSE](https://github.com/Sefeshki/VANTA/blob/main/LICENSE) for the complete terms.

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer&color=0:000000,50:101010,100:000000&text=BEYOND%20THE%20ORDINARY.&fontColor=ffffff&fontSize=25&fontAlignY=50&animation=fadeIn" width="100%" />

**MADE BY [SEFESHKI](https://github.com/Sefeshki)**

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases)

<sub>VANTA — Your device. Your connection. Your control.</sub>

</div>
