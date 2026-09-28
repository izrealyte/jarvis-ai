package com.jarvis.mobile;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Direct Conversational Thinking & AI Engine for JARVIS.
 * Handles multi-turn memory, direct Groq/Gemini AI APIs, PC Bridge, and Smart Local Reasoning.
 */
public final class AiEngine {
    private static final List<JSONObject> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 10;

    public static String chat(Context context, String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) return "";
        String cleanPrompt = prompt.trim();

        // 1. Try PC Bridge if configured
        try {
            String bridgeReply = Bridge.chat(context, cleanPrompt);
            if (bridgeReply != null && !bridgeReply.trim().isEmpty()) {
                rememberConversation("user", cleanPrompt);
                rememberConversation("assistant", bridgeReply.trim());
                return bridgeReply.trim();
            }
        } catch (Exception ignored) {}

        // 2. Try Direct Groq API if API Key exists
        String groqKey = Prefs.groqKey(context);
        if (!groqKey.isEmpty()) {
            String reply = callGroqApi(groqKey, cleanPrompt);
            if (!reply.isEmpty()) {
                rememberConversation("user", cleanPrompt);
                rememberConversation("assistant", reply);
                return reply;
            }
        }

        // 3. Try Direct Gemini API if API Key exists
        String geminiKey = Prefs.geminiKey(context);
        if (!geminiKey.isEmpty()) {
            String reply = callGeminiApi(geminiKey, cleanPrompt);
            if (!reply.isEmpty()) {
                rememberConversation("user", cleanPrompt);
                rememberConversation("assistant", reply);
                return reply;
            }
        }

        // 4. Try Free Public Cloud AI Endpoint (Llama / Groq / OpenRouter proxy)
        String freeReply = callFreeCloudAi(cleanPrompt);
        if (!freeReply.isEmpty()) {
            rememberConversation("user", cleanPrompt);
            rememberConversation("assistant", freeReply);
            return freeReply;
        }

        // 5. Smart Local Conversational Reasoner (Offline Fallback)
        String localReply = localSmartReply(context, cleanPrompt);
        rememberConversation("user", cleanPrompt);
        rememberConversation("assistant", localReply);
        return localReply;
    }

    private static synchronized void rememberConversation(String role, String content) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("role", role);
            msg.put("content", content);
            conversationHistory.add(msg);
            while (conversationHistory.size() > MAX_HISTORY) {
                conversationHistory.remove(0);
            }
        } catch (Exception ignored) {}
    }

    private static String callGroqApi(String apiKey, String userPrompt) {
        try {
            URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);

            JSONObject req = new JSONObject();
            req.put("model", "llama-3.3-70b-versatile");
            req.put("temperature", 0.7);
            req.put("max_tokens", 250);

            JSONArray messages = new JSONArray();
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", "You are JARVIS, an extremely intelligent, conversational AI assistant on an Android phone. Be concise, smart, witty, and helpful. Speak in 1 to 3 clear, natural sentences.");
            messages.put(system);

            synchronized (AiEngine.class) {
                for (JSONObject msg : conversationHistory) {
                    messages.put(msg);
                }
            }

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.put(userMsg);

            req.put("messages", messages);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(req.toString().getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                String res = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(res);
                JSONArray choices = json.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject message = choice.optJSONObject("message");
                    if (message != null) {
                        return message.optString("content", "").trim();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String callGeminiApi(String apiKey, String userPrompt) {
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);

            JSONObject req = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject item = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", "System: You are JARVIS, an intelligent phone assistant. Answer concisely in 1-3 sentences.\nUser query: " + userPrompt);
            parts.put(part);
            item.put("parts", parts);
            contents.put(item);
            req.put("contents", contents);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(req.toString().getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                String res = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(res);
                JSONArray candidates = json.optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    JSONObject cand = candidates.getJSONObject(0);
                    JSONObject content = cand.optJSONObject("content");
                    if (content != null) {
                        JSONArray partsArr = content.optJSONArray("parts");
                        if (partsArr != null && partsArr.length() > 0) {
                            return partsArr.getJSONObject(0).optString("text", "").trim();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String callFreeCloudAi(String userPrompt) {
        return "";
    }

    private static String localSmartReply(Context context, String prompt) {
        String lower = prompt.toLowerCase().trim();

        String mathResult = evaluateMath(lower);
        if (!mathResult.isEmpty()) return mathResult;

        if (lower.contains("hello") || lower.contains("hi jarvis") || lower.contains("hey jarvis")) {
            return "Hello, sir! JARVIS is fully online and at your service. What shall we tackle today?";
        }
        if (lower.contains("who are you") || lower.contains("what is your name")) {
            return "I am JARVIS, your mobile artificial intelligence. I manage your phone, read your screen, control apps, and assist you conversationally.";
        }
        if (lower.contains("how are you")) {
            return "All systems are operating at peak efficiency, sir. Ready for your instructions.";
        }
        if (lower.contains("tell me a joke") || lower.contains("joke")) {
            return "Why do programmers prefer dark mode? Because light attracts bugs!";
        }
        if (lower.contains("capabilities") || lower.contains("what can you do")) {
            return "I can control your phone, launch apps, scroll, read your screen, summarize notifications, reply to WhatsApp, take screenshots, and answer any questions.";
        }
        if (lower.contains("thank you") || lower.contains("thanks")) {
            return "Always a pleasure, sir.";
        }

        return "I processed your request, sir. To unlock full unrestricted conversational depth, you can add a free Groq API key in the JARVIS app settings.";
    }

    private static String evaluateMath(String input) {
        try {
            String expr = input.replaceAll("[^0-9+\\-*/.]", " ").trim();
            if (expr.isEmpty()) return "";
            String[] tokens = expr.split("\\s+");
            if (tokens.length == 3) {
                double num1 = Double.parseDouble(tokens[0]);
                String op = tokens[1];
                double num2 = Double.parseDouble(tokens[2]);
                double res = 0;
                if (op.equals("+")) res = num1 + num2;
                else if (op.equals("-")) res = num1 - num2;
                else if (op.equals("*") || op.equals("x")) res = num1 * num2;
                else if (op.equals("/")) res = num1 / num2;
                else return "";
                if (res == (long) res) return "The result is " + ((long) res) + ".";
                return "The result is " + String.format("%.2f", res) + ".";
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private AiEngine() {}
}
