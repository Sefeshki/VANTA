 <div align="center">

<img src="https://capsule-render.vercel.app/api?type=venom&height=230&color=0:000000,50:101010,100:000000&text=VANTA&fontColor=ffffff&fontSize=85&fontAlignY=45&desc=SMS%20BRIDGE%20%E2%80%A2%20ANDROID%20%E2%80%A2%20TELEGRAM&descAlignY=68&descSize=16&animation=fadeIn&stroke=ffffff&strokeWidth=1" width="100%" />

**Your phone. Your SIM. Your Telegram.**

An app that turns a spare phone into your personal SMS gateway. Incoming messages are forwarded straight to your own Telegram bot.

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

<div align="center">
  <img
    src="public/assets/images/vanta-showcase.png"
    alt="VANTA App Showcase"
    width="100%"
    style="border-radius: 18px;"
  />
</div>

Got an old Android phone? Give it a job.

VANTA turns it into your personal SMS gateway. It runs directly on your phone, forwarding incoming messages to your own Telegram bot without requiring a separate forwarding server or subscription service.

It supports dual-SIM devices, with independent controls for each SIM. VANTA targets Android 7.0 and newer, although available functionality can vary depending on the device and Android version.

**VANTA is a hands-on Android development project that I have built, tested, debugged, and continuously refined.** My work goes beyond the interface. I've worked on the complete process behind the application: Android SMS handling, telephony integration, subscription-aware SIM management, Telegram API communication, background execution, connectivity monitoring, and message delivery handling.

The project has involved bringing these different Android systems together and making them work as one application.

---

### Features

<p align="center">
  <img
    src="public/assets/images/VANTA_FEATURES_INFOGRAPHIC_3to1.png"
    alt="VANTA Features"
    width="100%"
    style="border-radius: 18px;"
  />
</p>

VANTA supports more than simply forwarding incoming messages.

You can reply to received SMS messages directly through Telegram, forward messages, or start completely new SMS conversations by entering a recipient number. When needed, you can choose which SIM sends the message.

Other features include:

- Independent forwarding and feature controls for each SIM.
- OTP and verification-message detection with configurable protection.
- Optional call-log forwarding and incoming-call notifications.
- Telegram bot pairing and authorized-chat management.
- Automatic connection monitoring and message delivery retries.

Messages are sent directly from your phone to your configured Telegram bot through the Telegram Bot API.

---

### How it works

VANTA runs on the Android device that receives the messages.

When an SMS arrives, Android delivers an SMS broadcast to VANTA. The app processes the message, identifies its SIM information, applies the relevant forwarding settings, and passes it to the delivery system.

The delivery system handles communication with Telegram through the official Bot API.

VANTA also monitors different parts of its operating state separately:

- **Internet connectivity:** Android's network callbacks track network availability.
- **Telegram reachability:** VANTA checks whether a TCP connection to Telegram's API endpoint can be established.
- **Forwarding service:** The app tracks whether its background forwarding service is active.

When connectivity is interrupted, VANTA attempts to recover automatically.

There is no separate VANTA forwarding server involved. The phone handles the forwarding process itself.

---

### Engineering & implementation

VANTA is built with Kotlin, Jetpack Compose, and native Android APIs.

The application is divided into dedicated components, each responsible for a different part of the system.

| Component | Implementation |
|---|---|
| SMS reception | Android `BroadcastReceiver` and SMS processing |
| Message processing | Multipart SMS reconstruction, SIM identification, and OTP detection |
| SIM management | Android `SubscriptionManager`, system observers, and `StateFlow` |
| Telegram integration | Kotlin API interface and OkHttp networking |
| Background execution | Android foreground service and notification controls |
| Network monitoring | `ConnectivityManager`, network callbacks, and endpoint probes |
| Message delivery | Coroutine worker, synchronized queue operations, and retry handling |
| Call monitoring | Android phone-state broadcasts and call-log observation |

**Message processing**

The `SmsReceiver` handles Android's incoming SMS broadcast and delegates processing to `SmsProcessor`.

