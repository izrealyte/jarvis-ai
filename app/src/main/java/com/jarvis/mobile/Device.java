package com.jarvis.mobile;
import android.content.Context; import android.provider.Settings;
public final class Device {
 public static String id(Context c){return Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);}
 private Device(){}
}
