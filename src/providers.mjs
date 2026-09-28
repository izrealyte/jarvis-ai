import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, statSync, createReadStream } from "node:fs";
import { mkdir, readFile, readdir, stat, unlink } from "node:fs/promises";
import { join } from "node:path";
import { KokoroTTS } from "kokoro-js";
import { config, configured, RUNTIME_DIR } from "./config.mjs";

const TTS_CACHE_DIR = join(RUNTIME_DIR, "tts-cache");
let kokoroPromise = null;

async function getKokoro() {
  if (!kokoroPromise) {
    console.log(`[kokoro] loading ${config.kokoroModel} (${config.kokoroDtype}/${config.kokoroDevice})...`);
    kokoroPromise = KokoroTTS.from_pretrained(config.kokoroModel, {
      dtype: config.kokoroDtype,
      device: config.kokoroDevice
    });
  }
  try {
    return await kokoroPromise;
  } catch (error) {
    kokoroPromise = null;
    throw error;
  }
}

export async function complete(messages, tools = []) {
  if (!configured(config.llmApiKey)) {
    throw new Error(`${config.llmProvider.toUpperCase()} API key is not configured.`);
  }
  const body = { model: config.llmModel, messages, temperature: 0.65 };
  if (tools.length) { body.tools = tools; body.tool_choice = "auto"; }
  if (config.llmProvider === "groq") body.max_completion_tokens = 900;
  else body.max_tokens = 900;
  const maxAttempts = 3;
  let lastError = null;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const response = await fetch(config.llmEndpoint, {
        method: "POST",
        headers: { "Authorization": `Bearer ${config.llmApiKey}`, "Content-Type": "application/json", "Connection": "close" },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(60_000)
      });
      const raw = await response.text();
      let data = {};
      try { data = raw ? JSON.parse(raw) : {}; } catch {}
      if (!response.ok) {
        const detail = data?.error?.message || data?.message || raw.slice(0, 300) || `HTTP ${response.status}`;
        throw new Error(`${config.llmProvider} returned HTTP ${response.status}: ${detail}`);
      }
      return data?.choices?.[0]?.message || { role: "assistant", content: "" };
    } catch (error) {
      lastError = error;
      const message = error?.cause?.message || error?.message || String(error);
      const retryable = error?.cause?.code === "UND_ERR_CONNECT_TIMEOUT" || error?.cause?.code === "UND_ERR_SOCKET" || error?.name === "TimeoutError" || /fetch failed|socket|timeout/i.test(message);
      if (!retryable || attempt === maxAttempts) throw error;
      const delay = 800 * attempt;
      console.warn(`[llm] attempt ${attempt}/${maxAttempts} failed (${message}); retrying in ${delay}ms`);
      await new Promise((resolve) => setTimeout(resolve, delay));
    }
  }
  throw lastError || new Error("LLM request failed.");
}

export async function transcribeAudio(audioBuffer, mimeType = "audio/wav") {
  if (!configured(config.sttApiKey)) throw new Error("Groq API key required for STT.");
  if (!audioBuffer || audioBuffer.length < 500) throw new Error("Audio recording was too short or empty.");

  const head = audioBuffer.subarray(0, 12);
  const isWav  = head[0] === 0x52 && head[1] === 0x49 && head[2] === 0x46 && head[3] === 0x46;
  const isWebm = head[0] === 0x1a && head[1] === 0x45 && head[2] === 0xdf && head[3] === 0xa3;

  let ext = "wav";
  let type = "audio/wav";
  if (isWav) { ext = "wav"; type = "audio/wav"; }
  else if (isWebm) { ext = "webm"; type = "audio/webm"; }

  console.log(`[stt] received ${audioBuffer.length} bytes, detected .${ext}`);

  const form = new FormData();
  const blob = new Blob([audioBuffer], { type });
  form.append("file", blob, `speech.${ext}`);
  form.append("model", config.groqSttModel);
  form.append("response_format", "json");
  form.append("language", "en");

  const response = await fetch("https://api.groq.com/openai/v1/audio/transcriptions", {
    method: "POST",
    headers: { "Authorization": `Bearer ${config.sttApiKey}` },
    body: form,
    signal: AbortSignal.timeout(45_000)
  });

  const raw = await response.text();
  let data = {};
  try { data = raw ? JSON.parse(raw) : {}; } catch {}
  if (!response.ok) {
    const detail = data?.error?.message || raw.slice(0, 300) || `HTTP ${response.status}`;
    throw new Error(`Groq Whisper returned HTTP ${response.status}: ${detail}`);
  }
  return { text: (data.text || "").trim() };
}

