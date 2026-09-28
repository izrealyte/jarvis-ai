# JARVIS Mobile — Command Router + HUD Upgrade

This source package keeps the phone-first JARVIS architecture and upgrades the runtime so explicit device/app commands are routed to Android actions before web search.

Key behavior:
- `open TikTok`, `launch WhatsApp`, `start YouTube`, etc. search installed launcher apps and launch them.
- `go home`, `go back`, `recent apps`, `notifications`, `quick settings`, screenshot, volume and flashlight commands route to Android capabilities where available.
- Explicit app/action commands never fall through to Google search.
- Web search happens only when the user explicitly says `search`, `google`, `look up`, or `search the web` (or when an unknown knowledge question reaches the current fallback).
- A lightweight non-touch JARVIS HUD appears when overlay permission is enabled.
- The HUD is hidden before launching another app and the Accessibility service can hide/show it as foreground apps change.
- PC bridge remains optional.

Build: open this folder in Android Studio, sync Gradle, and Run on the phone.
