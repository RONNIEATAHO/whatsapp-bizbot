package com.example.bizbot;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class NetworkReceiver extends BroadcastReceiver {
    private static boolean lastState = true; // Store last state to avoid spamming
    private static final String CHANNEL_ID = "NetworkAlerts";

    public interface NetworkStateListener {
        void onNetworkStateChanged(boolean isConnected);
    }

    private NetworkStateListener listener;

    public NetworkReceiver() {}

    public NetworkReceiver(NetworkStateListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        
        if (isConnected != lastState) {
            lastState = isConnected;
            showSystemNotification(context, isConnected);
        }

        if (listener != null) {
            listener.onNetworkStateChanged(isConnected);
        }
    }

    private void showSystemNotification(Context context, boolean isConnected) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Network Status", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        String title = isConnected ? "Internet Restored" : "Internet Lost";
        String message = isConnected ? "BizBot is back online and ready." : "BizBot is offline. Orders cannot be captured.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        manager.notify(101, builder.build());
    }
}
