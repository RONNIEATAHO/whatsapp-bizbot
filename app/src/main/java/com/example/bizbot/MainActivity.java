package com.example.bizbot;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class MainActivity extends BaseActivity implements NetworkReceiver.NetworkStateListener, SensorEventListener {

    private OrderAdapter adapter;
    private TextView tvStatusWarning;
    private TextView tvNetworkWarning;
    private LinearLayout layoutLanding, layoutOrders;
    private TextView tvNoOrders;
    private NetworkReceiver networkReceiver;
    
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Button btnExport;
    private TextView tvTitle;
    
    private boolean isAuthenticated = false;
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private float acceleration = 0f;
    private float currentAcceleration = 0f;
    private float lastAcceleration = 0f;
    private static final int SHAKE_THRESHOLD = 10;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                checkServiceStatus();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatusWarning = findViewById(R.id.tvStatusWarning);
        tvNetworkWarning = findViewById(R.id.tvNetworkWarning);
        layoutLanding = findViewById(R.id.layoutLanding);
        layoutOrders = findViewById(R.id.layoutOrders);
        tvNoOrders = findViewById(R.id.tvNoOrders);
        tvTitle = findViewById(R.id.tvTitle);
        
        Button btnEnable = findViewById(R.id.btnEnable);
        btnEnable.setOnClickListener(v -> showPermissionPopup());
        tvStatusWarning.setOnClickListener(v -> showPermissionPopup());

        btnExport = findViewById(R.id.btnExport);
        btnExport.setVisibility(View.GONE);
        btnExport.setOnClickListener(v -> exportOrdersToCSV());

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        networkReceiver = new NetworkReceiver(this);
        
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            currentAcceleration = SensorManager.GRAVITY_EARTH;
            lastAcceleration = SensorManager.GRAVITY_EARTH;
        }

        setupBiometric();
        
        AppDatabase.getInstance(this).orderDao().getAllOrdersLive().observe(this, orders -> {
            if (isAuthenticated) {
                adapter = new OrderAdapter(orders);
                recyclerView.setAdapter(adapter);
                tvNoOrders.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        AppDatabase.getInstance(this).orderDao().getOrderCountLive().observe(this, count -> {
            if (isAuthenticated && tvTitle != null) {
                tvTitle.setText("Live Orders (" + count + ")");
            }
        });
    }

    private void setupBiometric() {
        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                isAuthenticated = true;
                layoutLanding.setVisibility(View.GONE);
                layoutOrders.setVisibility(View.VISIBLE);
                refreshOrders();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    finish();
                }
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("BizBot Security")
                .setSubtitle("Securely access your orders")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
    }

    private void authenticateUser() {
        if (!isAuthenticated) {
            biometricPrompt.authenticate(promptInfo);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isAuthenticated) return;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            lastAcceleration = currentAcceleration;
            currentAcceleration = (float) Math.sqrt((double) (x * x + y * y + z * z));
            float delta = currentAcceleration - lastAcceleration;
            acceleration = acceleration * 0.9f + delta;
            if (acceleration > SHAKE_THRESHOLD) {
                if (btnExport.getVisibility() != View.VISIBLE) {
                    showExportButtonSeamlessly();
                }
            }
        }
    }

    private void showExportButtonSeamlessly() {
        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setDuration(500);
        btnExport.setVisibility(View.VISIBLE);
        btnExport.startAnimation(fadeIn);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void exportOrdersToCSV() {
        List<OrderEntity> orders = AppDatabase.getInstance(this).orderDao().getAllOrders();
        if (orders.isEmpty()) return;
        StringBuilder csvData = new StringBuilder();
        csvData.append("ID,Customer,Message,Phone,Date\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        for (OrderEntity o : orders) {
            csvData.append(o.id).append(",")
                    .append("\"").append(o.customerName).append("\",")
                    .append("\"").append(o.message.replace("\n", " ")).append("\",")
                    .append(o.phoneNumber != null ? o.phoneNumber : "N/A").append(",")
                    .append(sdf.format(new Date(o.timestamp))).append("\n");
        }
        try {
            File cachePath = new File(getCacheDir(), "exports");
            cachePath.mkdirs();
            File csvFile = new File(cachePath, "BizBot_Orders.csv");
            FileOutputStream stream = new FileOutputStream(csvFile);
            stream.write(csvData.toString().getBytes());
            stream.close();
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", csvFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Orders CSV"));
            btnExport.setVisibility(View.GONE);
        } catch (Exception ignored) {}
    }

    private void showPermissionPopup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        boolean notificationEnabled = isNotificationServiceEnabled();
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        if (!notificationEnabled || !accessibilityEnabled) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Allow BizBot?")
                .setMessage("Permissions required to capture and send orders.")
                .setPositiveButton("Allow", (dialog, which) -> {
                    if (!notificationEnabled) {
                        startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                    } else {
                        openAccessibilitySettings();
                    }
                })
                .setNegativeButton("Deny", null)
                .show();
        }
    }

    private void openAccessibilitySettings() {
        ComponentName componentName = new ComponentName(getPackageName(), WhatsAppHelperService.class.getName());
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("extra_shortcut_type", 1);
        intent.putExtra("extra_component_name", componentName.flattenToString());
        try { startActivity(intent); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
    }

    @Override
    public void onNetworkStateChanged(boolean isConnected) {
        runOnUiThread(() -> {
            if (isConnected) {
                if (tvNetworkWarning.getVisibility() == View.VISIBLE) {
                    tvNetworkWarning.setBackgroundColor(Color.GREEN);
                    tvNetworkWarning.setText("Connection Restored");
                    tvNetworkWarning.postDelayed(() -> tvNetworkWarning.setVisibility(View.GONE), 3000);
                }
            } else {
                tvNetworkWarning.setVisibility(View.VISIBLE);
                tvNetworkWarning.setText("No Internet Connection.");
                tvNetworkWarning.setBackgroundColor(Color.RED);
            }
        });
    }

    private void refreshOrders() {
        List<OrderEntity> orders = AppDatabase.getInstance(this).orderDao().getAllOrders();
        adapter = new OrderAdapter(orders);
        RecyclerView rv = findViewById(R.id.recyclerView);
        if (rv != null) rv.setAdapter(adapter);
        tvNoOrders.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void checkServiceStatus() {
        boolean postNotificationGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotificationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        boolean notificationEnabled = isNotificationServiceEnabled();
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();

        if (notificationEnabled && accessibilityEnabled && postNotificationGranted) {
            if (!isAuthenticated) {
                layoutLanding.setVisibility(View.VISIBLE); // Keep landing visible until auth
                authenticateUser();
            } else {
                layoutLanding.setVisibility(View.GONE);
                layoutOrders.setVisibility(View.VISIBLE);
            }
        } else {
            layoutLanding.setVisibility(View.VISIBLE);
            layoutOrders.setVisibility(View.GONE);
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            for (String name : flat.split(":")) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) return true;
            }
        }
        return false;
    }

    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + WhatsAppHelperService.class.getCanonicalName();
        try { accessibilityEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED); } catch (Exception ignored) {}
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) return settingValue.contains(service);
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        checkServiceStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(networkReceiver); } catch (Exception ignored) {}
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }
}
