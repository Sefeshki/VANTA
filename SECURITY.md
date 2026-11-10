# Security Policy — VANTA

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

Security is a foundational pillar of VANTA. If you discover a vulnerability or security issue, please do **NOT** open a public issue.

Instead, please report it privately:
- Contact the creator via GitHub: [https://github.com/Sefeshki/](https://github.com/Sefeshki/)

### Threat Model & Limitations

1. **Client-Side Bot Tokens**:
   - The Telegram bot token is stored locally using Android Keystore AES-256-GCM encryption.
   - Attackers with root access to the physical device may inspect memory. Use a dedicated secondary device.
2. **End-to-End Encryption Notice**:
   - Standard Telegram Bot API messages are encrypted in transit via TLS (HTTPS) between your Android phone and Telegram's servers, and between Telegram's servers and your Telegram client. They are not end-to-end encrypted like Secret Chats.
3. **No Covert Forwarding**:
   - VANTA strictly operates in the foreground with a permanent system notification. It cannot be converted into spyware or hidden surveillance software.
4. **Credential Purging**:
   - Disconnecting the bot from the UI immediately wipes all decrypted and encrypted credentials from persistent storage.
