package com.hpk.calls;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CallActionReceiver extends BroadcastReceiver {
    public static final String ACTION_ANSWER = "com.hpk.calls.ACTION_ANSWER";
    public static final String ACTION_DECLINE = "com.hpk.calls.ACTION_DECLINE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String roomId = intent.getStringExtra("roomId");
        String callerName = intent.getStringExtra("callerName");
        String callerUserId = intent.getStringExtra("callerUserId");
        String mode = intent.getStringExtra("mode");
        if (roomId == null || roomId.trim().isEmpty()) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(CallMonitorService.notificationId(roomId));

        if (ACTION_DECLINE.equals(intent.getAction())) {
            final PendingResult pending = goAsync();
            new Thread(() -> {
                try { HpkApi.decline(roomId, "declined"); }
                finally { pending.finish(); }
            }, "hpk-decline").start();
            return;
        }

        if (ACTION_ANSWER.equals(intent.getAction())) {
            Intent open = MainActivity.answerIntent(context, roomId, callerName, callerUserId, mode, true);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(open);
        }
    }
}
