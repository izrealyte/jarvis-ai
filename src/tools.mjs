import { spawn, execFile } from "node:child_process";
import { randomUUID } from "node:crypto";
import { readdir } from "node:fs/promises";
import { join, resolve } from "node:path";
import { config, configured, publicConfig, ROOT } from "./config.mjs";
import { readStore, writeStore } from "./storage.mjs";
import { gmailBriefing, googleCalendarBriefing, outlookBriefing, outlookCalendarBriefing } from "./integrations.mjs";



function adbBinary() { return process.env.ADB_PATH || "adb"; }
function adbRun(args, {input} = {}) {
  return new Promise((resolvePromise, reject) => {
    execFile(adbBinary(), args, { windowsHide: true, maxBuffer: 8 * 1024 * 1024, input }, (error, stdout, stderr) => {
      if (error) reject(new Error(String(stderr || error.message).trim()));
      else resolvePromise(String(stdout || "").trim());
    });
  });
}
async function adbDevice() {
  const raw = await adbRun(["devices", "-l"]);
  const line = raw.split(/\r?\n/).find((x) => /\bdevice\b/.test(x) && !x.startsWith("List of devices"));
  if (!line) return null;
  const serial = line.trim().split(/\s+/)[0];
  return { serial, raw: line.trim() };
}
function adbArgs(device, args) { return ["-s", device.serial, ...args]; }

