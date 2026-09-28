import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import { RUNTIME_DIR } from "./config.mjs";

async function ensureRuntime() {
  await mkdir(RUNTIME_DIR, { recursive: true });
}

export async function readStore(name, fallback) {
  await ensureRuntime();
  try {
    return JSON.parse(await readFile(join(RUNTIME_DIR, name), "utf8"));
  } catch {
    return fallback;
  }
}

export async function writeStore(name, value) {
  await ensureRuntime();
  await writeFile(join(RUNTIME_DIR, name), JSON.stringify(value, null, 2), "utf8");
}

export async function addPendingAction(action) {
  const pending = await readStore("pending-actions.json", {});
  const id = randomUUID();
  pending[id] = { ...action, id, createdAt: Date.now() };
  await writeStore("pending-actions.json", pending);
  return id;
}

export async function takePendingAction(id) {
  const pending = await readStore("pending-actions.json", {});
  const action = pending[id];
  if (action) {
    delete pending[id];
    await writeStore("pending-actions.json", pending);
  }
  return action || null;
}
