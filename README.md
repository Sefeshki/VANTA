 <div align="center">

<img src="https://capsule-render.vercel.app/api?type=venom&height=190&color=0:090909,50:241044,100:090909&text=VANTA&fontColor=ffffff&fontSize=72&fontAlignY=45&desc=SMS%20%E2%86%92%20TELEGRAM&descAlignY=68&descSize=16&animation=fadeIn" width="100%" />

**Your phone. Your SIM. Your Telegram.**

A small Android app that forwards incoming SMS messages to your Telegram bot. Simple idea. Useful little tool.

[GitHub](https://github.com/Sefeshki/VANTA) · [Releases](https://github.com/Sefeshki/VANTA/releases) · [Report an issue](https://github.com/Sefeshki/VANTA/issues)

</div>

---

### What it does

VANTA turns an Android phone into an SMS-to-Telegram bridge.

- **SMS forwarding** — sends incoming messages to your Telegram.
- **Dual SIM** — use both SIMs or choose one.
- **OTP filtering** — helps detect verification codes and sensitive messages.
- **Stay Alive** — keeps the forwarding service running in the background.

### How it works

<table>
<tr>
<td align="center" width="33%">
  <img src="https://img.shields.io/badge/01-ANDROID-111111?style=for-the-badge" />
  <br/><br/>
  Your phone receives an SMS.
</td>
<td align="center" width="33%">
  <img src="https://img.shields.io/badge/02-VANTA-241044?style=for-the-badge" />
  <br/><br/>
  VANTA processes it and forwards it securely.
</td>
<td align="center" width="33%">
  <img src="https://img.shields.io/badge/03-TELEGRAM-111111?style=for-the-badge" />
  <br/><br/>
  Your bot delivers it to your Telegram.
</td>
</tr>
</table>

### Privacy

Your messages are sent to the Telegram bot you configure. VANTA communicates with the official Telegram Bot API over HTTPS.

Your bot token is sensitive: keep it private and only share it with people you trust. You can disconnect your bot from the app.

### Does it work offline?

The app and its local settings remain available without internet. But forwarding messages to Telegram requires an internet connection. Messages received while offline cannot be delivered until connectivity returns, depending on the app's delivery and retry behavior.

### Getting started

1. Install VANTA on your Android phone.
2. Create a bot through [@BotFather](https://t.me/BotFather).
3. Paste its token into VANTA.
4. Complete the pairing process with your Telegram account.
5. Grant the required permissions.

That's it.

---

<div align="center">

**Made by [Sefeshki](https://github.com/Sefeshki)**

<sub>Built for Android. Made to do one job well.</sub>

<img src="https://capsule-render.vercel.app/api?type=rect&height=2&color=0:090909,50:7040b8,100:090909" width="100%" />

</div>
