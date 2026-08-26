# V2.0.0 Architecture

## Foreground / active call

Native Android `MainActivity` hosts the proven HTTPS HPK Calls interface in a hardened WebView. WebRTC remains inside Chromium/WebView for this beta, while Android grants camera/microphone permissions and places the audio system in communication mode.

## Background direct-call readiness

`CallMonitorService` runs as an Android foreground `dataSync` service after the Web app creates an HPK User ID. It keeps the HPK identity alive using heartbeat requests and checks the direct-call inbox approximately every 2.2 seconds.

## Incoming call

When the server inbox contains a room, Android marks the room ringing and creates a high-priority `CATEGORY_CALL` notification with native ringtone, vibration, full-screen intent, and Answer/Decline actions.

## Answer

Answer opens `MainActivity` using the secure room ID and server URL. The WebRTC room is loaded, Android permissions are bridged to WebView, and the app attempts to execute the existing HPK answer flow automatically.

## Identity continuity

The browser identity is synchronized into encrypted-by-app-private Android preferences. If the Render process restarts and loses its in-memory user map, the foreground monitor re-registers the same HPK User ID and stores the newly issued token. The next WebView session synchronizes that token back into the HPK web identity storage.

## Production roadmap

V2.1+: persistent release signing, native settings screen, battery-optimization guidance, improved Bluetooth routing.

V2.2+: FCM push notification path for guaranteed incoming-call delivery without continuous polling.

V2.5+: native libWebRTC media engine, reducing dependence on WebView for the call media layer.