The processor reconstructs multipart messages, extracts sender and timestamp information, resolves SIM routing information, and identifies OTP-like messages. It then applies global and per-SIM forwarding restrictions before a message enters the delivery queue.

**SIM management**

`SimManager` uses Android telephony and subscription APIs to detect active SIM subscriptions. It also observes subscription changes and SIM state broadcasts so that SIM information can be refreshed when the device configuration changes.

Detected SIM information is exposed through Kotlin `StateFlow`.

**Message delivery**

`DeliveryQueueManager` uses a coroutine worker to process pending messages. Queue access is coordinated with a `Mutex`, while SHA-256 fingerprints are used to identify duplicate messages and prevent repeated forwarding.

Failed deliveries can be attempted again up to five times, using increasing retry delays capped at 30 seconds.

The current delivery queue is held in memory rather than persisted across process restarts.

**Network and background service**

`NetworkMonitor` tracks network availability through Android connectivity callbacks and performs periodic TCP reachability checks against `api.telegram.org` on port 443.

`VantaForegroundService` manages foreground execution, service notifications, pause and resume actions, and the application's background service state.

These components work together to handle the Android side of the bridge without relying on a separate VANTA server.

**Explore the implementation**

- [SMS Receiver](app/src/main/java/com/example/sms/SmsReceiver.kt)
- [SMS Processor](app/src/main/java/com/example/sms/SmsProcessor.kt)
- [SIM Manager](app/src/main/java/com/example/telephony/SimManager.kt)
- [Delivery Queue Manager](app/src/main/java/com/example/data/queue/DeliveryQueueManager.kt)
- [Telegram API Client](app/src/main/java/com/example/telegram/TelegramApiClient.kt)
- [Network Monitor](app/src/main/java/com/example/core/NetworkMonitor.kt)
- [Foreground Service](app/src/main/java/com/example/service/VantaForegroundService.kt)
- [Call Monitor Manager](app/src/main/java/com/example/telephony/CallMonitorManager.kt)

---

### Security

<div align="center">
  <img
    src="public/assets/images/security_and_technology.png"
    alt="VANTA Security and Technology"
    width="100%"
    style="border-radius: 18px;"
  />
</div>

Security is an important part of VANTA because the application handles personal messages and Telegram bot credentials.

The Telegram bot token is an authentication credential and should never be shared publicly or included in bug reports.

VANTA encrypts the stored token before saving it in app-private preferences. The implementation can be inspected in [SecurityManager.kt](app/src/main/java/com/example/core/SecurityManager.kt) and [PreferencesManager.kt](app/src/main/java/com/example/core/PreferencesManager.kt).

VANTA communicates directly with Telegram through its official Bot API rather than routing messages through a separate forwarding server.

Keep in mind that enabling forwarding means the selected SMS content is transmitted to the Telegram chat associated with your bot. Only enable forwarding for messages you are comfortable sending there.

For security concerns and responsible vulnerability reporting, see [SECURITY.md](SECURITY.md).

---

### Getting started

<div align="center">
  <img
    src="public/assets/images/getting_started.png"
    alt="Getting Started with VANTA"
    width="100%"
    style="border-radius: 18px;"
  />
</div>

**1. Create your bot**

Open [@BotFather](https://t.me/BotFather), send `/newbot`, choose a name and username, and copy your API token.

**2. Connect VANTA**

Install the app, paste your token into Telegram Setup, verify it, and pair your account using the generated code:

`/start YOUR_CODE`

**3. Grant permissions**

Use **Grant All Permissions** in the Permission Center, or configure permissions individually. Follow the battery optimization guidance too.

**4. Turn it on**

Return to the Dashboard and enable forwarding.

That's it. Your phone is now your SMS bridge.

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

<sub>Built through hands-on development, testing, debugging, and continuous improvement. If you appreciate the work, a <a href="https://github.com/Sefeshki/VANTA">GitHub star</a> helps motivate future development.</sub>

</div>
