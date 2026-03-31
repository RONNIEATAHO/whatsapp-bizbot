package com.example.bizbot;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Lifecycle Controller: Implements the OSP principle of releasing resources 
 * when the app is not in use.
 */
public abstract class BaseActivity extends AppCompatActivity {

    // 5 minutes of inactivity before auto-closing (300,000 milliseconds)
    private static final long INACTIVITY_TIMEOUT = 5 * 60 * 1000;
    private final Handler inactivityHandler = new Handler(Looper.getMainLooper());
    private final Runnable inactivityCallback = () -> {
        Toast.makeText(this, "BizBot closed due to inactivity to save resources.", Toast.LENGTH_LONG).show();
        finishAffinity(); // Forces the app's UI to stop and release resources
    };

    @Override
    protected void onResume() {
        super.onResume();
        resetInactivityTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopInactivityTimer();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        resetInactivityTimer(); // Reset timer whenever the user touches the screen
    }

    private void resetInactivityTimer() {
        stopInactivityTimer();
        inactivityHandler.postDelayed(inactivityCallback, INACTIVITY_TIMEOUT);
    }

    private void stopInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityCallback);
    }
}
