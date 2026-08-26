# HPK Calls Android V2.0.0 — Native Android Beta

This is the first native Android edition of HPK Calls. It keeps the proven HPK Calls V1.14.0 WebRTC media/call engine, but moves important phone-level behavior into Android itself.

## Native Android features

- Android application package: `com.hpk.calls` (`com.hpk.calls.debug` for the beta APK).
- Native incoming call notification using Android's CALL notification category.
- Native Android ringtone and vibration rather than relying only on browser media volume.
- Full-screen/lock-screen incoming call presentation where Android permits full-screen call notifications.
- Native Answer and Decline actions.
- Foreground direct-call monitor so HPK User ID presence can remain active while the app is backgrounded.
- Native microphone/camera permission bridge for WebRTC.
- Native audio communication mode and speaker routing support.
- Native Android share sheet for Quick Connect links.
- Secure HTTPS-only WebView and server communication.
- Automatic re-registration of the same HPK User ID after a transient server-memory restart.

## Existing HPK features retained

- HPK User IDs.
- Direct HPK-to-HPK calling.
- Quick Connect fallback.
- Voice and video WebRTC.
- Answer / Decline / End Call.
- Caller ringback.
- Incoming ringtone settings inside the call UI.
- Call health, adaptive quality, contacts and recent calls from V1.14.0.

## Server

The beta is configured for:

`https://hpk-calls.onrender.com`

The deployed service must provide the V1.14.0-compatible `/api/users/*` and `/api/rooms/*` endpoints.

## Important beta limitation

This V2.0.0 build uses a native Android foreground monitor instead of Firebase Cloud Messaging. It can therefore improve background incoming-call behavior without adding a paid service, but Android can still stop it after **Force stop**, aggressive battery management, or a device reboot until HPK Calls is opened again. Guaranteed push delivery when the app is fully stopped is planned for the later FCM-enabled native release.

The WebRTC media layer is intentionally retained from the proven V1.14.0 engine for this first native beta. After the Android shell and incoming-call behavior are stable, the media layer can be migrated to native libWebRTC in a later V2.x release.
