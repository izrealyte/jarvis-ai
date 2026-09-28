# JARVIS Mobile Companion v2 — Server Contract

The Android app is the perception/control agent. The PC JARVIS server is the reasoning, memory, OCR/vision and planning layer.

## Events → JARVIS
`POST /api/mobile/event`
```json
{"type":"notification|call_state|ui_event|screen_snapshot|agent_status|memory|screen_stream|camera_stream","data":{}}
```

Important event types:
- `notification`: package/title/text/time/pattern
- `call_state`: RINGING/OFFHOOK/IDLE and any number Android exposes
- `screen_snapshot`: accessibility-visible text from the active window
- `ui_event`: foreground UI changes
- `memory`: local companion memory that should be considered for long-term storage
- `screen_stream` / `camera_stream`: stream status

## Vision frames → JARVIS
`POST /api/mobile/frame`
- Content-Type: `image/jpeg`
- `X-JARVIS-Device: <android_id>`
- Optional Bearer token

The PC server can run OCR/vision over frames and return a structured observation such as:
```json
{"screen":"Chrome","ocr":["..."],"elements":[{"text":"Deploy","x":123,"y":456}],"issues":[]}
```

## Commands ← JARVIS
`GET /api/mobile/commands?device_id=<id>`
```json
{"commands":[
  {"id":"1","action":"READ_SCREEN"},
  {"id":"2","action":"TYPE","text":"hello"},
  {"id":"3","action":"TAP","x":500,"y":800},
  {"id":"4","action":"SWIPE","x1":500,"y1":1200,"x2":500,"y2":300,"duration":600},
  {"id":"5","action":"LAUNCH_PACKAGE","package":"com.android.chrome"},
  {"id":"6","action":"OPEN_URI","uri":"https://example.com"},
  {"id":"7","action":"HOME"},
  {"id":"8","action":"BACK"},
  {"id":"9","action":"RECENTS"},
  {"id":"10","action":"NOTIFICATIONS"},
  {"id":"11","action":"QUICK_SETTINGS"},
  {"id":"12","action":"VOLUME_UP"}
]}
```

## Command acknowledgement
`POST /api/mobile/command-result`
```json
{"id":"1","ok":true}
```

## Developer-agent workflow
The PC JARVIS layer should treat the mobile feed as a remote development sensor:
1. Ask the phone for `READ_SCREEN`.
2. Use the accessibility snapshot for structured text/UI.
3. Use the latest JPEG frame for OCR/visual layout analysis.
4. Detect errors, broken layouts, console/error text or unexpected navigation.
5. Explain the issue in plain language and propose a concrete fix.
6. When the user authorizes an action, issue a narrow command and verify with another snapshot/frame.
7. Keep a task/memory record so repeated problems and project conventions are remembered.

## Independence model
JARVIS should not behave as a blind command router. The PC layer should maintain:
- user profile + preferences
- active projects and their conventions
- short-term task state
- long-term memory with user-controlled deletion/editing
- scheduled tasks/reminders
- event history and daily summaries
- confidence/uncertainty for observations
- an approval policy for risky actions

Autonomy means planning and proposing the next useful step; it does **not** mean silently sending messages, making purchases, deleting data, changing security settings, or taking other high-impact actions without authorization.

## Conversational brain
`POST /api/mobile/chat`
```json
{"text":"What do you think about studying Linguistics?","device_id":"<android_id>"}
```
Expected response:
```json
{"reply":"..."}
```
The phone speaks `reply` when returned. If the endpoint is unavailable, the phone reports that the conversational brain cannot be reached; it does not open Google automatically.
