package com.example.bizbot;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.Observer;

import java.net.URLEncoder;
import java.util.Locale;

public class OrderDetailsActivity extends BaseActivity {

    private EditText etReply;
    private TextView tvMessage;
    private String phoneNumber;
    private String customerName;
    private int orderId;
    private SharedPreferences prefs;
    private Button btnLocationAction;
    private String currentCoords = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_details);

        // 1. Initialize SharedPreferences and Intent Data
        prefs = getSharedPreferences("BizBotDrafts", MODE_PRIVATE);
        orderId = getIntent().getIntExtra("id", -1);
        customerName = getIntent().getStringExtra("customer");
        String initialMessage = getIntent().getStringExtra("message");
        phoneNumber = getIntent().getStringExtra("phoneNumber");

        // 2. Initialize UI Views
        TextView tvCustomer = findViewById(R.id.tvDetailCustomer);
        tvMessage = findViewById(R.id.tvDetailMessage);
        TextView tvPhone = findViewById(R.id.tvDetailPhone);
        etReply = findViewById(R.id.etReply);
        Button btnReply = findViewById(R.id.btnReply);
        btnLocationAction = findViewById(R.id.btnLocationAction);

        // Enable clickable links in the message (e.g., Google Maps URLs)
        tvMessage.setAutoLinkMask(Linkify.WEB_URLS);
        tvMessage.setLinksClickable(true);

        // 3. Set basic UI text
        tvCustomer.setText(customerName);
        tvMessage.setText(initialMessage);

        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            tvPhone.setText("Phone: " + phoneNumber);
            tvPhone.setVisibility(View.VISIBLE);
        }

        // 4. Observe the Order for updates (New messages or Location data)
        if (orderId != -1) {
            AppDatabase.getInstance(this).orderDao().getOrderByIdLive(orderId).observe(this, order -> {
                if (order != null) {
                    // Update message (in case new messages were appended)
                    tvMessage.setText(order.message);
                    
                    // Update button state
                    updateLocationButton(order.locationData, order.message);
                    
                    // Mark as read if a new message arrived while viewing
                    if (!order.isRead) {
                        new Thread(() -> {
                            AppDatabase.getInstance(this).orderDao().markAsRead(orderId);
                        }).start();
                    }
                }
            });
        } else {
            String locationData = getIntent().getStringExtra("locationData");
            updateLocationButton(locationData, initialMessage);
        }

        btnLocationAction.setOnClickListener(v -> {
            if (currentCoords != null) {
                // Open native Map
                Intent mapIntent = new Intent(OrderDetailsActivity.this, MapActivity.class);
                mapIntent.putExtra("locationData", currentCoords);
                startActivity(mapIntent);
            } else {
                // Request Location
                sendLocationRequest();
            }
        });

        // 5. Logic for the Reply Button
        btnReply.setOnClickListener(v -> handleReply(etReply.getText().toString()));
    }

    private void updateLocationButton(String locationData, String message) {
        currentCoords = (locationData != null && !locationData.isEmpty()) ? locationData : extractCoordinates(message);

        if (currentCoords != null) {
            // Location found! Turn button Green and change text
            btnLocationAction.setText("📍 View Live Location");
            btnLocationAction.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50)); // Green
        } else {
            // No location. Keep it Blue for requesting
            btnLocationAction.setText("Request Location");
            btnLocationAction.setBackgroundTintList(ColorStateList.valueOf(0xFF2196F3)); // Blue
        }
    }

    private void sendLocationRequest() {
        String requestMsg = "Please share your live location so I can track your delivery.";
        handleReply(requestMsg);
        Toast.makeText(this, "Requesting location from customer...", Toast.LENGTH_SHORT).show();
    }

    private void handleReply(String replyText) {
        if (replyText.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        Notification.Action action = (NotificationService.getInstance() != null) ?
                NotificationService.getInstance().getReplyActionForCustomer(customerName) : null;

        if (action != null) {
            try {
                sendBackgroundReply(action, replyText);
                Toast.makeText(this, "Sent in background", Toast.LENGTH_SHORT).show();
                etReply.setText("");
                return;
            } catch (Exception e) {
                Log.e("BizBot", "Background reply failed", e);
            }
        }

        openWhatsApp(replyText);
        etReply.setText("");
    }

    private void sendBackgroundReply(Notification.Action action, String text) throws PendingIntent.CanceledException {
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        for (RemoteInput remoteInput : action.getRemoteInputs()) {
            bundle.putCharSequence(remoteInput.getResultKey(), text);
        }
        RemoteInput.addResultsToIntent(action.getRemoteInputs(), intent, bundle);
        action.actionIntent.send(this, 0, intent, null, null);
    }

    private String extractCoordinates(String text) {
        if (text == null || text.isEmpty()) return null;

        // 1. Try raw coordinates: -1.23, 36.78
        java.util.regex.Pattern coordPattern = java.util.regex.Pattern.compile("([-+]?\\d+\\.\\d+)\\s*,\\s*([-+]?\\d+\\.\\d+)");
        java.util.regex.Matcher matcher = coordPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1) + "," + matcher.group(2);
        }

        // 2. Try Google Maps URL patterns (q=, ll=, @, loc:)
        String[] patterns = {"q=([\\-\\d.]+),([\\-\\d.]+)", "ll=([\\-\\d.]+),([\\-\\d.]+)", "@([\\-\\d.]+),([\\-\\d.]+)", "loc:([\\-\\d.]+),([\\-\\d.]+)"};
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) return m.group(1) + "," + m.group(2);
        }
        return null;
    }

    private void openWhatsApp(String reply) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Toast.makeText(this, "Cannot send: No phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            WhatsAppHelperService.messageToPaste = reply;
            WhatsAppHelperService.shouldSend = true;
            String url = "https://api.whatsapp.com/send?phone=" + phoneNumber + "&text=" + URLEncoder.encode(reply, "UTF-8");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String draft = prefs.getString("draft_" + customerName, "");
        etReply.setText(draft);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (etReply != null) {
            prefs.edit().putString("draft_" + customerName, etReply.getText().toString()).apply();
        }
    }
}
