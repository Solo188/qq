package com.example.touchlock;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityEvent;

public class TouchLockAccessibilityService extends AccessibilityService {
    public static TouchLockAccessibilityService instance;
    private WindowManager windowManager;
    private LinearLayout layout;
    private String currentPassword = "";
    private boolean isLocked = false;

    @Override
    protected void onServiceConnected() { instance = this; }

    public void lock(String password) {
        if (isLocked) return;
        this.currentPassword = password;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Создаем контейнер для интерфейса
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#EE000000")); // Темный фон

        // Поле ввода
        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Введите пароль");
        passwordInput.setHintTextColor(Color.GRAY);
        passwordInput.setTextColor(Color.WHITE);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(passwordInput, new LinearLayout.LayoutParams(600, 150));

        // Кнопка разблокировки
        Button unlockBtn = new Button(this);
        unlockBtn.setText("РАЗБЛОКИРОВАТЬ");
        unlockBtn.setBackgroundColor(Color.parseColor("#FF5722"));
        unlockBtn.setTextColor(Color.WHITE);
        unlockBtn.setOnClickListener(v -> {
            if (passwordInput.getText().toString().equals(currentPassword)) {
                unlock();
            } else {
                Toast.makeText(this, "Неверный код!", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(unlockBtn, new LinearLayout.LayoutParams(600, 150));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        windowManager.addView(layout, params);
        isLocked = true;
    }

    public void unlock() {
        if (isLocked && layout != null) {
            windowManager.removeView(layout);
            layout = null;
            isLocked = false;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}

