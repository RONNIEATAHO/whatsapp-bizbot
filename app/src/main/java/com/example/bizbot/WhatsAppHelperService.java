package com.example.bizbot;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class WhatsAppHelperService extends AccessibilityService {

    public static String messageToPaste = "";
    public static boolean shouldSend = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!shouldSend || messageToPaste == null || messageToPaste.isEmpty()) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // 1. Try to find the message input field
        List<AccessibilityNodeInfo> messageInput = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry");
        if (messageInput == null || messageInput.isEmpty()) {
            messageInput = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/entry");
        }

        if (messageInput != null && !messageInput.isEmpty()) {
            AccessibilityNodeInfo inputNode = messageInput.get(0);
            
            // If the input is currently empty, fill it.
            CharSequence currentText = inputNode.getText();
            if (currentText == null || !currentText.toString().equals(messageToPaste)) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, messageToPaste);
                inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                // After setting text, return. The UI update will trigger another event where we click send.
                return;
            }

            // 2. The text is already there, now find and click the send button
            List<AccessibilityNodeInfo> sendButtons = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send");
            if (sendButtons == null || sendButtons.isEmpty()) {
                sendButtons = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/send");
            }

            if (sendButtons != null && !sendButtons.isEmpty()) {
                AccessibilityNodeInfo sendButton = sendButtons.get(0);
                if (sendButton.isVisibleToUser() && sendButton.isEnabled()) {
                    sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    
                    // Reset state
                    messageToPaste = "";
                    shouldSend = false;
                    
                    // Optional: Small delay before going back
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    performGlobalAction(GLOBAL_ACTION_BACK);
                }
            }
        }
    }

    @Override
    public void onInterrupt() {}
}
