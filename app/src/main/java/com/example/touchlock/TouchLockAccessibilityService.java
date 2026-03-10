package com.example.touchlock;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;

public class TouchLockAccessibilityService extends AccessibilityService {
    public static TouchLockAccessibilityService instance;
    private WindowManager windowManager;
    private LinearLayout lockLayout;
    private String currentPassword = "0000";
    private boolean isLocked = false;

    @Override
    protected void onServiceConnected() { instance = this; }

    public void lock(String password) {
        if (isLocked) return;
        this.currentPassword = password;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        lockLayout = new LinearLayout(this);
        lockLayout.setOrientation(LinearLayout.VERTICAL);
        lockLayout.setGravity(Gravity.CENTER);
        lockLayout.setBackgroundColor(Color.parseColor("#FB000000"));

        EditText input = new EditText(this);
        input.setHint("Введите код доступа");
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE);
        input.setGravity(Gravity.CENTER);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        lockLayout.addView(input, new LinearLayout.LayoutParams(600, 150));

        Button btn = new Button(this);
        btn.setText("РАЗБЛОКИРОВАТЬ");
        btn.setOnClickListener(v -> {
            String entered = input.getText().toString();
            if (entered.equals(currentPassword)) {
                unlock();
            } else {
                // ОТПРАВЛЯЕМ ОЧЕТ В ТЕЛЕГРАМ
                if (TouchLockService.instance != null) {
                    TouchLockService.instance.sendTelegramMessage("⚠️ Попытка взлома! Введен неверный код: " + entered);
                }
                input.setText("");
                Toast.makeText(this, "НЕВЕРНЫЙ КОД", Toast.LENGTH_SHORT).show();
            }
        });
        lockLayout.addView(btn, new LinearLayout.LayoutParams(600, 150));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        windowManager.addView(lockLayout, params);
        isLocked = true;
    }

    public void unlock() {
        if (isLocked && lockLayout != null) {
            windowManager.removeView(lockLayout);
            lockLayout = null;
            isLocked = false;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}

