package com.example.bizbot;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.net.URLEncoder;

public class OrderDetailsActivity extends BaseActivity {

    private EditText etReply;
    private String phoneNumber;
    private String customerName;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_details);

        prefs = getSharedPreferences("BizBotDrafts", MODE_PRIVATE);
        customerName = getIntent().getStringExtra("customer");
        String message = getIntent().getStringExtra("message");
        phoneNumber = getIntent().getStringExtra("phoneNumber");

        TextView tvCustomer = findViewById(R.id.tvDetailCustomer);
        TextView tvMessage = findViewById(R.id.tvDetailMessage);
        TextView tvPhone = findViewById(R.id.tvDetailPhone);
        etReply = findViewById(R.id.etReply);
        Button btnReply = findViewById(R.id.btnReply);

        tvCustomer.setText(customerName);
        tvMessage.setText(message);

        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            tvPhone.setText("Phone: " + phoneNumber);
            tvPhone.setVisibility(android.view.View.VISIBLE);
        }

        btnReply.setOnClickListener(v -> handleReply(etReply.getText().toString()));
    }

    private void handleReply(String replyText) {
        if (replyText.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        Notification.Action action = NotificationService.getInstance() != null ? 
                NotificationService.getInstance().getReplyActionForCustomer(customerName) : null;

        if (action != null) {
            try {
                sendBackgroundReply(action, replyText);
                Toast.makeText(this, "Sent in background", Toast.LENGTH_SHORT).show();
                etReply.setText("");
                finish();
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
        action.actionIntent.send(this, 0, intent);
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
}
