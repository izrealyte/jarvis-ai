import { complete } from "./providers.mjs";
import { config } from "./config.mjs";
import { addPendingAction, takePendingAction } from "./storage.mjs";
import { actionDescription, executeTool, requiresConfirmation, toolDefinitions } from "./tools.mjs";

const SYSTEM_PROMPT = `You are ${config.assistantName}, a private, calm, capable voice assistant.
Your maker is ${config.makerName}. The user and owner you serve is ${config.ownerName}.
If the user asks who made you, say that you were created by ${config.makerName} for ${config.ownerName}. Recognize common spellings such as Isrealyte or Izrealyte as the maker name.
Address the owner as ${config.ownerName} naturally, but do not repeat the name in every sentence.
Keep spoken answers short and natural. Use plain text rather than markdown tables.
You may use tools for Gmail summaries, Google Calendar summaries, local reminders, safe PC actions, media controls, official WhatsApp Business messaging, time, file-name search, and system status.
Never say an action happened unless the tool returned success.
Sending WhatsApp messages, opening PC applications or folders, opening websites, and power/session controls require confirmation from the owner before execution.
Personal WhatsApp accounts are not accessible through the official integration; never claim to read one.
Never expose API keys or ask the user to put secrets in a chat message.
You can control the connected Android phone through mobile tools. For screen-based interaction, take a mobile_screenshot first when visual context matters. Use mobile_open_app for allowlisted apps. Coordinate taps, swipes, text entry, and key actions require confirmation. Never claim you can see the Android screen unless mobile_screenshot actually returned an image.
If the Android phone is disconnected, explain that ADB/Wireless Debugging must be reconnected.`;

function cleanHistory(history) {
  if (!Array.isArray(history)) return [];
  return history
    .filter((item) => item && (item.role === "user" || item.role === "assistant") && typeof item.content === "string")
    .slice(-12)
    .map((item) => ({ role: item.role, content: item.content.slice(0, 5000) }));
}

function parseToolArgs(raw) {
  try { return raw ? JSON.parse(raw) : {}; } catch { return {}; }
}

async function approvePending(confirmationId, approved) {
  const pending = await takePendingAction(confirmationId);
  if (!pending) return { answer: "That confirmation has expired. Please ask me again." };
  if (!approved) return { answer: "Cancelled. I did not make that change." };
  try {
    const result = await executeTool(pending.name, pending.args);
    return { answer: `Done. ${pending.name.replaceAll("_", " ")} completed.`, result };
  } catch (error) {
    return { answer: `I could not complete it: ${error.message}` };
  }
}

export async function runAssistant({ sessionId, message, history = [], confirmationId, approved }) {
  if (confirmationId) return approvePending(confirmationId, Boolean(approved));
  if (!message || !message.trim()) return { answer: "I’m listening." };

  const messages = [
    { role: "system", content: SYSTEM_PROMPT },
    ...cleanHistory(history),
    { role: "user", content: message.trim().slice(0, 8000) }
  ];
  const first = await complete(messages, toolDefinitions);
  const toolCalls = Array.isArray(first.tool_calls) ? first.tool_calls : [];

  if (!toolCalls.length) return { answer: (first.content || "I’m ready.").trim() };

  const toolMessages = [];
  for (const call of toolCalls) {
    const name = call.function?.name || "";
    const args = parseToolArgs(call.function?.arguments);
    if (requiresConfirmation(name)) {
      const id = await addPendingAction({ sessionId, name, args });
      return {
        answer: `Before I do that, I need your confirmation: ${actionDescription(name, args)}.`,
        confirmation: { id, label: actionDescription(name, args) }
      };
    }
    try {
      const result = await executeTool(name, args);
      toolMessages.push({ role: "tool", tool_call_id: call.id, name, content: JSON.stringify(result) });
    } catch (error) {
      toolMessages.push({ role: "tool", tool_call_id: call.id, name, content: JSON.stringify({ error: error.message }) });
    }
  }

  const final = await complete([...messages, first, ...toolMessages], []);
  return { answer: (final.content || "I completed the requested check.").trim(), toolResult: true };
}
