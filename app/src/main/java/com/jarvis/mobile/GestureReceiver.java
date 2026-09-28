package com.jarvis.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * BroadcastReceiver for external motion/gesture apps like AirTouch, Tasker, or custom shortcuts.
 */
public class GestureReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String gesture = intent.getStringExtra("gesture");
        if (gesture == null) gesture = intent.getStringExtra("action");
        if (gesture == null) gesture = "";
        gesture = gesture.toUpperCase().trim();

        if (gesture.equals("WAVE") || gesture.equals("WAKE") || "com.jarvis.mobile.GESTURE_WAVE".equals(action)) {
            JarvisAssistantServiceProxy.speak(context, "Hand gesture detected.");
            Intent startIntent = new Intent(context, JarvisAssistantService.class);
            context.startForegroundService(startIntent);
        } else if (gesture.equals("SWIPE_UP") || gesture.equals("SCROLL_UP")) {
            JarvisAccessibilityService.scroll(true);
        } else if (gesture.equals("SWIPE_DOWN") || gesture.equals("SCROLL_DOWN")) {
            JarvisAccessibilityService.scroll(false);
        } else if (gesture.equals("READ") || gesture.equals("READ_SCREEN")) {
            JarvisAccessibilityService.readScreenText(context);
        } else if (gesture.equals("BACK")) {
            JarvisAccessibilityService.global(JarvisAccessibilityService.AccessibilityServiceCompat.BACK);
        } else if (gesture.equals("HOME")) {
            JarvisAccessibilityService.global(JarvisAccessibilityService.AccessibilityServiceCompat.HOME);
        }
    }
}
