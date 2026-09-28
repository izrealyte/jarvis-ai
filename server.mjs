import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { cpus, totalmem, freemem } from "node:os";
import { URL } from "node:url";
import { config, PUBLIC_DIR, publicConfig } from "./src/config.mjs";
import { runAssistant } from "./src/assistant.mjs";
import { synthesizeSpeech, synthesizeSpeechStream, piperHealth, transcribeAudio } from "./src/providers.mjs";
import { integrationStatus, createOAuthState, consumeOAuthState, googleAuthUrl, exchangeGoogleCode, microsoftAuthUrl, exchangeMicrosoftCode } from "./src/integrations.mjs";
import { readStore, writeStore } from "./src/storage.mjs";
import { mobileStatus, executeTool } from "./src/tools.mjs";

function security(response) {
  response.setHeader("X-Content-Type-Options", "nosniff");
  response.setHeader("Referrer-Policy", "no-referrer");
  response.setHeader("Cache-Control", "no-store");
}

function json(response, status, value) {
  security(response);
  response.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(value));
}

async function body(request, maxBytes = 256 * 1024) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > maxBytes) throw new Error("Request is too large.");
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

async function rawBody(request, maxBytes = 20 * 1024 * 1024) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > maxBytes) throw new Error("Audio is too large.");
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

