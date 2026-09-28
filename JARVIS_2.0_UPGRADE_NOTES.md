# JARVIS 2.0 upgrade

This package keeps the existing JARVIS command center and adds a controlled Android ADB tool layer, real Android status/screenshot endpoints, reactive phone telemetry in the HUD, and non-random system telemetry.

## Android tools
- mobile_status
- mobile_screenshot
- mobile_open_app
- mobile_key
- mobile_tap
- mobile_swipe
- mobile_type

Coordinate taps, swipes, text entry and key actions require confirmation. Screenshot/status are read-only.

## Setup
1. Make sure Android Platform-Tools (`adb`) is installed and available in PATH, or set `ADB_PATH` in `.env`.
2. Keep Wireless Debugging paired/connected.
3. Run `adb devices` and confirm the phone says `device`.
4. Run `npm install` (or `setup-kokoro.ps1`).
5. Run `npm start`.

The first Kokoro model load/download may take longer; subsequent speech uses the local cache.
