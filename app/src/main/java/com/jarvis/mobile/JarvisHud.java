package com.jarvis.mobile;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.TextView;
import java.util.Locale;

/** Lightweight, non-touch JARVIS HUD. It floats only when overlay permission is granted. */
public final class JarvisHud {
    private static WindowManager wm;
    private static View root;
    private static TextView status;
    private static boolean attached;

    private JarvisHud() {}

    public static synchronized void show(Context context, String state) {
        if (!android.provider.Settings.canDrawOverlays(context)) return;
        if (attached) { update(state); return; }
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        android.widget.FrameLayout box = new android.widget.FrameLayout(context);
        box.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(232, 5, 13, 22));
        bg.setStroke(dp(context, 1), Color.argb(210, 77, 235, 255));
        bg.setCornerRadius(dp(context, 28));
        box.setBackground(bg);

        TextView title = new TextView(context);
        title.setText("JARVIS  •  ONLINE");
        title.setTextColor(Color.rgb(77,235,255));
        title.setTextSize(13);
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        box.addView(title, new android.widget.FrameLayout.LayoutParams(-2, -2));

        status = new TextView(context);
        status.setText(state == null ? "LISTENING" : state);
        status.setTextColor(Color.WHITE);
        status.setTextSize(11);
        status.setPadding(0, dp(context, 22), 0, 0);
        android.widget.FrameLayout.LayoutParams sp = new android.widget.FrameLayout.LayoutParams(-2, -2);
        box.addView(status, sp);

        root = box;
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = dp(context, 38);
        try { wm.addView(root, p); attached = true; } catch (Exception ignored) { attached = false; }
    }

    public static synchronized void update(String state) {
        if (status != null) status.setText(state == null ? "LISTENING" : state);
    }

    public static synchronized void hide() {
        if (!attached || wm == null || root == null) return;
        try { wm.removeView(root); } catch (Exception ignored) {}
        root = null; status = null; attached = false;
    }

    public static synchronized boolean isAttached() { return attached; }

    private static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
}
