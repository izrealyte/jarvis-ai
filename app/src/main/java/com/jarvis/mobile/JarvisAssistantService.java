package com.jarvis.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Phone-first JARVIS runtime. Natural-language commands are normalized locally;
 * explicit "search for" is the only browser-search trigger.
 */
public class JarvisAssistantService extends Service implements TextToSpeech.OnInitListener {
    private static boolean running=false;
    private static JarvisAssistantService instance;
    public static boolean isRunning(){ return running; }
    public static void externalSpeak(String text){ if(instance!=null && text!=null){ instance.handler.post(() -> instance.speak(text)); } }
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextToSpeech tts;
    private boolean ready=false, speaking=false, restarting=false, speechMuted=false;
    private String pendingInstallApp = null;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private HandGestureDetector gestureDetector;

    @Override public void onCreate() {
        super.onCreate(); running=true; instance=this; createChannel();
        tts = new TextToSpeech(this, this);
        gestureDetector = new HandGestureDetector(this, new HandGestureDetector.OnHandGestureListener() {
            @Override public void onHandWave() {
                handler.post(() -> {
                    update("HAND WAVE DETECTED");
                    if (!speaking) restartListening(100);
                });
            }
            @Override public void onDoubleHandWave() {
                handler.post(() -> {
                    update("DOUBLE WAVE • READING SCREEN");
                    readScreen();
                });
            }
        });
        gestureDetector.start();

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle p){ update("LISTENING"); }
                public void onBeginningOfSpeech(){ update("HEARING YOU…"); }
                public void onRmsChanged(float r){}
                public void onBufferReceived(byte[] b){}
                public void onEndOfSpeech(){ update("PROCESSING…"); }
                public void onError(int e){ if(!speaking) restartListening(450); }
                public void onResults(Bundle b){
                    ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(r!=null&&!r.isEmpty()) handle(r.get(0)); else restartListening(300);
                }
                public void onPartialResults(Bundle b){}
                public void onEvent(int a, Bundle b){}
            });
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        }
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        startForeground(41, notification("JARVIS ONLINE • phone mode"));
        if(i!=null && "JARVIS_SPEAK".equals(i.getAction())){ String t=i.getStringExtra("text"); if(t!=null&&!t.isEmpty()) speak(t); return START_STICKY; }
        JarvisHud.show(this, "LISTENING");
        if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
            if(!ready) update("INITIALIZING…"); else restartListening(200);
        } else update("MICROPHONE PERMISSION REQUIRED");
        return START_STICKY;
    }

    private void handle(String text){
        try { handleInternal(text); } catch (Throwable e) { update("RECOVERING…"); speak("I hit a phone control error, but I am still here."); }
    }

    private void handleInternal(String text){
        String q=text.trim(); if(q.isEmpty()){restartListening(300);return;}
        String l=clean(q);
        // The wake word is not an action. Remove it before intent parsing.
        l=l.replaceFirst("^(hey\\s+)?jarvis[ ,:;.-]*\\s*", "").trim();
        if(l.isEmpty()){ restartListening(150); return; }
        update("THINKING…");

        // Speech control must work even while muted.
        if(has(l,"shut up","be quiet","stop talking","stop speaking","mute yourself")){ speechMuted=true; stopSpeech(); update("LISTENING • SILENT"); restartListening(200); return; }
        if(has(l,"keep talking","start talking","unmute","speak again","turn speech on")){ speechMuted=false; speak("Speech restored."); return; }

        // Follow-up to a missing-app prompt.
        if(pendingInstallApp!=null && (l.equals("yes") || l.equals("yeah") || l.equals("yep") || l.equals("sure") || l.contains("yes open") || l.contains("open it") || l.contains("open the play store"))){
            String app=pendingInstallApp; pendingInstallApp=null; openPlayStore(app); return;
        }
        if(pendingInstallApp!=null && (l.equals("no") || l.equals("nope") || l.contains("don't") || l.contains("do not"))){
            pendingInstallApp=null; speak("Okay."); return;
        }

        if(has(l,"stop listening","stop jarvis","go to sleep","sleep","stand by","standby")) { speak("Going to standby."); handler.postDelayed(this::stopSelf,900); return; }
        if(has(l,"go home","home screen","take me home","show home","minimize","minimize this","minimise")){ hideHudAndHome("Going home."); return; }
        if(has(l,"go back","press back","previous screen","return","back")){ JarvisAccessibilityService.global(JarvisAccessibilityService.AccessibilityServiceCompat.BACK); speak("Going back."); return; }
        if(has(l,"overview","show recent apps","open recent apps","recent apps","open recents","show recents")){ hideHud(); JarvisAccessibilityService.global(JarvisAccessibilityService.AccessibilityServiceCompat.RECENTS); speak("Opening overview."); return; }
        if(has(l,"close all apps","clear all apps","clear recent apps","close recent apps","clear my recents")){ hideHud(); boolean ok=JarvisAccessibilityService.clearRecents(); speak(ok?"Clearing recent apps.":"I opened overview. I need Accessibility access to clear recent apps."); return; }
        if(has(l,"close app","close this app","exit app","exit","get me out","quit app")){ JarvisAccessibilityService.global(JarvisAccessibilityService.AccessibilityServiceCompat.HOME); hideHud(); speak("Closing this app."); return; }
        if(has(l,"check my recent whatsapp message","check recent whatsapp message","read my recent whatsapp message","read recent whatsapp message","latest whatsapp message","last whatsapp message")){ readRecentWhatsApp(); return; }
        if(has(l,"show notifications","open notifications","notifications","read my notifications")){ hideHud(); JarvisAccessibilityService.global(JarvisAccessibilityService.AccessibilityServiceCompat.NOTIFICATIONS); speak("Opening notifications."); return; }
        if(has(l,"quick settings","open quick settings")){ hideHud(); JarvisAccessibilityService.global(JarvisAccessibilityService.AccessibilityServiceCompat.QUICK_SETTINGS); speak("Opening quick settings."); return; }
        if(has(l,"screenshot","take a screen shot","take screenshot")){ hideHud(); boolean ok=JarvisAccessibilityService.takeScreenshot(); speak(ok?"Taking a screenshot.":"Screenshot control needs Accessibility access."); return; }
        if(has(l,"what is on my screen","what's on my screen","read my screen","read this screen","read what is on my screen","read this","what does this say")){ readScreen(); return; }
        if(has(l,"start screen recording","record my screen")){ speak("Screen recording needs Android's screen capture confirmation. Use the screen capture control in JARVIS to authorize it."); return; }
        if(has(l,"start screen share","share my screen")){ speak("Screen sharing needs Android's screen capture confirmation. I can use the authorized screen capture session when it is available."); return; }
        if(has(l,"like this","like this video","like this tiktok","like the video","tap like")){ boolean ok=JarvisAccessibilityService.clickText("like","like video","like this video"); speak(ok?"Liked.":"I couldn't find the Like control on this screen."); return; }
        if(has(l,"double tap","double click")){ boolean ok=JarvisAccessibilityService.doubleTapCenter(); speak(ok?"Done.":"I couldn't perform the double tap."); return; }
        if(has(l,"work mode","professional mode","enable work mode","enable professional mode")){ Prefs.setWorkMode(this,true); speak("Professional work mode enabled. Sir, are you ready to work?"); return; }
        if(Prefs.workMode(this) && (l.equals("yes")||l.equals("yes jarvis")||l.equals("i am ready")||l.equals("ready"))){ openMapsForWork(); return; }
        if(has(l,"take control","take over","start work","begin work")){ Prefs.setWorkMode(this,true); openMapsForWork(); return; }
        if(has(l,"play music","play some music","start music","play a song")){ if(!openAnyApp("YouTube Music") && !openAnyApp("Spotify") && !openAnyApp("YouTube")) speak("I couldn't find a music app. Tell me which music app you want to use."); return; }
        if(has(l,"open opay","launch opay","start opay")){ if(!openAnyApp("OPay")) askInstall("OPay"); return; }
        if(has(l,"make a transfer","make transfer","transfer money","send money")){ if(openAnyApp("OPay")) speak("OPay is open. I can help you navigate, but you must review the recipient and approve the transaction yourself."); else askInstall("OPay"); return; }
        if(has(l,"reply to that","reply to him","reply to her","reply to them")){ String reply=extractReplyText(l); if(reply.isEmpty()) speak("Tell me what you want me to reply."); else if(JarvisNotificationListener.replyToLatestWhatsApp(this,reply)) speak("Reply sent."); else speak("I couldn't find a supported WhatsApp quick-reply action in the latest notification."); return; }
        if(has(l,"scroll down","scroll lower","go down","swipe down","move down")){ boolean ok=JarvisAccessibilityService.scroll(false); speak(ok?"Scrolling down.":"I need Accessibility access to scroll."); return; }
        if(has(l,"scroll up","scroll higher","go up","swipe up","move up")){ boolean ok=JarvisAccessibilityService.scroll(true); speak(ok?"Scrolling up.":"I need Accessibility access to scroll."); return; }

        if(has(l,"volume up","turn volume up","increase volume","louder")){ volume(true); return; }
        if(has(l,"volume down","turn volume down","decrease volume","quieter")){ volume(false); return; }
        if(has(l,"mute media","mute phone","silent mode")){ ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0); speak("Muted."); return; }
        if(has(l,"turn on flashlight","turn on torch","flashlight on","torch on")){ flashlight(true); return; }
        if(has(l,"turn off flashlight","turn off torch","flashlight off","torch off")){ flashlight(false); return; }

        if(has(l,"battery","battery level","how much battery","battery percentage")){ battery(); return; }
        if(has(l,"storage","free space","storage space","how much storage")){ storage(); return; }
        if(has(l,"phone info","device info","about this phone","device information")){ deviceInfo(); return; }
        if(has(l,"what time","current time")||l.equals("time")){ speak(new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date())); return; }
        if(has(l,"what date","today's date","todays date","current date")||l.equals("date")){ speak(new SimpleDateFormat("EEEE, MMMM d",Locale.getDefault()).format(new Date())); return; }
        if(l.contains("who are you")||l.contains("what are you")){ speak("I am JARVIS, your phone assistant. I can control supported phone functions, read accessible screen text and notifications, open apps, listen, speak, and search when you explicitly ask me to search."); return; }

        // Explicit app commands.
        String appName=extractAppCommand(l);
        if(appName!=null){
            if(openAnyApp(appName)) return;
            askInstall(appName); return;
        }
        // A bare installed-app name (for example "TikTok") should also work.
        if(openAnyApp(l)) return;

        // Explicit web search ONLY.
        if(l.startsWith("search for ") || l.startsWith("google ")){
            String s=l.startsWith("google ") ? l.substring("google ".length()).trim() : l.substring("search for ".length()).trim();
            if(!s.isEmpty()){ openUrl("https://www.google.com/search?q="+ Uri.encode(s)); speak("Searching for "+s); }
            else speak("What would you like me to search for?");
            return;
        }

        // Conversational brain: ask the configured JARVIS/Groq bridge first. Never open a browser here.
        speakThinkingThenChat(q);
    }

    private boolean has(String s,String... phrases){ for(String p:phrases) if(s.equals(p)||s.contains(p)) return true; return false; }
    private String clean(String s){ return s.toLowerCase(Locale.ROOT).replaceAll("[,.!?]"," ").replaceAll("\\s+"," ").trim(); }

    private String extractAppCommand(String l){
        String s=l.replaceFirst("^(hey\\s+)?jarvis[ ,:;.-]*\\s*","").trim();
        String[] prefixes={"open ","launch ","start ","run ","open the app ","open app ","launch the app ","start the app ","go to "};
        for(String p:prefixes) if(s.startsWith(p)) return s.substring(p.length()).trim();
        return null;
    }

    private boolean openAnyApp(String requested){
        String n=normalizeAppName(requested);
        if(n.equals("settings")){ hideHud(); startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); speak("Opening settings."); return true; }

        PackageManager pm=getPackageManager();
        List<ApplicationInfo> apps;
        try { apps=pm.getInstalledApplications(0); } catch(Exception e){ apps=new ArrayList<>(); }
        ApplicationInfo best=null; int bestScore=0;
        for(ApplicationInfo a:apps){
            String label=""; try { label=String.valueOf(pm.getApplicationLabel(a)); } catch(Exception ignored){}
            String ln=normalizeAppName(label); String pkg=a.packageName.toLowerCase(Locale.ROOT);
            int score=appScore(n,ln,pkg);
            if(score>bestScore){bestScore=score;best=a;}
        }
        if(best==null || bestScore<50) return false;
        Intent launch=pm.getLaunchIntentForPackage(best.packageName);
        if(launch==null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        hideHud();
        try{ startActivity(launch); speak("Opening "+pm.getApplicationLabel(best)); return true; }catch(Exception e){ return false; }
    }

    private String normalizeAppName(String s){
        String n=(s==null?"":s).toLowerCase(Locale.ROOT).trim();
        n=n.replaceAll("[^a-z0-9]+"," ").replaceAll("\\s+"," ").trim();
        if(n.equals("tik tok")) n="tiktok";
        if(n.equals("what s app")||n.equals("whatapp")) n="whatsapp";
        if(n.equals("face book")) n="facebook";
        if(n.equals("you tube")) n="youtube";
        if(n.equals("x app")) n="x";
        return n;
    }

    private int appScore(String requested,String label,String pkg){
        String r=normalizeAppName(requested), l=normalizeAppName(label), p=pkg.replace('.',' ');
        if(l.equals(r)) return 100;
        if(p.endsWith(" "+r) || p.equals(r)) return 95;
        if(l.contains(r) || r.contains(l)) return 80;
        if(p.contains(" "+r+" ") || p.contains(r)) return 65;
        // Common spoken aliases.
        if(r.equals("whatsapp") && (l.equals("whatsapp messenger") || p.contains("whatsapp"))) return 100;
        if(r.equals("facebook") && p.contains("facebook")) return 100;
        if(r.equals("messenger") && p.contains("facebook orca")) return 100;
        if(r.equals("tiktok") && p.contains("musically") ) return 100;
        if(r.equals("zoom") && p.contains("zoom")) return 100;
        return 0;
    }

    private void askInstall(String app){
        pendingInstallApp=app;
        speak(app+" isn't installed. Would you like me to open it in the Play Store?");
    }

    private void openPlayStore(String app){
        try{
            String q= Uri.encode(app);
            Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q="+q));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); hideHud(); speak("Opening the Play Store for "+app+".");
        }catch(Exception e){ openUrl("https://play.google.com/store/search?q="+ Uri.encode(app)+"&c=apps"); }
    }

    private void speakThinkingThenChat(String q){
        new Thread(() -> {
            try{
                String reply=AiEngine.chat(this,q);
                if(reply!=null && !reply.trim().isEmpty()){ speak(reply.trim()); return; }
            }catch(Exception ignored){}
            Bridge.send(this,"conversation", "{\"text\":"+Bridge.q(q)+"}");
            speak("I am sorry, sir. I was unable to generate a response right now.");
        },"jarvis-chat").start();
    }

    private void openMapsForWork(){
        try{ Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=businesses+without+website")); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); hideHud(); speak("Google Maps is open. I am ready for the business research workflow."); }catch(Exception e){ speak("I couldn't open Google Maps."); }
    }

    private String extractReplyText(String l){
        String[] keys={"reply to that ","reply to him ","reply to her ","reply to them "};
        for(String k:keys) if(l.startsWith(k)) return l.substring(k.length()).trim();
        return "";
    }

    private void readRecentWhatsApp(){
        SharedPreferences p=getSharedPreferences("jarvis_notifications",MODE_PRIVATE);
        String msg=p.getString("whatsapp_last","");
        if(msg.isEmpty()){ speak("I don't have a recent WhatsApp notification available. Make sure Notification Access is enabled for JARVIS."); return; }
        speak(msg);
    }

    private void readScreen(){
        if(JarvisAccessibilityService.isRunning()){ JarvisAccessibilityService.readScreenText(this); return; }
        speak("Accessibility access is needed for me to read the current screen.");
    }
    private void battery(){ BatteryManager b=(BatteryManager)getSystemService(BATTERY_SERVICE); int p=b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); speak("Battery is at "+p+" percent."); }
    private void storage(){ File path=Environment.getDataDirectory(); long free=path.getFreeSpace()/1073741824L,total=path.getTotalSpace()/1073741824L; speak("You have about "+free+" gigabytes free out of "+total+" gigabytes of internal storage."); }
    private void deviceInfo(){ speak("This is "+Build.MANUFACTURER+" "+Build.MODEL+", Android "+Build.VERSION.RELEASE+"."); }
    private void volume(boolean up){ AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);a.adjustStreamVolume(AudioManager.STREAM_MUSIC,up?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);speak(up?"Volume up.":"Volume down."); }
    private void flashlight(boolean on){ try{CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE);String id=cm.getCameraIdList()[0];cm.setTorchMode(id,on);speak(on?"Flashlight on.":"Flashlight off.");}catch(Exception e){speak("I couldn't control the flashlight on this phone.");} }
    private void hideHud(){JarvisHud.hide();}
    private void hideHudAndHome(String phrase){hideHud();JarvisAccessibilityService.global(JarvisAccessibilityService.AccessibilityServiceCompat.HOME);speak(phrase);}
    private void stopSpeech(){ if(tts!=null)tts.stop(); speaking=false; handler.removeCallbacksAndMessages(null); }
    private void speak(String s){
        if(s==null||s.trim().isEmpty()) return;
        handler.post(() -> {
            try {
                if(speechMuted){ update("LISTENING • SILENT"); restartListening(150); return; }
                speaking=true; update("SPEAKING…");
                if(tts==null || !ready){ speaking=false; restartListening(250); return; }
                tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"jarvis");
                long delay=Math.max(1400,s.length()*55L);
                handler.removeCallbacksAndMessages("speechDone");
                handler.postAtTime(()->{ speaking=false; restartListening(250); }, "speechDone", SystemClock.uptimeMillis()+delay);
            } catch(Throwable ignored){ speaking=false; restartListening(300); }
        });
    }
    private void restartListening(long delay){if(restarting||recognizer==null||speaking)return;restarting=true;handler.postDelayed(()->{restarting=false;if(!speaking&&(Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)){try{recognizer.startListening(recognizerIntent);}catch(Exception ignored){}}},delay);}
    private void update(String s){JarvisHud.show(this,s);JarvisHud.update(s);Intent b=new Intent("com.izrealyte.jarvismobile.STATUS");b.setPackage(getPackageName());b.putExtra("status",s);sendBroadcast(b);NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.notify(41,notification("JARVIS • "+s));}
    private Notification notification(String text){return new Notification.Builder(this,"jarvis_assistant").setContentTitle("JARVIS Mobile").setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("jarvis_assistant","JARVIS Assistant",NotificationManager.IMPORTANCE_LOW));}
    private void openUrl(String u){try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));hideHud();}catch(Exception ignored){}}
    @Override public void onInit(int status){ready=status==TextToSpeech.SUCCESS;if(ready){tts.setLanguage(Locale.getDefault());update("LISTENING");speak("JARVIS online. I'm listening.");}}
    @Override public void onDestroy(){running=false;instance=null;JarvisHud.hide();if(gestureDetector!=null){gestureDetector.stop();}if(recognizer!=null){recognizer.cancel();recognizer.destroy();}if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
