# JARVIS — Local Testing Checklist

## 1. Install

Open PowerShell in this folder:

```powershell
npm install
```

## 2. Configure

```powershell
Copy-Item .env.example .env
```

Open `.env` and add your own Groq key. Do not paste keys into chat.

For local free voice:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\setup-piper.ps1
```

## 3. Start

```powershell
npm start
```

Open:

```text
http://localhost:8787
```

Allow microphone access in Chrome/Edge.

## 4. Test in this order

1. **HUD** — confirm the JARVIS artwork loads and the new command-center layout appears.
2. **Microphone** — confirm `ALWAYS LISTENING` and that the mic meter moves while speaking.
3. **STT** — say: `What time is it?`
4. **LLM** — ask a normal question.
5. **TTS** — confirm JARVIS speaks the answer.
6. **Quiet mode** — say `stop responding`, then say something else. JARVIS should listen but not answer.
7. **Resume** — say `resume responding`.
8. **PC tool** — try `open calculator`. Confirmations should appear for protected actions where required.
9. **Replay** — press `REPLAY` after a response.
10. **Stop** — press `STOP` and verify audio/mic capture stops.
11. **Settings** — verify silence and sensitivity sliders work.
12. **Google/Microsoft** — use the CONNECT buttons after OAuth credentials and redirect URIs are configured.
13. **WhatsApp** — test only after the Meta webhook is publicly reachable over HTTPS.

## 5. Important behavior change

There is **no wake word** and **no barge-in/interruption system**. JARVIS continuously listens for the next command when the voice loop is running. While JARVIS is speaking, it will finish speaking instead of treating your voice as an interruption.

`stop responding` mutes replies while the listener remains active; `resume responding` restores replies.

## 6. If the mic does not start

- Use Chrome or Edge.
- Allow microphone permission for `localhost`.
- Make sure another application is not exclusively using the microphone.
- Reload the page after granting permission.

## 7. Kokoro voice setup

After extracting the project, run `npm install`, then start JARVIS normally. Leave the first voice test running for a little longer because Kokoro may need to download its model files the first time. The default is Kokoro q8 on Node CPU with the `af_heart` voice.

If Kokoro fails to load, temporarily set `TTS_PROVIDER=piper` in `.env` and restart JARVIS; Piper remains available as a fallback.

## 8. If Piper fails

Check the terminal output. The setup script should create `.piper-env` and make a Piper executable available. The browser voice fallback can still speak if Piper fails.
