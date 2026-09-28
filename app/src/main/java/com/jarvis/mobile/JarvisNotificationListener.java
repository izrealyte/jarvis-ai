package com.jarvis.mobile;
import android.app.Notification;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;

public class JarvisNotificationListener extends NotificationListenerService {
 private static JarvisNotificationListener instance;
 private final Map<String,Long> last=new HashMap<>();
 private static final String WA="com.whatsapp";
 private static final String WAB="com.whatsapp.w4b";
 @Override public void onListenerConnected(){instance=this;}
 @Override public void onDestroy(){instance=null;super.onDestroy();}
 @Override public void onNotificationPosted(StatusBarNotification s){
  if(s==null||s.getNotification()==null)return; Bundle e=s.getNotification().extras;
  String title=String.valueOf(e.getCharSequence("android.title",""));
  String text=String.valueOf(e.getCharSequence("android.text","")); String pkg=s.getPackageName(); long now=System.currentTimeMillis();
  boolean burst=now-last.getOrDefault(pkg,0L)<15000; last.put(pkg,now);
  Bridge.send(this,"notification","{\"package\":"+q(pkg)+",\"title\":"+q(title)+",\"text\":"+q(text)+",\"time\":"+now+",\"pattern\":"+q(burst?"burst":"normal")+"}");
  if(WA.equals(pkg)||WAB.equals(pkg)){
   String readable=(text==null||text.trim().isEmpty())?"You have a WhatsApp message from "+title+".":"You have a WhatsApp message from "+title+": "+text;
   getSharedPreferences("jarvis_notifications",MODE_PRIVATE).edit().putString("whatsapp_last",readable).putLong("whatsapp_time",now).putString("whatsapp_key",s.getKey()).apply();
   if(JarvisAssistantService.isRunning()) JarvisAssistantServiceProxy.speak(this,readable);
  }
 }
 public static boolean replyToLatestWhatsApp(Context context, String reply){
  try{
   if(instance==null)return false;
   StatusBarNotification[] active=instance.getActiveNotifications(); if(active==null)return false;
   StatusBarNotification best=null; long newest=0;
   for(StatusBarNotification n:active){if(n==null)continue;String p=n.getPackageName();if((WA.equals(p)||WAB.equals(p))&&n.getPostTime()>=newest){best=n;newest=n.getPostTime();}}
   if(best==null)return false;
   Notification.Action[] actions=best.getNotification().actions; if(actions==null)return false;
   for(Notification.Action a:actions){if(a==null||a.getRemoteInputs()==null||a.getRemoteInputs().length==0)continue;Intent fill=new Intent();Bundle b=new Bundle();for(RemoteInput ri:a.getRemoteInputs())b.putCharSequence(ri.getResultKey(),reply);RemoteInput.addResultsToIntent(a.getRemoteInputs(),fill,b);a.actionIntent.send(instance,0,fill);return true;}
  }catch(Throwable ignored){}
  return false;
 }
 static String q(String s){return "\""+(s==null?"":s).replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ")+"\"";}
}
