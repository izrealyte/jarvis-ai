import { existsSync, readFileSync } from "node:fs";
import { dirname, isAbsolute, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
export const PUBLIC_DIR = join(ROOT, "public");
export const RUNTIME_DIR = join(ROOT, ".runtime");

function loadDotEnv() {
  const file = join(ROOT, ".env");
  if (!existsSync(file)) return;
  for (const raw of readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) continue;
    const index = line.indexOf("=");
    const key = line.slice(0, index).trim();
    let value = line.slice(index + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (!(key in process.env)) process.env[key] = value;
  }
}

loadDotEnv();

function absolute(value, fallback) {
  const raw = value && String(value).trim() ? String(value).trim() : fallback;
  return isAbsolute(raw) ? raw : resolve(ROOT, raw);
}

const provider = (process.env.LLM_PROVIDER || "groq").toLowerCase();
const isGroq = provider === "groq";

const piperExeDefault =
  process.platform === "win32"
    ? join(".piper-env", "Scripts", "piper.exe")
    : join(".piper-env", "bin", "piper");

const rawSpeed = Number(process.env.PIPER_SPEED || "1.0");
const kokoroDtype = (process.env.KOKORO_DTYPE || "q8").toLowerCase();
const kokoroDevice = (process.env.KOKORO_DEVICE || "cpu").toLowerCase();
const piperSpeed = Number.isFinite(rawSpeed) ? Math.min(2.0, Math.max(0.5, rawSpeed)) : 1.0;

export const config = {
  port: Number(process.env.PORT || 8787),
  assistantName: process.env.ASSISTANT_NAME || "JARVIS",
  ownerName: process.env.OWNER_NAME || "Isreal",
  makerName: process.env.MAKER_NAME || "Isrealyte",
  llmProvider: isGroq ? "groq" : "xai",
  llmModel: isGroq ? (process.env.GROQ_MODEL || "openai/gpt-oss-20b") : (process.env.XAI_MODEL || "grok-4.7"),
  llmEndpoint: isGroq ? "https://api.groq.com/openai/v1/chat/completions" : "https://api.x.ai/v1/chat/completions",
  llmApiKey: isGroq ? (process.env.GROQ_API_KEY || process.env.XAI_API_KEY || "") : (process.env.XAI_API_KEY || ""),
  ttsProvider: (process.env.TTS_PROVIDER || "kokoro").toLowerCase(),
  kokoroModel: process.env.KOKORO_MODEL || "onnx-community/Kokoro-82M-ONNX",
  kokoroVoice: process.env.KOKORO_VOICE || "af_heart",
  kokoroDtype,
  kokoroDevice,
  fishApiKey: process.env.FISH_API_KEY || "",
  fishModel: process.env.FISH_MODEL || "s2.1-pro-free",
  fishReferenceId: process.env.FISH_REFERENCE_ID || "",
  piperExe: absolute(process.env.PIPER_EXE, piperExeDefault),
  piperSpeed,
  piperModel: process.env.PIPER_MODEL || "en_US-lessac-medium",
  piperDataDir: absolute(process.env.PIPER_DATA_DIR, "voices"),

  // STT
  sttProvider: (process.env.STT_PROVIDER || "groq").toLowerCase(),
  sttApiKey: process.env.GROQ_API_KEY || "",
  groqSttModel: process.env.GROQ_STT_MODEL || "whisper-large-v3-turbo",

  // Voice loop tuning
  vadSilenceMs: Number(process.env.VAD_SILENCE_MS || 1400),
  vadThreshold: Number(process.env.VAD_THRESHOLD || 0.015),
  maxListenMs: Number(process.env.MAX_LISTEN_MS || 20000),

  metaGraphVersion: process.env.META_GRAPH_VERSION || "v23.0",
  whatsappPhoneNumberId: process.env.WHATSAPP_PHONE_NUMBER_ID || "",
  metaAccessToken: process.env.META_ACCESS_TOKEN || "",
  whatsappVerifyToken: process.env.WHATSAPP_VERIFY_TOKEN || "",
  googleClientId: process.env.GOOGLE_CLIENT_ID || "",
  googleClientSecret: process.env.GOOGLE_CLIENT_SECRET || "",
  googleRedirectUri: process.env.GOOGLE_REDIRECT_URI || "http://localhost:8787/integrations/google/callback",
  microsoftClientId: process.env.MICROSOFT_CLIENT_ID || "",
  microsoftClientSecret: process.env.MICROSOFT_CLIENT_SECRET || "",
  microsoftTenant: process.env.MICROSOFT_TENANT || "common",
  microsoftRedirectUri: process.env.MICROSOFT_REDIRECT_URI || "http://localhost:8787/integrations/microsoft/callback"
};

export function configured(value) {
  return typeof value === "string" && value.trim().length > 0 && !value.startsWith("replace_with_");
}

export function publicConfig() {
  return {
    assistantName: config.assistantName,
    ownerName: config.ownerName,
    makerName: config.makerName,
    provider: config.llmProvider,
    model: config.llmModel,
    hasLlmKey: configured(config.llmApiKey),
    ttsProvider: config.ttsProvider,
    kokoroModel: config.kokoroModel,
    kokoroVoice: config.kokoroVoice,
    kokoroDtype: config.kokoroDtype,
    kokoroDevice: config.kokoroDevice,
    hasFishKey: configured(config.fishApiKey),
    hasWhatsApp: configured(config.whatsappPhoneNumberId) && configured(config.metaAccessToken),
    hasGoogle: configured(config.googleClientId) && configured(config.googleClientSecret),
    hasMicrosoft: configured(config.microsoftClientId) && configured(config.microsoftClientSecret),
    sttProvider: config.sttProvider,
    vadSilenceMs: config.vadSilenceMs,
    vadThreshold: config.vadThreshold,
    maxListenMs: config.maxListenMs
  };
}