async function staticFile(pathname, response) {
  if (pathname === "/favicon.ico") {
    response.writeHead(204);
    response.end();
    return;
  }
  const relative = pathname === "/" ? "index.html" : pathname.replace(/^\/+/, "");
  if (relative.includes("..") || relative.includes("\\")) {
    response.writeHead(400);
    response.end("Bad path");
    return;
  }
  const file = join(PUBLIC_DIR, relative);
  const types = { html: "text/html; charset=utf-8", js: "text/javascript; charset=utf-8", css: "text/css; charset=utf-8", jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png", svg: "image/svg+xml", webmanifest: "application/manifest+json", wav: "audio/wav", mp3: "audio/mpeg" };
  try {
    const contents = await readFile(file);
    security(response);
    response.writeHead(200, { "Content-Type": types[file.split(".").pop()] || "application/octet-stream" });
    response.end(contents);
  } catch {
    response.writeHead(404);
    response.end("Not found");
  }
}

let previousCpu = process.cpuUsage();
let previousCpuAt = process.hrtime.bigint();

function realCpuPercent() {
  const now = process.hrtime.bigint();
  const usage = process.cpuUsage(previousCpu);
  const elapsedMicros = Number(now - previousCpuAt) / 1000;
  previousCpu = process.cpuUsage();
  previousCpuAt = now;
  if (!elapsedMicros) return 0;
  return Math.min(100, Math.round(((usage.user + usage.system) / (elapsedMicros * Math.max(1, cpus().length))) * 100));
}

function redirect(response, location) {
  response.writeHead(302, { Location: location });
  response.end();
}

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url || "/", `http://${request.headers.host || "localhost"}`);

    if (request.method === "GET" && url.pathname === "/health") {
      json(response, 200, { ok: true, ...publicConfig() });
      return;
    }

    if (request.method === "GET" && url.pathname === "/health/piper") {
      const health = piperHealth();
      json(response, health.ok ? 200 : 503, health);
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/status") {
      json(response, 200, { ...publicConfig(), integrations: await integrationStatus() });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/system/telemetry") {
      const cores = cpus();
      const used = Math.round((1 - freemem() / totalmem()) * 100);
      const cpu = realCpuPercent();
      const phone = await mobileStatus();
      json(response, 200, { cpu, memory: used, cores: cores.length, nodeMemory: Math.round(process.memoryUsage().rss / 1024 / 1024), online: true, phone });
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/mobile/status") {
      json(response, 200, await mobileStatus());
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/mobile/screenshot") {
      const result = await executeTool("mobile_screenshot", {});
      security(response);
      response.writeHead(200, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
      response.end(JSON.stringify(result));
      return;
    }
    if (request.method === "POST" && url.pathname === "/api/command") {
      const input = await body(request);
      const result = await runAssistant({
        sessionId: typeof input.sessionId === "string" ? input.sessionId : "local",
        message: typeof input.message === "string" ? input.message : "",
        history: input.history,
        confirmationId: input.confirmationId,
        approved: input.approved
      });
      json(response, 200, result);
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/stt") {
  const audio = await rawBody(request);
  if (!audio.length) {
    json(response, 400, { error: "No audio received." });
    return;
  }
  const contentType = String(request.headers["content-type"] || "audio/webm");
  try {
    const result = await transcribeAudio(audio, contentType);
    json(response, 200, result);
  } catch (error) {
    console.error("[stt]", error);
    json(response, 500, { error: error.message });
  }
  return;
}

    /* ----------------------- TTS (streaming) ----------------------- */
    if (request.method === "POST" && url.pathname === "/api/tts") {
      const input = await body(request);
      const text = typeof input.text === "string" ? input.text : "";
      if (!text.trim()) {
        json(response, 400, { error: "No text supplied." });
        return;
      }

      security(response);
      response.setHeader("Content-Type", "audio/wav");
      response.setHeader("Transfer-Encoding", "chunked");
      response.setHeader("Cache-Control", "no-store");

      try {
        const { stream, cached } = await synthesizeSpeechStream(text);
        if (cached) {
          response.setHeader("X-TTS-Cached", "1");
        }
        stream.on("error", (error) => {
          console.error("[tts-stream]", error.message);
          try { response.destroy(); } catch {}
        });
        request.on("close", () => {
          // Client disconnected — kill the Piper child if still running.
          try { stream.destroy(); } catch {}
        });
        stream.pipe(response);
      } catch (error) {
        console.error("[tts]", error);
        if (!response.headersSent) {
          json(response, 500, { error: error.message });
        } else {
          try { response.destroy(); } catch {}
        }
      }
      return;
    }

    /* ----------------------- OAuth ----------------------- */
    if (request.method === "GET" && url.pathname === "/integrations/google/start") {
      const state = await createOAuthState("google");
      redirect(response, googleAuthUrl(state));
      return;
    }
    if (request.method === "GET" && url.pathname === "/integrations/google/callback") {
      const valid = await consumeOAuthState(url.searchParams.get("state"), "google");
      if (!valid) { json(response, 400, { error: "Invalid or expired Google OAuth state." }); return; }
      await exchangeGoogleCode(url.searchParams.get("code"));
      redirect(response, "/?connected=google");
      return;
    }
    if (request.method === "GET" && url.pathname === "/integrations/microsoft/start") {
      const state = await createOAuthState("microsoft");
      redirect(response, microsoftAuthUrl(state));
      return;
    }
    if (request.method === "GET" && url.pathname === "/integrations/microsoft/callback") {
      const valid = await consumeOAuthState(url.searchParams.get("state"), "microsoft");
      if (!valid) { json(response, 400, { error: "Invalid or expired Microsoft OAuth state." }); return; }
      await exchangeMicrosoftCode(url.searchParams.get("code"));
      redirect(response, "/?connected=microsoft");
      return;
    }

    /* ----------------------- WhatsApp webhook ----------------------- */
    if (request.method === "GET" && url.pathname === "/webhooks/whatsapp") {
      const mode = url.searchParams.get("hub.mode");
      const token = url.searchParams.get("hub.verify_token");
      const challenge = url.searchParams.get("hub.challenge");
      if (mode === "subscribe" && token === config.whatsappVerifyToken && challenge) {
        response.writeHead(200, { "Content-Type": "text/plain" });
        response.end(challenge);
      } else {
        response.writeHead(403);
        response.end("Forbidden");
      }
      return;
    }

    if (request.method === "POST" && url.pathname === "/webhooks/whatsapp") {
      const input = await body(request);
      const events = await readStore("whatsapp-events.json", []);
      const history = Array.isArray(events) ? events : [events];
      history.push({ receivedAt: new Date().toISOString(), payload: input });
      await writeStore("whatsapp-events.json", history.slice(-100));
      json(response, 200, { received: true });
      return;
    }

    if (request.method === "GET") {
      await staticFile(url.pathname, response);
      return;
    }

    json(response, 405, { error: "Method not allowed" });
  } catch (error) {
    console.error(error);
    if (!response.headersSent) {
      json(response, 500, { error: error instanceof Error ? error.message : "Unexpected server error" });
    } else {
      try { response.destroy(); } catch {}
    }
  }
});

server.listen(config.port, "0.0.0.0", () => {
  console.log(`${config.assistantName} command center running at http://0.0.0.0:${config.port}`);
  console.log(`LLM: ${config.llmProvider}/${config.llmModel} | TTS: ${config.ttsProvider} | STT: ${config.sttProvider}`);

  if (config.ttsProvider === "piper") {
    const health = piperHealth();
    if (health.ok) {
      console.log(`Piper OK: ${health.exe}`);
      console.log(`       → model: ${health.model} (speed ${health.speed})`);
    } else {
      console.error(`Piper is NOT ready:`);
      for (const reason of health.reasons) console.error(`   - ${reason}`);
    }
  }

  if (config.sttProvider === "groq" && !config.sttApiKey) {
    console.error(`STT: Groq Whisper selected but GROQ_API_KEY is missing.`);
  } else if (config.sttProvider === "groq") {
    console.log(`STT OK: Groq Whisper (${config.groqSttModel})`);
  }
});