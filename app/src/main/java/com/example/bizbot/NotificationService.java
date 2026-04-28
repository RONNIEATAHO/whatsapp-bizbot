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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationService extends NotificationListenerService {

    private static NotificationService instance;
    private static final String CHANNEL_ID = "BizBotOrders";
    private static final long SESSION_TIMEOUT = 1 * 60 * 60 * 1000; // 1 hour session

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
                .setContentTitle("New Order Update: " + name)
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
                String title = titleCS != null ? titleCS.toString() : "Unknown";

                // Filter out system-only notifications
                if (title.equals("WhatsApp") || title.equals("Checking for new messages")) {
                    // If it's a summary notification, we still want to look at the lines
                    if (extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) == null) return;
                }
                
                // Basic group chat filter
                if (title.toLowerCase().contains("group") || title.contains("@")) return;

                CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                
                if (lines != null && lines.length > 0) {
                    for (CharSequence line : lines) {
                        processMessage(line.toString(), title, sbn);
                    }
                } else {
                    CharSequence textCS = extras.getCharSequence(Notification.EXTRA_TEXT);
                    if (textCS != null) {
                        processMessage(textCS.toString(), title, sbn);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("BizBot", "Error processing notification", e);
        }
    }

    private void processMessage(String text, String name, StatusBarNotification sbn) {
        if (text == null || text.isEmpty()) return;

        String foundCoords = extractCoordinates(text);
        String currentPhone = extractPhoneNumber(sbn);
        if (currentPhone == null && isLikelyPhoneNumber(name)) {
            currentPhone = name.replaceAll("[^0-9]", "");
        }

        final String finalPhone = currentPhone;
        final String finalCoords = foundCoords;

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            OrderEntity latestOrder = db.orderDao().getLatestOrderByName(name);

            // 1. Session Logic: If there's a recent order from this customer
            if (latestOrder != null && (System.currentTimeMillis() - latestOrder.timestamp < SESSION_TIMEOUT)) {
                boolean updated = false;

                if (finalCoords != null) {
                    latestOrder.locationData = finalCoords;
                    String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + finalCoords;
                    latestOrder.message = latestOrder.message + "\n📍 Location: " + mapsUrl;
                    updated = true;
                } else if (!latestOrder.message.contains(text)) {
                    latestOrder.message = latestOrder.message + "\n- " + text;
                    updated = true;
                }

                if (updated) {
                    latestOrder.timestamp = System.currentTimeMillis();
                    // If the order was already read, mark it unread so the user sees the update
                    if (latestOrder.isRead) {
                        latestOrder.isRead = false;
                        sendOrderAlert(name, text);
                    }
                    db.orderDao().update(latestOrder);
                }
                return;
            } 

            // 2. New Order Logic
            if (isOrder(text) || finalCoords != null) {
                String uniqueKey = name + "_" + text.hashCode() + "_" + System.currentTimeMillis();
                String message = text;
                if (finalCoords != null) {
                    String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + finalCoords;
                    message = text + "\n📍 Location: " + mapsUrl;
                }
                OrderEntity newOrder = new OrderEntity(name, message, System.currentTimeMillis(), uniqueKey, finalPhone, false, finalCoords);
                db.orderDao().insert(newOrder);
                sendOrderAlert(name, text);
            }
        }).start();
    }

    private String extractCoordinates(String text) {
        if (text == null || text.isEmpty()) return null;
        Pattern coordPattern = Pattern.compile("([-+]?\\d+\\.\\d+)\\s*,\\s*([-+]?\\d+\\.\\d+)");
        Matcher matcher = coordPattern.matcher(text);
        if (matcher.find()) return matcher.group(1) + "," + matcher.group(2);

        String[] urlPatterns = {
            "q=([\\-\\d.]+),([\\-\\d.]+)", 
            "ll=([\\-\\d.]+),([\\-\\d.]+)", 
            "@([\\-\\d.]+),([\\-\\d.]+)", 
            "loc:([\\-\\d.]+),([\\-\\d.]+)"
        };
        for (String pattern : urlPatterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(1) + "," + m.group(2);
        }
        return null;
    }

    private boolean isOrder(String text) {
        String t = text.toLowerCase();
        
        // Immediate matches
        if (t.contains("order") || t.contains("buying") || t.contains("purchase") || 
            t.contains("ordered") || t.contains("delivered") || t.contains("price")) return true;

        // Intent markers
        boolean hasIntent = Pattern.compile("\\b(buy|want|need|get|bring|send|deliver|grab|reserve)\\b").matcher(t).find();
        
        // Business markers
        boolean hasContext = t.contains("how much") || t.contains("cost") || 
                           t.contains("pay") || t.contains("cash") || 
                           t.contains("momo") || t.contains("shs") || t.contains("ugx");

        // Quantity markers (e.g., "2 items", "1 pizza")
        boolean hasNumber = Pattern.compile("\\b\\d+\\b").matcher(t).find();
        
        if (t.length() < 3) return false;

        // An order is likely if it has intent + quantity OR business context
        return (hasIntent && hasNumber) || hasContext || (hasIntent && t.length() > 10);
    }

    private boolean isLikelyPhoneNumber(String str) {
        return str != null && str.replaceAll("[^0-9]", "").length() >= 7;
    }

    private String extractPhoneNumber(StatusBarNotification sbn) {
        try {
            String tag = sbn.getTag();
            if (tag != null && tag.contains("@s.whatsapp.net")) return tag.split("@")[0].replaceAll("[^0-9]", "");
            Bundle extras = sbn.getNotification().extras;
            String convId = extras.getString("android.conversationId");
            if (convId != null && convId.contains("@")) return convId.split("@")[0].replaceAll("[^0-9]", "");
        } catch (Exception ignored) {}
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
