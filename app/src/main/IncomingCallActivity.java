package com.hpk.calls;

import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class IncomingCallActivity extends android.app.Activity {
    private String roomId;
    private String callerName;
    private String callerUserId;
    private String mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_incoming_call);
        readIntent(getIntent());
        if (roomId == null || roomId.trim().isEmpty()) { finish(); return; }

        ((TextView) findViewById(R.id.callerName)).setText(callerName == null || callerName.trim().isEmpty() ? "HPK Caller" : callerName);
        ((TextView) findViewById(R.id.callMeta)).setText(("video".equals(mode) ? "Video" : "Voice") + " call");
        ((TextView) findViewById(R.id.callerId)).setText(callerUserId == null || callerUserId.trim().isEmpty() ? roomId : callerUserId);

        Button answer = findViewById(R.id.answerBtn);
        Button decline = findViewById(R.id.declineBtn);
        answer.setOnClickListener(v -> answer());
        decline.setOnClickListener(v -> decline());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntent(intent);
    }

    private void readIntent(Intent i) {
        roomId = i.getStringExtra("roomId");
        callerName = i.getStringExtra("callerName");
        callerUserId = i.getStringExtra("callerUserId");
        mode = i.getStringExtra("mode");
    }

    private void answer() {
        cancelNotification();
        startActivity(MainActivity.answerIntent(this, roomId, callerName, callerUserId, mode, true));
        finish();
    }

    private void decline() {
        cancelNotification();
        HpkApi.decline(roomId, "declined");
        finish();
    }

    private void cancelNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(CallMonitorService.notificationId(roomId));
    }
}