export async function mobileStatus() {
  try {
    const device = await adbDevice();
    if (!device) return { connected: false, message: "No Android device connected." };
    const [model, android, battery, wifi, foreground] = await Promise.all([
      adbRun(adbArgs(device, ["shell", "getprop", "ro.product.model"])),
      adbRun(adbArgs(device, ["shell", "getprop", "ro.build.version.release"])),
      adbRun(adbArgs(device, ["shell", "dumpsys", "battery"])),
      adbRun(adbArgs(device, ["shell", "cmd", "wifi", "status"])).catch(() => ""),
      adbRun(adbArgs(device, ["shell", "dumpsys", "activity", "activities"])).catch(() => "")
    ]);
    const level = Number(battery.match(/level: (\d+)/)?.[1] || 0);
    const pkg = foreground.match(/mResumedActivity:.*? ([A-Za-z0-9._]+)\//)?.[1] || foreground.match(/mCurrentFocus.*? ([A-Za-z0-9._]+)\//)?.[1] || "unknown";
    return { connected: true, serial: device.serial, model, android, battery: level, foregroundPackage: pkg, wifi: /Wi-Fi is enabled|Wi-Fi enabled|mWifiInfo/i.test(wifi) ? "connected" : "unknown" };
  } catch (error) { return { connected: false, message: error.message }; }
}

async function mobileDeviceOrThrow() {
  const device = await adbDevice();
  if (!device) throw new Error("No Android phone is connected through ADB. Check Wireless Debugging and run adb devices.");
  return device;
}
async function mobileScreenshot() {
  const device = await mobileDeviceOrThrow();
  const base64 = await new Promise((resolvePromise, reject) => {
    execFile(adbBinary(), adbArgs(device, ["exec-out", "screencap", "-p"]), { encoding: "buffer", maxBuffer: 20 * 1024 * 1024, windowsHide: true }, (error, stdout, stderr) => {
      if (error) reject(new Error(String(stderr || error.message).trim())); else resolvePromise(Buffer.from(stdout).toString("base64"));
    });
  });
  return { connected: true, serial: device.serial, mime: "image/png", dataUrl: `data:image/png;base64,${base64}` };
}
async function mobileOpenApp(app) {
  const packages = { whatsapp:"com.whatsapp", instagram:"com.instagram.android", tiktok:"com.zhiliaoapp.musically", chrome:"com.android.chrome", youtube:"com.google.android.youtube", gmail:"com.google.android.gm", camera:"com.android.camera", settings:"com.android.settings", play_store:"com.android.vending" };
  const pkg = packages[app]; if (!pkg) throw new Error("That Android app is not allowlisted.");
  const device = await mobileDeviceOrThrow();
  await adbRun(adbArgs(device, ["shell", "monkey", "-p", pkg, "1"]));
  return { opened: true, app, package: pkg };
}
async function mobileKey(key) {
  const codes = { back:"4", home:"3", recents:"187", volume_up:"24", volume_down:"25", power:"26", enter:"66", tab:"61", delete:"67" };
  const code=codes[key]; if(!code) throw new Error("That Android key is not allowlisted.");
  const device=await mobileDeviceOrThrow(); await adbRun(adbArgs(device,["shell","input","keyevent",code])); return {pressed:key};
}
async function mobileTap(x,y) { const device=await mobileDeviceOrThrow(); await adbRun(adbArgs(device,["shell","input","tap",String(Math.round(x)),String(Math.round(y))])); return {tapped:{x:Number(x),y:Number(y)}}; }
async function mobileSwipe(x1,y1,x2,y2,duration=400) { const device=await mobileDeviceOrThrow(); await adbRun(adbArgs(device,["shell","input","swipe",String(Math.round(x1)),String(Math.round(y1)),String(Math.round(x2)),String(Math.round(y2)),String(Math.max(100,Math.min(2000,duration)))])); return {swiped:{x1:Number(x1),y1:Number(y1),x2:Number(x2),y2:Number(y2),duration:Number(duration)}}; }
async function mobileType(text) {
  const device=await mobileDeviceOrThrow();
  const safe=String(text).replace(/([\\"'&<>|;()$`])/g,"\\$1").replace(/ /g,"%s");
  await adbRun(adbArgs(device,["shell","input","text",safe]));
  return {typed:String(text)};
}

export const toolDefinitions = [
  { type: "function", function: { name: "get_time", description: "Get the current local date and time.", parameters: { type: "object", properties: {}, additionalProperties: false } } },
  { type: "function", function: { name: "get_email_briefing", description: "Read and summarize unread Gmail messages. Never send or delete email. Prefer Gmail because this user selected Gmail.", parameters: { type: "object", properties: { provider: { type: "string", enum: ["gmail", "outlook", "auto"] } }, additionalProperties: false } } },
  { type: "function", function: { name: "get_calendar_briefing", description: "Read upcoming calendar events for the next seven days.", parameters: { type: "object", properties: { provider: { type: "string", enum: ["google", "microsoft", "auto"] } }, additionalProperties: false } } },
  { type: "function", function: { name: "get_whatsapp_inbox", description: "Read recent incoming messages received by the connected official WhatsApp Business webhook. This cannot read a personal WhatsApp account.", parameters: { type: "object", properties: { limit: { type: "integer", minimum: 1, maximum: 20 } }, additionalProperties: false } } },
  { type: "function", function: { name: "send_whatsapp", description: "Send a WhatsApp text through an official WhatsApp Business Cloud API number. Always request confirmation first.", parameters: { type: "object", properties: { to: { type: "string", description: "Recipient phone number in international format" }, message: { type: "string", description: "Message to send" } }, required: ["to", "message"], additionalProperties: false } } },
  { type: "function", function: { name: "open_pc_app", description: "Open one approved application on the user's Windows PC. Always request confirmation first.", parameters: { type: "object", properties: { app: { type: "string", enum: ["calculator", "notepad", "explorer", "browser", "vscode", "terminal", "settings"] } }, required: ["app"], additionalProperties: false } } },
  { type: "function", function: { name: "open_pc_folder", description: "Open one approved local folder. Always request confirmation first.", parameters: { type: "object", properties: { folder: { type: "string", enum: ["desktop", "downloads", "documents", "pictures", "jarvis"] } }, required: ["folder"], additionalProperties: false } } },
  { type: "function", function: { name: "open_website", description: "Open a valid http or https website in the default browser. Always request confirmation first.", parameters: { type: "object", properties: { url: { type: "string" } }, required: ["url"], additionalProperties: false } } },
  { type: "function", function: { name: "control_pc", description: "Perform a power or session action on the PC. Always request confirmation first. Never use this for arbitrary shell commands.", parameters: { type: "object", properties: { action: { type: "string", enum: ["lock", "sleep", "restart", "shutdown"] } }, required: ["action"], additionalProperties: false } } },
  { type: "function", function: { name: "control_media", description: "Control Windows media volume and playback. Always request confirmation first for volume changes. Never execute arbitrary commands.", parameters: { type: "object", properties: { action: { type: "string", enum: ["volume_up", "volume_down", "mute", "unmute"] } }, required: ["action"], additionalProperties: false } } },
  { type: "function", function: { name: "search_pc_files", description: "Search filenames in one approved user folder. Do not read file contents or search secrets.", parameters: { type: "object", properties: { root: { type: "string", enum: ["desktop", "downloads", "documents", "pictures", "jarvis"] }, query: { type: "string", minLength: 1 }, maxResults: { type: "integer", minimum: 1, maximum: 30 } }, required: ["root", "query"], additionalProperties: false } } },
  { type: "function", function: { name: "create_reminder", description: "Create a local reminder in the assistant's reminder store.", parameters: { type: "object", properties: { title: { type: "string" }, when: { type: "string", description: "Natural language or ISO date/time" } }, required: ["title", "when"], additionalProperties: false } } },
  { type: "function", function: { name: "list_reminders", description: "List saved local reminders.", parameters: { type: "object", properties: {}, additionalProperties: false } } },
  { type: "function", function: { name: "get_system_status", description: "Show which JARVIS modules are configured.", parameters: { type: "object", properties: {}, additionalProperties: false } } },
  { type: "function", function: { name: "mobile_status", description: "Check the connected Android phone, including model, Android version, battery, and foreground app.", parameters: { type: "object", properties: {}, additionalProperties: false } } },
  { type: "function", function: { name: "mobile_screenshot", description: "Capture the current Android screen. Use this before coordinate-based interaction when visual context is needed.", parameters: { type: "object", properties: {}, additionalProperties: false } } },
  { type: "function", function: { name: "mobile_open_app", description: "Open an approved Android app.", parameters: { type: "object", properties: { app: { type: "string", enum: ["whatsapp","instagram","tiktok","chrome","youtube","gmail","camera","settings","play_store"] } }, required: ["app"], additionalProperties: false } } },
  { type: "function", function: { name: "mobile_key", description: "Press a safe Android navigation/media key.", parameters: { type: "object", properties: { key: { type: "string", enum: ["back","home","recents","volume_up","volume_down","power","enter","tab","delete"] } }, required: ["key"], additionalProperties: false } } },
  { type: "function", function: { name: "mobile_tap", description: "Tap an Android screen coordinate. Prefer a recent screenshot before using coordinates.", parameters: { type: "object", properties: { x: { type: "number" }, y: { type: "number" } }, required: ["x","y"], additionalProperties: false } } },
  { type: "function", function: { name: "mobile_swipe", description: "Swipe on the Android screen.", parameters: { type: "object", properties: { x1:{type:"number"}, y1:{type:"number"}, x2:{type:"number"}, y2:{type:"number"}, duration:{type:"number"} }, required:["x1","y1","x2","y2"], additionalProperties:false } } },
  { type: "function", function: { name: "mobile_type", description: "Type text into the currently focused Android field.", parameters: { type: "object", properties: { text:{type:"string"} }, required:["text"], additionalProperties:false } } }
];

export function requiresConfirmation(name) {
  return ["send_whatsapp", "open_pc_app", "open_pc_folder", "open_website", "control_pc", "control_media", "mobile_tap", "mobile_swipe", "mobile_type", "mobile_key"].includes(name);
}

export function actionDescription(name, args) {
  if (name === "send_whatsapp") return `Send this WhatsApp message to ${args.to}: “${args.message}”`;
  if (name === "open_pc_app") return `Open ${args.app} on this PC`;
  if (name === "open_pc_folder") return `Open the ${args.folder} folder on this PC`;
  if (name === "open_website") return `Open ${args.url} in the browser`;
  if (name === "control_pc") return `${args.action} this PC`;
  if (name === "control_media") return `${args.action.replaceAll("_", " ")} the PC audio`;
  if (name === "mobile_tap") return `Tap the Android screen at ${args.x}, ${args.y}`;
  if (name === "mobile_swipe") return `Swipe the Android screen from ${args.x1}, ${args.y1} to ${args.x2}, ${args.y2}`;
  if (name === "mobile_type") return `Type “${args.text}” into the Android phone`;
  if (name === "mobile_key") return `Press ${args.key} on the Android phone`;
  return `Run ${name}`;
}

async function sendWhatsApp({ to, message }) {
  if (!configured(config.whatsappPhoneNumberId) || !configured(config.metaAccessToken)) {
    throw new Error("WhatsApp is not configured. Only the official Business Cloud API is supported.");
  }
  const response = await fetch(`https://graph.facebook.com/${config.metaGraphVersion}/${config.whatsappPhoneNumberId}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${config.metaAccessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ messaging_product: "whatsapp", recipient_type: "individual", to, type: "text", text: { preview_url: false, body: message } })
  });
  const raw = await response.text();
  let data = {};
  try { data = raw ? JSON.parse(raw) : {}; } catch {}
  if (!response.ok) throw new Error(data?.error?.message || `WhatsApp returned HTTP ${response.status}`);
  return { sent: true, id: data?.messages?.[0]?.id || null, to };
}

function detached(command, args) {
  const child = spawn(command, args, { detached: true, stdio: "ignore", windowsHide: true });
  child.unref();
  return { started: true };
}

function openPcApp(app) {
  const commands = {
    calculator: process.platform === "win32" ? ["calc.exe", []] : process.platform === "darwin" ? ["open", ["-a", "Calculator"]] : ["gnome-calculator", []],
    notepad: process.platform === "win32" ? ["notepad.exe", []] : process.platform === "darwin" ? ["open", ["-a", "TextEdit"]] : ["gedit", []],
    explorer: process.platform === "win32" ? ["explorer.exe", ["."]] : process.platform === "darwin" ? ["open", ["."]] : ["xdg-open", ["."]],
    browser: process.platform === "win32" ? ["cmd.exe", ["/c", "start", "", "https://www.google.com"]] : process.platform === "darwin" ? ["open", ["https://www.google.com"]] : ["xdg-open", ["https://www.google.com"]],
    vscode: process.platform === "win32" ? ["cmd.exe", ["/c", "start", "", "code"]] : ["code", []],
    terminal: process.platform === "win32" ? ["wt.exe", []] : process.platform === "darwin" ? ["open", ["-a", "Terminal"]] : ["x-terminal-emulator", []],
    settings: process.platform === "win32" ? ["cmd.exe", ["/c", "start", "", "ms-settings:"]] : process.platform === "darwin" ? ["open", ["x-apple.systempreferences:"]] : ["gnome-control-center", []]
  };
  const selected = commands[app];
  if (!selected) throw new Error("That PC application is not allowlisted.");
  return { ...detached(selected[0], selected[1]), app };
}

function userFolders() {
  const home = process.env.USERPROFILE || process.env.HOME || ROOT;
  return {
    desktop: join(home, "Desktop"),
    downloads: join(home, "Downloads"),
    documents: join(home, "Documents"),
    pictures: join(home, "Pictures"),
    jarvis: ROOT
  };
}

function openPcFolder(folder) {
  const target = userFolders()[folder];
  if (!target) throw new Error("That folder is not allowlisted.");
  if (process.platform === "win32") return { ...detached("explorer.exe", [target]), folder };
  if (process.platform === "darwin") return { ...detached("open", [target]), folder };
  return { ...detached("xdg-open", [target]), folder };
}

function openWebsite(url) {
  let parsed;
  try { parsed = new URL(url); } catch { throw new Error("That is not a valid website URL."); }
  if (!["http:", "https:"].includes(parsed.protocol)) throw new Error("Only http and https websites are allowed.");
  if (process.platform === "win32") detached("cmd.exe", ["/c", "start", "", parsed.href]);
  else if (process.platform === "darwin") detached("open", [parsed.href]);
  else detached("xdg-open", [parsed.href]);
  return { opened: true, url: parsed.href };
}


function controlMedia(action) {
  if (process.platform !== "win32") throw new Error("Media controls are currently implemented for Windows only.");
  const scripts = {
    volume_up: "$w = New-Object -ComObject WScript.Shell; 1..3 | ForEach-Object { $w.SendKeys([char]175) }",
    volume_down: "$w = New-Object -ComObject WScript.Shell; 1..3 | ForEach-Object { $w.SendKeys([char]174) }",
    mute: "$w = New-Object -ComObject WScript.Shell; $w.SendKeys([char]173)",
    unmute: "$w = New-Object -ComObject WScript.Shell; $w.SendKeys([char]173)"
  };
  const script = scripts[action];
  if (!script) throw new Error("That media control is not allowlisted.");
  const child = spawn("powershell.exe", ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script], { detached: true, stdio: "ignore", windowsHide: true });
  child.unref();
  return { started: true, action };
}

function controlPc(action) {
  if (process.platform !== "win32") throw new Error("Power/session controls are currently implemented for Windows only.");
  if (action === "lock") return { ...detached("rundll32.exe", ["user32.dll,LockWorkStation"]), action };
  if (action === "sleep") return { ...detached("rundll32.exe", ["powrprof.dll,SetSuspendState", "0,1,0"]), action };
  if (action === "restart") return { ...detached("shutdown.exe", ["/r", "/t", "15", "/c", "JARVIS requested a restart"]), action, delaySeconds: 15 };
  if (action === "shutdown") return { ...detached("shutdown.exe", ["/s", "/t", "15", "/c", "JARVIS requested a shutdown"]), action, delaySeconds: 15 };
  throw new Error("That PC control is not allowlisted.");
}

async function searchPcFiles({ root, query, maxResults = 20 }) {
  const base = userFolders()[root];
  if (!base) throw new Error("That search folder is not allowlisted.");
  const needle = String(query).toLowerCase();
  const results = [];
  let visited = 0;
  const ignored = new Set([".git", "node_modules", ".piper-env", "voices", ".runtime"]);
  async function walk(dir) {
    if (results.length >= maxResults || visited >= 5000) return;
    let entries = [];
    try { entries = await readdir(dir, { withFileTypes: true }); } catch { return; }
    for (const entry of entries) {
      if (results.length >= maxResults || visited >= 5000) break;
      visited++;
      if (entry.name.startsWith(".") || ignored.has(entry.name)) continue;
      const full = join(dir, entry.name);
      if (entry.name.toLowerCase().includes(needle)) results.push(full);
      if (entry.isDirectory()) await walk(full);
    }
  }
  await walk(resolve(base));
  return { root, query, results, truncated: visited >= 5000 };
}

async function createReminder(args) {
  const reminders = await readStore("reminders.json", []);
  const reminder = { id: randomUUID(), title: args.title, when: args.when, createdAt: new Date().toISOString(), done: false };
  reminders.push(reminder);
  await writeStore("reminders.json", reminders);
  return reminder;
}

async function listReminders() {
  return { reminders: await readStore("reminders.json", []) };
}

function messagesFromWebhook(payload) {
  const messages = [];
  for (const entry of payload?.entry || []) {
    for (const change of entry.changes || []) {
      const value = change.value || {};
      const contacts = new Map((value.contacts || []).map((contact) => [contact.wa_id, contact.profile?.name || ""]));
      for (const message of value.messages || []) {
        const text = message.text?.body || message.button?.text || message.interactive?.button_reply?.title || `[${message.type || "message"}]`;
        messages.push({ id: message.id, from: message.from, name: contacts.get(message.from) || "", timestamp: message.timestamp, type: message.type, text });
      }
    }
  }
  return messages;
}

async function getWhatsAppInbox(limit = 10) {
  const stored = await readStore("whatsapp-events.json", []);
  const events = Array.isArray(stored) ? stored : [stored];
  const messages = events.flatMap((event) => messagesFromWebhook(event.payload || event)).sort((a, b) => String(b.timestamp).localeCompare(String(a.timestamp)));
  return { officialBusinessApiOnly: true, messages: messages.slice(0, Math.min(Number(limit) || 10, 20)) };
}

export async function executeTool(name, args = {}) {
  switch (name) {
    case "get_time": return { now: new Date().toString(), iso: new Date().toISOString() };
    case "get_email_briefing": {
      const provider = args.provider || "gmail";
      if (provider === "gmail") return gmailBriefing();
      if (provider === "outlook") return outlookBriefing();
      try { return await gmailBriefing(); } catch (gmailError) {
        try { return await outlookBriefing(); } catch { throw gmailError; }
      }
    }
    case "get_calendar_briefing": {
      const provider = args.provider || "google";
      if (provider === "google") return googleCalendarBriefing();
      if (provider === "microsoft") return outlookCalendarBriefing();
      try { return await googleCalendarBriefing(); } catch (googleError) {
        try { return await outlookCalendarBriefing(); } catch { throw googleError; }
      }
    }
    case "get_whatsapp_inbox": return getWhatsAppInbox(args.limit);
    case "send_whatsapp": return sendWhatsApp(args);
    case "open_pc_app": return openPcApp(args.app);
    case "open_pc_folder": return openPcFolder(args.folder);
    case "open_website": return openWebsite(args.url);
    case "control_pc": return controlPc(args.action);
    case "control_media": return controlMedia(args.action);
    case "search_pc_files": return searchPcFiles(args);
    case "create_reminder": return createReminder(args);
    case "list_reminders": return listReminders();
    case "get_system_status": return publicConfig();
    case "mobile_status": return mobileStatus();
    case "mobile_screenshot": return mobileScreenshot();
    case "mobile_open_app": return mobileOpenApp(args.app);
    case "mobile_key": return mobileKey(args.key);
    case "mobile_tap": return mobileTap(args.x, args.y);
    case "mobile_swipe": return mobileSwipe(args.x1,args.y1,args.x2,args.y2,args.duration);
    case "mobile_type": return mobileType(args.text);
    default: throw new Error(`Unknown tool: ${name}`);
  }
}
