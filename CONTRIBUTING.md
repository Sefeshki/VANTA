# Contributing to VANTA

Thank you for your interest in contributing to **VANTA**, an open-source project created by **[Sefeshki](https://github.com/Sefeshki/)**!

## Code of Conduct

- Be respectful and collaborative.
- Adhere to the core privacy principles of VANTA: **no covert forwarding, no telemetry tracking, and zero spyware features**.

## Pull Request Guidelines

1. **Architecture Consistency**:
   - Keep business logic decoupled from UI components.
   - Use Kotlin coroutines and Flow for all asynchronous operations.
   - Follow the 4-theme system (`Theme.kt`).
2. **Security & Privacy First**:
   - Never commit API keys, bot tokens, or private phone numbers.
   - Do not log sensitive SMS bodies or OTP codes in `EventLogger`.
3. **Automated Testing**:
   - Run tests before submitting PRs:
     ```bash
     gradle :app:testDebugUnitTest
     ```
   - Add tests for any new SMS filters, entity builders, or network handling.

## Development Setup

- Android Studio Ladybug or newer.
- JDK 17 / 21.
- Gradle with Kotlin DSL.
