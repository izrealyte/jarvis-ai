package com.jarvis.mobile;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Small authenticated bridge between the Android companion and the local JARVIS server. */
public final class Bridge {
    public static void send(Context c, String type, String json) {
        String base = Prefs.url(c); if (base == null || base.isEmpty()) return;
        new Thread(() -> {
            try {
                HttpURLConnection h = connection(c, base.replaceAll("/$", "") + "/api/mobile/event", "POST");
                h.setRequestProperty("Content-Type", "application/json");
                write(h, ("{\"type\":" + q(type) + ",\"data\":" + json + "}").getBytes(StandardCharsets.UTF_8));
                h.getResponseCode(); h.disconnect();
            } catch (Exception ignored) {}
        }, "jarvis-event").start();
    }

    public static String get(Context c, String path) throws Exception {
        String base = Prefs.url(c); if (base == null || base.isEmpty()) return "";
        HttpURLConnection h = connection(c, base.replaceAll("/$", "") + path, "GET");
        int code = h.getResponseCode(); InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
        String out = read(in); h.disconnect(); return out;
    }

    /** Optional conversational endpoint. A PC JARVIS/Groq server can implement /api/mobile/chat. */
    public static String chat(Context c, String text) throws Exception {
        String raw = post(c, "/api/mobile/chat", "{\"text\":" + q(text) + ",\"device_id\":" + q(Device.id(c)) + "}");
        if(raw == null || raw.trim().isEmpty()) return "";
        try {
            JSONObject o = new JSONObject(raw);
            String[] keys={"reply","text","message","answer","response"};
            for(String k:keys){ String v=o.optString(k,""); if(v!=null&&!v.trim().isEmpty()) return v; }
        } catch(Exception ignored) {}
        return "";
    }

    public static String post(Context c, String path, String json) throws Exception {
        String base = Prefs.url(c); if (base == null || base.isEmpty()) return "";
        HttpURLConnection h = connection(c, base.replaceAll("/$", "") + path, "POST");
        h.setRequestProperty("Content-Type", "application/json");
        write(h, json.getBytes(StandardCharsets.UTF_8));
        int code = h.getResponseCode(); InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
        String out = read(in); h.disconnect(); return out;
    }

    public static void sendFrame(Context c, byte[] jpeg) {
        String base = Prefs.url(c); if (base == null || base.isEmpty()) return;
        new Thread(() -> {
            try {
                HttpURLConnection h = connection(c, base.replaceAll("/$", "") + "/api/mobile/frame", "POST");
                h.setRequestProperty("Content-Type", "image/jpeg");
                h.setRequestProperty("X-JARVIS-Device", Device.id(c));
                h.setFixedLengthStreamingMode(jpeg.length); write(h, jpeg); h.getResponseCode(); h.disconnect();
            } catch (Exception ignored) {}
        }, "jarvis-frame").start();
    }

    private static HttpURLConnection connection(Context c, String url, String method) throws Exception {
        HttpURLConnection h = (HttpURLConnection)new URL(url).openConnection();
        h.setRequestMethod(method); h.setConnectTimeout(2500); h.setReadTimeout(5000); h.setDoInput(true);
        if (method.equals("POST")) h.setDoOutput(true);
        String t = Prefs.token(c); if (t != null && !t.isEmpty()) h.setRequestProperty("Authorization", "Bearer " + t);
        h.setRequestProperty("X-JARVIS-Device", Device.id(c));
        return h;
    }
    private static void write(HttpURLConnection h, byte[] b) throws Exception { try(OutputStream o=h.getOutputStream()){o.write(b);} }
    private static String read(InputStream in) throws Exception { if(in==null)return ""; try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder s=new StringBuilder();String x;while((x=r.readLine())!=null)s.append(x);return s.toString();} }
    public static String q(String s){return "\""+(s==null?"":s).replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ")+"\"";}
    private Bridge() {}
}
