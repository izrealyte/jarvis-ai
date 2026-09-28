package com.jarvis.mobile;
import android.content.Context;
public final class JarvisAssistantServiceProxy {
 public static void speak(Context c,String text){ JarvisAssistantService.externalSpeak(text); }
 private JarvisAssistantServiceProxy(){}
}
