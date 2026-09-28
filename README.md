# JARVIS Mobile Companion v2.0

A native Android companion for the PC JARVIS agent. v2 changes the phone from a passive remote into a **two-way perception + action agent**.

## What v2 adds

### 👁 Perception
- NotificationListenerService for background notification events.
- Accessibility screen reader for visible UI text/content descriptions.
- Live MediaProjection screen stream to the PC for OCR/vision analysis.
- User-started camera stream for visual analysis.
- Active-window/UI event reporting.
- Call-state monitoring for incoming/active/ended calls.
- Device identity for authenticated event routing.

### 🖐 Action
After the user enables Android Accessibility, JARVIS can request:
- type into the focused field
- tap coordinates
- swipe gestures
- Home / Back / Recents
- open notifications / quick settings
- launch an installed package
- open a URL
- volume up/down
- request a screen read

The PC server must expose the command queue described in `MOBILE-V2-SERVER-CONTRACT.md`.

### 🧠 Memory / companion behavior
The mobile side has a small persistent local memory cache and emits memory events to the PC server. The PC server should be the canonical long-term memory store so the same user profile is available across desktop and phone.

Recommended memory categories:
- user identity/preferences supplied by the user
- projects and tech stacks
- coding conventions
- active goals
- recurring tasks
- important contacts/services the user explicitly chooses to remember
- previous solutions and known errors

### 💻 Developer mode
The phone becomes a remote sensor for web development. JARVIS can combine:
- screen screenshot
- OCR
- accessibility/UI tree
- browser/app state
- PC filesystem/project context
- terminal/build output

That enables workflows such as:
- “Read what is on my screen and tell me what is wrong.”
- “Find the error in this page.”
- “Inspect this layout and suggest a fix.”
- “Type this into the current field.”
- “Open the project URL and verify the page.”
- “Check the UI after the change and tell me whether the problem is fixed.”

## Android restrictions that are intentional
- Screen capture requires the Android MediaProjection consent flow.
- Camera use is explicit and user-started.
- Accessibility must be enabled by the user in Android Settings.
- Overlay requires the user to grant the overlay permission.
- Notification access requires explicit Android Settings approval.
- Android may restrict access to phone numbers/call details. v2 reports call state; it does **not** secretly record call audio. Robust call-history access requires the appropriate Android permission/role and must follow platform policy.
- “Unusual activity” is a heuristic signal, not proof that an app/person is malicious. JARVIS should combine multiple signals before raising an alert.

## Build
Open the project in Android Studio and let Gradle sync. Then build the APK from Android Studio.

This source intentionally avoids third-party Android libraries so the companion remains small and inspectable.

## Server contract
See `MOBILE-V2-SERVER-CONTRACT.md` for the exact endpoints and payloads the PC JARVIS server should implement.
