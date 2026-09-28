# JARVIS Upgrade Notes — September 2026

This package is an upgrade of the supplied JARVIS Command Center. The original uploaded archive is not modified.

## What changed

- Rebuilt the visual HUD around the supplied `public/assets/download.jpg` JARVIS artwork instead of the old generic central orb presentation.
- Added a three-column command-center layout with live command feed, system telemetry, connected-service cards, core status, quick commands, scanline/corner HUD treatment, and responsive mobile layout.
- Kept the existing canvas energy effect as an overlay rather than using it as the main visual.
- Added one-click Google and Microsoft OAuth connect buttons.
- Added a local testing checklist in `TESTING.md`.
- Added a live command feed and connected-service status chips.
- Removed the wake-word control and wake-word requirement.
- Removed the barge-in/interruption system entirely; JARVIS no longer tries to interrupt its own speech when it detects microphone activity.
- Added voice quiet/resume commands:
  - "stop responding"
  - "stop talking"
  - "be quiet"
  - "resume responding"
  - "start responding"
- Quiet mode keeps the microphone/listening loop active.
- Added PWA support so the command center can be installed on Android.
- Added additional Windows media controls: volume up/down, mute/unmute.
- Kept existing PC controls, confirmations, Gmail, Outlook, Calendar, and official WhatsApp Business Cloud API.
- Switched the default Fish Audio model setting to `s2.1-pro-free`.
- Added a faster Piper default (`PIPER_SPEED=0.75`).
- Removed credential-looking values from `.env.example`.
- Added explicit documentation that Hermes is not required for the integrations already built into JARVIS.
- Added documentation about the limits of browser/PWA Android control: true Android OS control needs a native companion such as Tasker/Termux.

## Important

The `.env` file from the supplied project is deliberately NOT included in this package. It contained credential-looking values. If any of those credentials were real and were ever exposed outside your private machine, rotate them.

The local `.piper-env`, `voices`, `node_modules`, and `.runtime` directories are also excluded to keep the upgrade package small. Run the existing setup steps after extracting the project.

## Android reality

The PWA lets the Android phone become a JARVIS remote interface. It does not magically gain permission to control Android system functions. For calls, SMS, app launching, device settings, etc., a native Android/Tasker/Termux bridge is needed.

## Hermes

Hermes is optional. JARVIS already has direct OAuth/API adapters for Gmail, Microsoft/Outlook, Google Calendar, Microsoft Calendar, and official WhatsApp Business Cloud API. Hermes only becomes useful if you want its separate agent/automation capabilities.
