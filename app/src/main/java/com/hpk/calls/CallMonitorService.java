package com.hpk.calls;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.media.RingtoneManager;

import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CallMonitorService extends Service {
    public static final String CHANNEL_READY = "hpk_direct_ready";
    public static final String CHANNEL_INCOMING = "hpk_incoming_calls";
    public static final int READY_ID = 11420;
    private static final String SERVICE_PREFS = "hpk_native_service";
    private static final String LAST_ROOM = "last_incoming_room";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile long lastHeartbeatAt = 0L;
    private volatile String activeRoom = "";
    private volatile long activeRoomSince = 0L;

    public static void start(Context context) {
        Intent i = new Intent(context, CallMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i); else context.startService(i);
    }

    public static int notificationId(String roomId) {
        return 22000 + Math.abs((roomId == null ? "" : roomId).hashCode() % 20000);
    }

    public static String lastIncomingRoom(Context context) {
        return context.getSharedPreferences(SERVICE_PREFS, MODE_PRIVATE).getString(LAST_ROOM, "");
    }

    public static void cancelLastIncoming(Context context) {
        String room = lastIncomingRoom(context);
        if (!room.trim().isEmpty()) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(notificationId(room));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(READY_ID, readyNotification());
        executor.scheduleWithFixedDelay(this::pollSafely, 400, 2200, TimeUnit.MILLISECONDS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void pollSafely() {
        try {
            IdentityStore.Identity identity = IdentityStore.load(this);
            if (!identity.isValid()) return;
            long now = System.currentTimeMillis();
            if (now - lastHeartbeatAt > 20_000L) {
                try {
                    HpkApi.heartbeat(identity.userId, identity.token, identity.displayName);
                } catch (HpkApi.ApiException e) {
                    if (e.statusCode == 404) {
                        JSONObject r = HpkApi.register(identity.userId, identity.displayName);
                        String newToken = r.optString("token", "");
                        if (!newToken.trim().isEmpty()) IdentityStore.save(this, identity.userId, newToken, identity.displayName);
                    } else throw e;
                }
                lastHeartbeatAt = now;
                identity = IdentityStore.load(this);
            }

            JSONObject inbox = HpkApi.inbox(identity.userId, identity.token);
            HpkApi.IncomingCall call = HpkApi.firstIncoming(inbox);
            if (call != null && call.isValid()) {
                if (!call.roomId.equals(activeRoom)) {
                    cancelActiveNotification();
                    activeRoom = call.roomId;
                    activeRoomSince = now;
                    getSharedPreferences(SERVICE_PREFS, MODE_PRIVATE).edit().putString(LAST_ROOM, activeRoom).apply();
                    HpkApi.ringing(activeRoom);
                    showIncoming(call);
                } else if (now - activeRoomSince >= 45_000L) {
                    HpkApi.decline(activeRoom, "missed");
                    cancelActiveNotification();
                    activeRoom = "";
                    activeRoomSince = 0L;
                }
            } else if (!activeRoom.trim().isEmpty()) {
                cancelActiveNotification();
                activeRoom = "";
                activeRoomSince = 0L;
            }
        } catch (Exception ignored) {
            // The next poll retries automatically. A transient network error must not kill background call readiness.
        }
    }

    private void cancelActiveNotification() {
        if (activeRoom == null || activeRoom.trim().isEmpty()) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(notificationId(activeRoom));
    }

    private Notification readyNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        return new Notification.Builder(this, CHANNEL_READY)
                .setSmallIcon(R.drawable.ic_call_notification)
                .setContentTitle(getString(R.string.native_ready))
                .setContentText(getString(R.string.native_ready_detail))
                .setContentIntent(pi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void showIncoming(HpkApi.IncomingCall call) {
        Intent full = new Intent(this, IncomingCallActivity.class)
                .putExtra("roomId", call.roomId)
                .putExtra("callerName", call.callerName)
                .putExtra("callerUserId", call.callerUserId)
                .putExtra("mode", call.mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullPi = PendingIntent.getActivity(this, notificationId(call.roomId) + 1, full,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Intent answer = new Intent(this, CallActionReceiver.class)
                .setAction(CallActionReceiver.ACTION_ANSWER)
                .putExtra("roomId", call.roomId)
                .putExtra("callerName", call.callerName)
                .putExtra("callerUserId", call.callerUserId)
                .putExtra("mode", call.mode);
        PendingIntent answerPi = PendingIntent.getBroadcast(this, notificationId(call.roomId) + 2, answer,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Intent decline = new Intent(this, CallActionReceiver.class)
                .setAction(CallActionReceiver.ACTION_DECLINE)
                .putExtra("roomId", call.roomId)
                .putExtra("callerName", call.callerName)
                .putExtra("callerUserId", call.callerUserId)
                .putExtra("mode", call.mode);
        PendingIntent declinePi = PendingIntent.getBroadcast(this, notificationId(call.roomId) + 3, decline,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Bitmap large = BitmapFactory.decodeResource(getResources(), R.drawable.hpk_logo);
        String meta = ("video".equals(call.mode) ? "Video" : "Voice") + " call" +
                (call.callerUserId.trim().isEmpty() ? "" : " • " + call.callerUserId);

        Notification n = new Notification.Builder(this, CHANNEL_INCOMING)
                .setSmallIcon(R.drawable.ic_call_notification)
                .setLargeIcon(large)
                .setContentTitle(call.callerName)
                .setContentText(meta)
                .setSubText("HPK Calls")
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(Notification.PRIORITY_MAX)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .addAction(new Notification.Action.Builder(R.drawable.ic_call_notification, "Decline", declinePi).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_call_notification, "Answer", answerPi).build())
                .setTimeoutAfter(45_000L)
                .build();
        n.flags |= Notification.FLAG_INSISTENT | Notification.FLAG_ONGOING;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(notificationId(call.roomId), n);
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || Build.VERSION.SDK_INT < 26) return;

        NotificationChannel ready = new NotificationChannel(CHANNEL_READY, "HPK Direct Call Readiness", NotificationManager.IMPORTANCE_LOW);
        ready.setDescription("Keeps HPK direct calling ready while Android allows the service to run.");
        ready.setShowBadge(false);
        ready.setSound(null, null);
        nm.createNotificationChannel(ready);

        NotificationChannel incoming = new NotificationChannel(CHANNEL_INCOMING, "Incoming HPK Calls", NotificationManager.IMPORTANCE_HIGH);
        incoming.setDescription("Ringtone and vibration for incoming HPK Calls.");
        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        incoming.setSound(ringtone, attrs);
        incoming.enableVibration(true);
        incoming.setVibrationPattern(new long[]{0, 700, 350, 700, 900, 700, 350, 700});
        incoming.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(incoming);
    }

    private int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }
}
