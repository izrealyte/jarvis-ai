import { randomUUID } from "node:crypto";
import { config, configured } from "./config.mjs";
import { readStore, writeStore } from "./storage.mjs";

async function jsonFetch(url, options = {}) {
  const response = await fetch(url, options);
  const raw = await response.text();
  let data = {};
  try { data = raw ? JSON.parse(raw) : {}; } catch {}
  if (!response.ok) {
    const detail = data?.error?.message || data?.error_description || data?.message || raw.slice(0, 240);
    throw new Error(`${response.status}: ${detail || "Integration request failed"}`);
  }
  return data;
}

async function saveToken(name, token) {
  const tokens = await readStore("tokens.json", {});
  tokens[name] = { ...token, expires_at: token.expires_in ? Date.now() + (token.expires_in * 1000) : token.expires_at };
  await writeStore("tokens.json", tokens);
}

async function getToken(name, refresh) {
  const tokens = await readStore("tokens.json", {});
  const token = tokens[name];
  if (!token) return null;
  if (token.access_token && (!token.expires_at || token.expires_at > Date.now() + 60_000)) return token.access_token;
  if (!token.refresh_token || !refresh) return token.access_token || null;
  const renewed = await refresh(token.refresh_token);
  await saveToken(name, { ...token, ...renewed });
  return renewed.access_token;
}

export async function createOAuthState(provider) {
  const state = crypto.randomUUID();
  const states = await readStore("oauth-states.json", {});
  states[state] = { provider, createdAt: Date.now() };
  await writeStore("oauth-states.json", states);
  return state;
}

export async function consumeOAuthState(state, provider) {
  const states = await readStore("oauth-states.json", {});
  const record = states[state];
  delete states[state];
  await writeStore("oauth-states.json", states);
  if (!record || record.provider !== provider || Date.now() - record.createdAt > 10 * 60 * 1000) return false;
  return true;
}

export function googleAuthUrl(state) {
  if (!configured(config.googleClientId)) throw new Error("GOOGLE_CLIENT_ID is not configured.");
  const params = new URLSearchParams({
    client_id: config.googleClientId,
    redirect_uri: config.googleRedirectUri,
    response_type: "code",
    access_type: "offline",
    prompt: "consent",
    scope: "openid email https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/calendar.readonly",
    state
  });
  return `https://accounts.google.com/o/oauth2/v2/auth?${params}`;
}

export async function exchangeGoogleCode(code) {
  const token = await jsonFetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      code,
      client_id: config.googleClientId,
      client_secret: config.googleClientSecret,
      redirect_uri: config.googleRedirectUri,
      grant_type: "authorization_code"
    })
  });
  await saveToken("google", token);
}

async function googleAccessToken() {
  return getToken("google", async (refreshToken) => jsonFetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: config.googleClientId,
      client_secret: config.googleClientSecret,
      refresh_token: refreshToken,
      grant_type: "refresh_token"
    })
  }));
}

export async function gmailBriefing() {
  const token = await googleAccessToken();
  if (!token) throw new Error("Connect Google from the Integrations panel first.");
  const query = encodeURIComponent("is:unread newer_than:7d");
  const list = await jsonFetch(`https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=8&q=${query}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  const messages = [];
  for (const item of (list.messages || []).slice(0, 8)) {
    const message = await jsonFetch(`https://gmail.googleapis.com/gmail/v1/users/me/messages/${item.id}?format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    const headers = Object.fromEntries((message.payload?.headers || []).map((header) => [header.name.toLowerCase(), header.value]));
    messages.push({ subject: headers.subject || "(No subject)", from: headers.from || "Unknown sender", date: headers.date || "", snippet: message.snippet || "" });
  }
  return { provider: "Gmail", count: messages.length, messages };
}

