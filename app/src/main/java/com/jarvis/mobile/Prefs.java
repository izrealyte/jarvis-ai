package com.jarvis.mobile;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    static final String P = "jarvis";

    static SharedPreferences p(Context c) { return c.getSharedPreferences(P, 0); }

    public static String url(Context c) { return p(c).getString("url", ""); }
    public static void setUrl(Context c, String url) { p(c).edit().putString("url", url).apply(); }

    public static String token(Context c) { return p(c).getString("token", ""); }
    public static void setToken(Context c, String token) { p(c).edit().putString("token", token).apply(); }

    public static final String DEFAULT_ORION_URL = "http://127.0.0.1:8888";

    public static String orionUrl(Context c) { return p(c).getString("orion_url", DEFAULT_ORION_URL); }
    public static void setOrionUrl(Context c, String url) { p(c).edit().putString("orion_url", url).apply(); }

    public static String groqKey(Context c) { return p(c).getString("groq_key", ""); }
    public static void setGroqKey(Context c, String key) { p(c).edit().putString("groq_key", key).apply(); }

    public static String geminiKey(Context c) { return p(c).getString("gemini_key", ""); }
    public static void setGeminiKey(Context c, String key) { p(c).edit().putString("gemini_key", key).apply(); }

    public static void setWorkMode(Context c, boolean on) { p(c).edit().putBoolean("work_mode", on).apply(); }
    public static boolean workMode(Context c) { return p(c).getBoolean("work_mode", false); }

    private Prefs() {}
}
