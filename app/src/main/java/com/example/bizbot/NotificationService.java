package com.example.bizbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

import java.util.List;

public class NotificationService extends NotificationListenerService {

    private static NotificationService instance;
    private static final String CHANNEL_ID = "BizBotOrders";

    public static NotificationService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "New Orders", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts for newly captured orders");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void sendOrderAlert(String name, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("New Order from " + name)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (pkg.equals("com.whatsapp") || pkg.equals("com.whatsapp.w4b")) {
                Notification notification = sbn.getNotification();
                Bundle extras = notification.extras;
                
                CharSequence titleCS = extras.getCharSequence(Notification.EXTRA_TITLE);
                String name = titleCS != null ? titleCS.toString() : "Unknown";

                CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                
                if (lines != null && lines.length > 0) {
                    for (CharSequence line : lines) {
                        processMessage(line.toString(), name, sbn);
                    }
                } else {
                    CharSequence textCS = extras.getCharSequence(Notification.EXTRA_TEXT);
                    if (textCS != null) {
                        processMessage(textCS.toString(), name, sbn);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("BizBot", "Error", e);
        }
    }

    private void processMessage(String text, String name, StatusBarNotification sbn) {
        if (isOrder(text)) {
            String phoneNumber = extractPhoneNumber(sbn);
            if (phoneNumber == null && isLikelyPhoneNumber(name)) {
                phoneNumber = name.replaceAll("[^0-9]", "");
            }

            String uniqueKey = sbn.getKey() + "_" + text.hashCode();
            
            OrderEntity newOrder = new OrderEntity(name, text, System.currentTimeMillis(), uniqueKey, phoneNumber);
            AppDatabase.getInstance(this).orderDao().insert(newOrder);
            
            sendOrderAlert(name, text);
        }
    }

    private boolean isOrder(String text) {
        String t = text.toLowerCase();
        return t.contains("order") || t.contains("price") || t.contains("buy") || 
               t.contains("cost") || t.contains("how much") || t.contains("payment");
    }

    private boolean isLikelyPhoneNumber(String str) {
        return str != null && str.replaceAll("[^0-9]", "").length() >= 7;
    }

    private String extractPhoneNumber(StatusBarNotification sbn) {
        try {
            String tag = sbn.getTag();
            if (tag != null && tag.contains("@s.whatsapp.net")) {
                return tag.split("@")[0].replaceAll("[^0-9]", "");
            }
            Bundle extras = sbn.getNotification().extras;
            String convId = extras.getString("android.conversationId");
            if (convId != null && convId.contains("@")) {
                return convId.split("@")[0].replaceAll("[^0-9]", "");
            }
            NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.getNotification());
            if (style != null) {
                for (NotificationCompat.MessagingStyle.Message msg : style.getMessages()) {
                    if (msg.getPerson() != null && msg.getPerson().getUri() != null) {
                        String uri = msg.getPerson().getUri();
                        if (uri.startsWith("tel:")) return uri.substring(4).replaceAll("[^0-9]", "");
                    }
                }
            }
        } catch (Exception e) {
            Log.e("BizBot", "Extraction error", e);
        }
        return null;
    }

    public Notification.Action getReplyActionForCustomer(String customerName) {
        StatusBarNotification[] active = getActiveNotifications();
        if (active == null) return null;
        for (StatusBarNotification sbn : active) {
            if (!sbn.getPackageName().contains("com.whatsapp")) continue;
            CharSequence title = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title != null && title.toString().toLowerCase().contains(customerName.toLowerCase())) {
                return findReplyAction(sbn.getNotification());
            }
        }
        return null;
    }

    private Notification.Action findReplyAction(Notification n) {
        if (n.actions == null) return null;
        for (Notification.Action action : n.actions) {
            if (action.getRemoteInputs() != null) {
                for (RemoteInput ri : action.getRemoteInputs()) {
                    if (ri.getAllowFreeFormInput()) return action;
                }
            }
        }
        return null;
    }
}