export function piperHealth() {
  const reasons = [];
  if (!existsSync(config.piperExe)) reasons.push(`piper.exe not found at ${config.piperExe}`);
  const modelPath = join(config.piperDataDir, `${config.piperModel}.onnx`);
  const configPath = join(config.piperDataDir, `${config.piperModel}.onnx.json`);
  if (!existsSync(modelPath)) reasons.push(`model not found at ${modelPath}`);
  else { try { const stats = statSync(modelPath); if (stats.size < 10 * 1024 * 1024) reasons.push(`model is only ${stats.size} bytes`); } catch (error) { reasons.push(`stat error: ${error.message}`); } }
  if (!existsSync(configPath)) reasons.push(`model config not found at ${configPath}`);
  return { ok: reasons.length === 0, reasons, exe: config.piperExe, model: config.piperModel, dataDir: config.piperDataDir, speed: config.piperSpeed };
}

function cacheKey(text) {
  return createHash("sha256").update(`${config.ttsProvider}|${config.kokoroModel}|${config.kokoroVoice}|${config.kokoroDtype}|${config.piperModel}|${config.piperSpeed}|${text}`).digest("hex").slice(0, 32);
}

async function evictCache() {
  try {
    const entries = await readdir(TTS_CACHE_DIR);
    if (entries.length <= 500) return;
    const stats = await Promise.all(entries.map(async (name) => { try { const st = await stat(join(TTS_CACHE_DIR, name)); return { name, mtime: st.mtimeMs }; } catch { return { name, mtime: 0 }; } }));
    stats.sort((a, b) => a.mtime - b.mtime);
    for (const entry of stats.slice(0, entries.length - 400)) await unlink(join(TTS_CACHE_DIR, entry.name)).catch(() => {});
  } catch {}
}

function piperArgs(outputPath, text) {
  return ["--model", config.piperModel, "--data-dir", config.piperDataDir, "--output_file", outputPath, "--length_scale", String(config.piperSpeed), "--", text];
}

async function runPiperFull(text) {
  const clean = String(text || "").slice(0, 5000).trim();
  if (!clean) throw new Error("Nothing to synthesize.");
  await mkdir(TTS_CACHE_DIR, { recursive: true });
  const key = cacheKey(clean);
  const cachePath = join(TTS_CACHE_DIR, `${key}.wav`);
  if (existsSync(cachePath)) return { bytes: await readFile(cachePath), contentType: "audio/wav", cached: true };
  if (!existsSync(config.piperExe)) throw new Error(`Piper not installed at ${config.piperExe}.`);
  await new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(config.piperExe, piperArgs(cachePath, clean), { windowsHide: true });
    let stderr = "", settled = false;
    child.stderr?.on("data", (chunk) => { stderr += chunk.toString(); });
    const timeout = setTimeout(() => { if (settled) return; settled = true; try { child.kill(); } catch {} rejectPromise(new Error("Piper timeout.")); }, 20_000);
    child.on("error", (error) => { if (settled) return; settled = true; clearTimeout(timeout); rejectPromise(new Error(`Piper error: ${error.message}`)); });
    child.on("close", (code) => { if (settled) return; settled = true; clearTimeout(timeout); if (code !== 0) { const tail = stderr.trim().split("\n").slice(-3).join(" | ").slice(0, 400); rejectPromise(new Error(`Piper exit ${code}. ${tail}`)); return; } resolvePromise(); });
  });
  const bytes = await readFile(cachePath);
  if (bytes.length < 100) throw new Error("Piper produced empty WAV.");
  evictCache().catch(() => {});
  return { bytes, contentType: "audio/wav", cached: false };
}

