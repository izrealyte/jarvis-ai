package com.jarvis.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.net.Uri;
import android.widget.*;

public class MainActivity extends Activity {
    TextView status;
    EditText groqKeyInput;
    BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            if (i != null && i.hasExtra("status")) {
                status.setText(i.getStringExtra("status"));
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        groqKeyInput = findViewById(R.id.groqKeyInput);

        String currentKey = Prefs.groqKey(this);
        if (!currentKey.isEmpty()) {
            groqKeyInput.setText(currentKey);
        }

        findViewById(R.id.start).setOnClickListener(v -> startJarvis());
        findViewById(R.id.stop).setOnClickListener(v -> {
            stopService(new Intent(this, JarvisAssistantService.class));
            status.setText("STANDBY • JARVIS OFFLINE");
        });

        findViewById(R.id.saveAiKey).setOnClickListener(v -> {
            String key = groqKeyInput.getText().toString().trim();
            Prefs.setGroqKey(this, key);
            status.setText("TESTING CONVERSATIONAL AI…");
            new Thread(() -> {
                String reply = AiEngine.chat(this, "Hello JARVIS, state your status.");
                runOnUiThread(() -> status.setText("AI RESPONSE: " + reply));
            }).start();
        });

        findViewById(R.id.notifications).setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        findViewById(R.id.accessibility).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.overlay).setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            else status.setText("FLOATING JARVIS ENABLED");
        });
        findViewById(R.id.screen).setOnClickListener(v -> requestScreen());
        findViewById(R.id.camera).setOnClickListener(v -> startCamera());
        findViewById(R.id.pcLink).setOnClickListener(v -> status.setText("PC BRIDGE OPTIONAL • PHONE JARVIS RUNS INDEPENDENTLY"));

        status.setText("READY • PHONE-FIRST MODE");
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 90);
    }

    private void startJarvis() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 91);
            return;
        }
        startForegroundService(new Intent(this, JarvisAssistantService.class));
        status.setText("JARVIS ONLINE • VOICE ACTIVE");
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == 91 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) startJarvis();
    }

    void requestScreen() {
        MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), 42);
    }

    void startCamera() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 77);
            return;
        }
        startForegroundService(new Intent(this, CameraService.class));
        status.setText("CAMERA VISION ACTIVE");
    }

    @Override protected void onActivityResult(int r, int code, Intent data) {
        super.onActivityResult(r, code, data);
        if (r == 42 && code == RESULT_OK && data != null) {
            Intent i = new Intent(this, ScreenCaptureService.class);
            i.putExtra("resultCode", code);
            i.putExtra("data", data);
            startForegroundService(i);
            status.setText("SCREEN VISION ACTIVE");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, new IntentFilter("com.izrealyte.jarvismobile.STATUS"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, new IntentFilter("com.izrealyte.jarvismobile.STATUS"));
        }
    }

    @Override protected void onPause() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onPause();
    }
}
