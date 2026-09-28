package com.jarvis.mobile;
import android.content.Context; import org.json.*;
/** Lightweight local companion memory/cache; the PC JARVIS server can persist the canonical long-term memory. */
public final class Memory {
 public static void remember(Context c,String key,String value){c.getSharedPreferences("jarvis_memory",0).edit().putString(key,value).apply();Bridge.send(c,"memory","{\"key\":"+Bridge.q(key)+",\"value\":"+Bridge.q(value)+",\"source\":\"mobile\"}");}
 public static String recall(Context c,String key){return c.getSharedPreferences("jarvis_memory",0).getString(key,"");}
 private Memory(){}
}