async function runPiperStream(text) {
  const clean = String(text || "").slice(0, 5000).trim();
  if (!clean) throw new Error("Nothing to synthesize.");
  await mkdir(TTS_CACHE_DIR, { recursive: true });
  const key = cacheKey(clean);
  const cachePath = join(TTS_CACHE_DIR, `${key}.wav`);
  if (existsSync(cachePath)) return { stream: createReadStream(cachePath), cached: true };
  if (!existsSync(config.piperExe)) throw new Error(`Piper not installed at ${config.piperExe}.`);
  await new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(config.piperExe, piperArgs(cachePath, clean), { windowsHide: true });
    let stderr = "", settled = false;
    child.stderr?.on("data", (chunk) => { const t = chunk.toString(); stderr += t; process.stderr.write(`[piper] ${t}`); });
    const timeout = setTimeout(() => { if (settled) return; settled = true; try { child.kill(); } catch {} rejectPromise(new Error("Piper timeout.")); }, 20_000);
    child.on("error", (error) => { if (settled) return; settled = true; clearTimeout(timeout); rejectPromise(new Error(`Piper error: ${error.message}`)); });
    child.on("close", (code) => { if (settled) return; settled = true; clearTimeout(timeout); if (code !== 0) { const tail = stderr.trim().split("\n").slice(-3).join(" | ").slice(0, 400); rejectPromise(new Error(`Piper exit ${code}. ${tail}`)); return; } resolvePromise(); });
  });
  evictCache().catch(() => {});
  return { stream: createReadStream(cachePath), cached: false };
}

async function runKokoroFull(text) {
  const clean = String(text || "").slice(0, 5000).trim();
  if (!clean) throw new Error("Nothing to synthesize.");
  await mkdir(TTS_CACHE_DIR, { recursive: true });
  const key = cacheKey(clean);
  const cachePath = join(TTS_CACHE_DIR, `${key}.wav`);
  if (existsSync(cachePath)) return { bytes: await readFile(cachePath), contentType: "audio/wav", cached: true };

  const tts = await getKokoro();
  const audio = await tts.generate(clean, { voice: config.kokoroVoice });
  const wav = Buffer.from(audio.toWav());
  if (wav.length < 100) throw new Error("Kokoro produced empty WAV.");
  await import("node:fs/promises").then(({ writeFile }) => writeFile(cachePath, wav));
  evictCache().catch(() => {});
  return { bytes: wav, contentType: "audio/wav", cached: false };
}

async function runKokoroStream(text) {
  const clean = String(text || "").slice(0, 5000).trim();
  if (!clean) throw new Error("Nothing to synthesize.");
  await mkdir(TTS_CACHE_DIR, { recursive: true });
  const key = cacheKey(clean);
  const cachePath = join(TTS_CACHE_DIR, `${key}.wav`);
  if (existsSync(cachePath)) return { stream: createReadStream(cachePath), cached: true };

  const audio = await runKokoroFull(clean);
  return { stream: createReadStream(cachePath), cached: audio.cached };
}

async function synthesizeFish(text) {
  if (!configured(config.fishApiKey)) throw new Error("FISH_API_KEY is not configured.");
  const body = { text: String(text).slice(0, 5000), format: "mp3", latency: "balanced", normalize: true, prosody: { speed: 1, volume: 0, normalize_loudness: true } };
  if (config.fishReferenceId) body.reference_id = config.fishReferenceId;
  const response = await fetch("https://api.fish.audio/v1/tts", { method: "POST", headers: { "Authorization": `Bearer ${config.fishApiKey}`, "Content-Type": "application/json", "model": config.fishModel }, body: JSON.stringify(body), signal: AbortSignal.timeout(60_000) });
  const bytes = Buffer.from(await response.arrayBuffer());
  if (!response.ok) { let detail = `Fish Audio HTTP ${response.status}`; try { const data = JSON.parse(bytes.toString("utf8")); detail = data?.message || data?.error || detail; } catch {} throw new Error(detail); }
  return { bytes, contentType: "audio/mpeg", cached: false };
}

export async function synthesizeSpeechStream(text) {
  if (config.ttsProvider === "kokoro") {
    try {
      return await runKokoroStream(text);
    } catch (error) {
      console.error(`[kokoro] failed: ${error.message}; falling back to Piper`);
      return runPiperStream(text);
    }
  }
  return runPiperStream(text);
}

export async function synthesizeSpeech(text) {
  if (config.ttsProvider === "kokoro") return runKokoroFull(text);
  if (config.ttsProvider === "piper") { if (!configured(config.piperModel)) throw new Error("PIPER_MODEL is not configured."); return runPiperFull(text); }
  if (config.ttsProvider === "browser") throw new Error("Browser voice is handled by the client.");
  return synthesizeFish(text);
}