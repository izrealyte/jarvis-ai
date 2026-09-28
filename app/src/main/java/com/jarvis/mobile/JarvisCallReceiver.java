package com.jarvis.mobile;
import android.content.*; import android.telephony.TelephonyManager;
/** Reports call-state transitions. Android may withhold phone numbers unless the app has the required role/permissions. */
public class JarvisCallReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if(!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(i.getAction()))return;String s=i.getStringExtra(TelephonyManager.EXTRA_STATE);String n=i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);Bridge.send(c,"call_state","{\"state\":"+Bridge.q(s)+",\"number\":"+Bridge.q(n==null?"":n)+",\"time\":"+System.currentTimeMillis()+"}");}
}
