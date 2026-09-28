package com.jarvis.mobile;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.Intent;
import java.util.*;

public class JarvisAccessibilityService extends AccessibilityService {
 public static final class AccessibilityServiceCompat { public static final int HOME=1,BACK=2,RECENTS=3,NOTIFICATIONS=4,QUICK_SETTINGS=5,POWER_DIALOG=6; }
 private static JarvisAccessibilityService instance;
 @Override public void onServiceConnected(){instance=this;Bridge.send(this,"agent_status","{\"accessibility\":true}");}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){
  if(e==null)return; CharSequence p=e.getPackageName(); String pkg=p==null?"":p.toString();
  Bridge.send(this,"ui_event","{\"package\":"+Bridge.q(pkg)+",\"type\":"+e.getEventType()+"}");
  if(JarvisAssistantService.isRunning()){
   try{ Intent homeI=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)==null?null:new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME); String home=homeI==null?"":String.valueOf(getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0).activityInfo.packageName); if(pkg.equals(getPackageName())||pkg.equals(home))JarvisHud.show(this,"LISTENING");else JarvisHud.hide(); }catch(Throwable ignored){}
  }
 }
 @Override public void onInterrupt(){Bridge.send(this,"agent_status","{\"accessibility\":false}");}
 @Override public void onDestroy(){instance=null;super.onDestroy();}
 public static boolean isRunning(){return instance!=null;}
 public static boolean takeScreenshot(){return instance!=null&& Build.VERSION.SDK_INT>=30&&instance.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);}
 public static boolean global(int action){if(instance==null)return false;int x=GLOBAL_BACK;switch(action){case AccessibilityServiceCompat.HOME:x=GLOBAL_HOME;break;case AccessibilityServiceCompat.BACK:x=GLOBAL_BACK;break;case AccessibilityServiceCompat.RECENTS:x=GLOBAL_RECENTS;break;case AccessibilityServiceCompat.NOTIFICATIONS:x=GLOBAL_NOTIFICATIONS;break;case AccessibilityServiceCompat.QUICK_SETTINGS:x=GLOBAL_QUICK_SETTINGS;break;case AccessibilityServiceCompat.POWER_DIALOG:x=GLOBAL_POWER;break;default:return false;}return instance.performGlobalAction(x);}
 public static boolean tap(int x,int y){if(instance==null)return false;AccessibilityNodeInfo r=instance.getRootInActiveWindow();return tapNode(r,x,y);}
 private static boolean tapNode(AccessibilityNodeInfo n,int x,int y){if(n==null)return false;Rect b=new Rect();n.getBoundsInScreen(b);if(b.contains(x,y)&&n.isClickable())return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null&&tapNode(c,x,y))return true;}return false;}
 public static boolean swipe(int x1,int y1,int x2,int y2,long duration){if(instance==null)return false;Path p=new Path();p.moveTo(x1,y1);p.lineTo(x2,y2);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,Math.max(100,duration))).build();return instance.dispatchGesture(g,null,null);}
 public static boolean type(String text){if(instance==null)return false;AccessibilityNodeInfo r=instance.getRootInActiveWindow();return typeNode(r,text);}
 private static boolean typeNode(AccessibilityNodeInfo n,String text){if(n==null)return false;if(n.isEditable()){Bundle arguments=new Bundle();arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);if(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments))return true;n.performAction(AccessibilityNodeInfo.ACTION_CLICK);if(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments))return true;}for(int i=0;i<n.getChildCount();i++)if(typeNode(n.getChild(i),text))return true;return false;}
 public static boolean doubleTapCenter(){if(instance==null)return false;
  DisplayMetrics m=instance.getResources().getDisplayMetrics();int x=m.widthPixels/2,y=m.heightPixels/2;Path p=new Path();p.moveTo(x,y);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,60)).addStroke(new GestureDescription.StrokeDescription(p,150,60)).build();return instance.dispatchGesture(g,null,null);}
 public static boolean clickText(String... names){if(instance==null)return false;AccessibilityNodeInfo r=instance.getRootInActiveWindow();return clickTextNode(r,names);}
 private static boolean clickTextNode(AccessibilityNodeInfo n,String...names){if(n==null)return false;String t=n.getText()==null?"":n.getText().toString();String d=n.getContentDescription()==null?"":n.getContentDescription().toString();for(String x:names)if(t.equalsIgnoreCase(x)||d.equalsIgnoreCase(x)||t.toLowerCase(Locale.ROOT).contains(x.toLowerCase(Locale.ROOT))||d.toLowerCase(Locale.ROOT).contains(x.toLowerCase(Locale.ROOT))){AccessibilityNodeInfo z=n;while(z!=null&&!z.isClickable())z=z.getParent();if(z!=null&&z.isClickable())return z.performAction(AccessibilityNodeInfo.ACTION_CLICK);}for(int i=0;i<n.getChildCount();i++)if(clickTextNode(n.getChild(i),names))return true;return false;}
 public static boolean scroll(boolean up){if(instance==null)return false;AccessibilityNodeInfo r=instance.getRootInActiveWindow();if(scrollNode(r,up))return true;
  DisplayMetrics m=instance.getResources().getDisplayMetrics();return swipe(m.widthPixels/2,up?(int)(m.heightPixels*.30):(int)(m.heightPixels*.75),m.widthPixels/2,up?(int)(m.heightPixels*.75):(int)(m.heightPixels*.30),450);}
 private static boolean scrollNode(AccessibilityNodeInfo n,boolean up){if(n==null)return false;if(n.isScrollable()){int a=up?AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;if(n.performAction(a))return true;}for(int i=0;i<n.getChildCount();i++)if(scrollNode(n.getChild(i),up))return true;return false;}
 public static boolean clearRecents(){if(instance==null)return false;instance.performGlobalAction(GLOBAL_RECENTS);new Handler(Looper.getMainLooper()).postDelayed(()->{AccessibilityNodeInfo r=instance==null?null:instance.getRootInActiveWindow();if(r!=null)clickTextNode(r,"Close all","CLEAR ALL","Clear all","Close all apps");},700);return true;}
 public static void readScreenText(Context c){if(instance==null)return;AccessibilityNodeInfo r=instance.getRootInActiveWindow();StringBuilder out=new StringBuilder();collect(r,out,0);String text=out.toString().replace('"',' ').trim();if(text.isEmpty()){JarvisAssistantServiceProxy.speak(c,"I can't see readable text on the current screen.");return;}String[] parts=text.split(",");StringBuilder spoken=new StringBuilder();int count=0;for(String x:parts){if(x.trim().isEmpty())continue;if(count++>0)spoken.append(". ");spoken.append(x.trim());if(count>=20)break;}JarvisAssistantServiceProxy.speak(c,spoken.toString());}
 private static void collect(AccessibilityNodeInfo n,StringBuilder out,int depth){if(n==null||depth>14)return;String t=n.getText()==null?"":n.getText().toString().trim();String d=n.getContentDescription()==null?"":n.getContentDescription().toString().trim();if(!t.isEmpty()||!d.isEmpty()){if(out.length()>0)out.append(',');out.append(Bridge.q(t.isEmpty()?d:(d.isEmpty()||d.equals(t)?t:t+" | "+d)));}for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),out,depth+1);}
 private static final int GLOBAL_BACK=AccessibilityService.GLOBAL_ACTION_BACK,GLOBAL_HOME=AccessibilityService.GLOBAL_ACTION_HOME,GLOBAL_RECENTS=AccessibilityService.GLOBAL_ACTION_RECENTS,GLOBAL_NOTIFICATIONS=AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,GLOBAL_QUICK_SETTINGS=AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,GLOBAL_POWER=AccessibilityService.GLOBAL_ACTION_POWER_DIALOG;
}