export async function googleCalendarBriefing() {
  const token = await googleAccessToken();
  if (!token) throw new Error("Connect Google from the Integrations panel first.");
  const start = new Date();
  const end = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
  const params = new URLSearchParams({
    timeMin: start.toISOString(),
    timeMax: end.toISOString(),
    singleEvents: "true",
    orderBy: "startTime",
    maxResults: "10"
  });
  const data = await jsonFetch(`https://www.googleapis.com/calendar/v3/calendars/primary/events?${params}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  return { provider: "Google Calendar", events: (data.items || []).map((event) => ({
    title: event.summary || "(Untitled event)",
    start: event.start?.dateTime || event.start?.date || "",
    end: event.end?.dateTime || event.end?.date || ""
  })) };
}

export function microsoftAuthUrl(state) {
  if (!configured(config.microsoftClientId)) throw new Error("MICROSOFT_CLIENT_ID is not configured.");
  const params = new URLSearchParams({
    client_id: config.microsoftClientId,
    response_type: "code",
    redirect_uri: config.microsoftRedirectUri,
    response_mode: "query",
    scope: "openid profile offline_access User.Read Mail.Read Calendars.Read",
    state
  });
  return `https://login.microsoftonline.com/${config.microsoftTenant}/oauth2/v2.0/authorize?${params}`;
}

export async function exchangeMicrosoftCode(code) {
  const token = await jsonFetch(`https://login.microsoftonline.com/${config.microsoftTenant}/oauth2/v2.0/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: config.microsoftClientId,
      client_secret: config.microsoftClientSecret,
      code,
      redirect_uri: config.microsoftRedirectUri,
      grant_type: "authorization_code",
      scope: "openid profile offline_access User.Read Mail.Read Calendars.Read"
    })
  });
  await saveToken("microsoft", token);
}

async function microsoftAccessToken() {
  return getToken("microsoft", async (refreshToken) => jsonFetch(`https://login.microsoftonline.com/${config.microsoftTenant}/oauth2/v2.0/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: config.microsoftClientId,
      client_secret: config.microsoftClientSecret,
      refresh_token: refreshToken,
      redirect_uri: config.microsoftRedirectUri,
      grant_type: "refresh_token",
      scope: "openid profile offline_access User.Read Mail.Read Calendars.Read"
    })
  }));
}

export async function outlookBriefing() {
  const token = await microsoftAccessToken();
  if (!token) throw new Error("Connect Microsoft from the Integrations panel first.");
  const params = new URLSearchParams({ "$top": "8", "$orderby": "receivedDateTime desc", "$filter": "isRead eq false", "$select": "subject,from,receivedDateTime,bodyPreview" });
  const data = await jsonFetch(`https://graph.microsoft.com/v1.0/me/messages?${params}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  return { provider: "Outlook", count: (data.value || []).length, messages: (data.value || []).map((message) => ({
    subject: message.subject || "(No subject)",
    from: message.from?.emailAddress?.address || "Unknown sender",
    date: message.receivedDateTime || "",
    snippet: message.bodyPreview || ""
  })) };
}

export async function outlookCalendarBriefing() {
  const token = await microsoftAccessToken();
  if (!token) throw new Error("Connect Microsoft from the Integrations panel first.");
  const start = new Date();
  const end = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
  const params = new URLSearchParams({ startDateTime: start.toISOString(), endDateTime: end.toISOString(), "$top": "10", "$orderby": "start/dateTime", "$select": "subject,start,end" });
  const data = await jsonFetch(`https://graph.microsoft.com/v1.0/me/calendarView?${params}`, {
    headers: { Authorization: `Bearer ${token}`, Prefer: 'outlook.timezone="UTC"' }
  });
  return { provider: "Outlook Calendar", events: (data.value || []).map((event) => ({ title: event.subject || "(Untitled event)", start: event.start?.dateTime || "", end: event.end?.dateTime || "" })) };
}

export async function integrationStatus() {
  const tokens = await readStore("tokens.json", {});
  return {
    google: { configured: configured(config.googleClientId) && configured(config.googleClientSecret), connected: Boolean(tokens.google?.access_token) },
    microsoft: { configured: configured(config.microsoftClientId) && configured(config.microsoftClientSecret), connected: Boolean(tokens.microsoft?.access_token) },
    whatsapp: { configured: configured(config.whatsappPhoneNumberId) && configured(config.metaAccessToken) }
  };
}
