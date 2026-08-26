package com.hpk.calls;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_MEDIA = 501;
    private static final int REQ_NOTIFICATIONS = 502;
    private static final String ACTION_NATIVE_ANSWER = "com.hpk.calls.OPEN_ANSWER";
    private static final String BASE_URL = BuildConfig.HPK_BASE_URL + "/";

    private WebView webView;
    private ProgressBar progress;
    private PermissionRequest pendingWebPermission;
    private String pendingRoom = "";
    private String pendingMode = "audio";
    private boolean pendingAutoAnswer = false;
    private int autoAnswerAttempts = 0;
    private AudioManager audioManager;
    private boolean nativeSpeakerOn = false;

    public static Intent answerIntent(Context context, String roomId, String callerName, String callerUserId, String mode, boolean autoAnswer) {
        return new Intent(context, MainActivity.class)
                .setAction(ACTION_NATIVE_ANSWER)
                .putExtra("roomId", roomId)
                .putExtra("callerName", callerName)
                .putExtra("callerUserId", callerUserId)
                .putExtra("mode", mode)
                .putExtra("autoAnswer", autoAnswer);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);
        configureWebView();
        requestNotificationPermissionIfNeeded();
        handleIntent(getIntent(), true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, false);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " HPKCallsAndroid/" + BuildConfig.VERSION_NAME);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        webView.addJavascriptInterface(new NativeBridge(), "HPKNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri != null && "https".equalsIgnoreCase(uri.getScheme()) && "hpk-calls.onrender.com".equalsIgnoreCase(uri.getHost())) {
                    return false;
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                pushNativeIdentityIntoWeb();
                injectNativeEnhancements();
                if (pendingAutoAnswer && !pendingRoom.trim().isEmpty()) scheduleAutoAnswer();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(MainActivity.this, "Secure connection could not be verified.", Toast.LENGTH_LONG).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }
        });
    }

    private void handleIntent(Intent intent, boolean firstLoad) {
        if (intent == null) {
            if (firstLoad) webView.loadUrl(BASE_URL + "?native=android-v2");
            return;
        }

        Uri deep = intent.getData();
        if (deep != null && "https".equalsIgnoreCase(deep.getScheme()) && "hpk-calls.onrender.com".equalsIgnoreCase(deep.getHost())) {
            webView.loadUrl(deep.toString());
            return;
        }

        if (ACTION_NATIVE_ANSWER.equals(intent.getAction())) {
            pendingRoom = safeRoom(intent.getStringExtra("roomId"));
            pendingMode = "video".equals(intent.getStringExtra("mode")) ? "video" : "audio";
            pendingAutoAnswer = intent.getBooleanExtra("autoAnswer", true);
            autoAnswerAttempts = 0;
            CallMonitorService.cancelLastIncoming(this);
            if (!pendingRoom.trim().isEmpty()) {
                String target = BASE_URL + "?native=android-v2#room=" + Uri.encode(pendingRoom) + "&signal=" + Uri.encode(BuildConfig.HPK_BASE_URL);
                webView.loadUrl(target);
                return;
            }
        }

        if (firstLoad) webView.loadUrl(BASE_URL + "?native=android-v2");
    }

    private String safeRoom(String room) {
        String v = room == null ? "" : room.trim().toUpperCase();
        return v.matches("HPK-[A-Z0-9]{4,10}") ? v : "";
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        List<String> androidPerms = new ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                androidPerms.add(Manifest.permission.RECORD_AUDIO);
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                androidPerms.add(Manifest.permission.CAMERA);
        }
        if (androidPerms.isEmpty()) {
            grantAllowedWebResources(request);
        } else {
            pendingWebPermission = request;
            requestPermissions(androidPerms.toArray(new String[0]), REQ_MEDIA);
        }
    }

    private void grantAllowedWebResources(PermissionRequest request) {
        List<String> allowed = new ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                allowed.add(r);
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                allowed.add(r);
        }
        if (allowed.isEmpty()) request.deny(); else request.grant(allowed.toArray(new String[0]));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA && pendingWebPermission != null) {
            grantAllowedWebResources(pendingWebPermission);
            pendingWebPermission = null;
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
    }

    private void pushNativeIdentityIntoWeb() {
        IdentityStore.Identity i = IdentityStore.load(this);
        if (!i.isValid()) return;
        try {
            JSONObject j = new JSONObject().put("userId", i.userId).put("token", i.token);
            String packed = JSONObject.quote(j.toString());
            String js = "try{var v=" + packed + ";localStorage.setItem('hpkCallsIdentityV114',v);for(var x=0;x<localStorage.length;x++){var k=localStorage.key(x);if(k&&k.indexOf('hpkCallsIdentity')===0)localStorage.setItem(k,v);}}catch(e){}";
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {}
    }

    private void injectNativeEnhancements() {
        String js = "(function(){try{" +
                "if(window.__hpkNativeV2)return;window.__hpkNativeV2=true;" +
                "function sync(){try{var ik='hpkCallsIdentityV114';for(var q=0;q<localStorage.length;q++){var kk=localStorage.key(q);if(kk&&kk.indexOf('hpkCallsIdentity')===0)ik=kk;}var i=JSON.parse(localStorage.getItem(ik)||'null');var n=(document.getElementById('myName')||{}).value||'HPK User';if(i&&i.userId&&i.token)HPKNative.syncIdentity(i.userId,i.token,n);HPKNative.callState((document.body&&document.body.dataset.callState)||'idle');}catch(e){}}" +
                "sync();setInterval(sync,7000);" +
                "if(document.body)new MutationObserver(sync).observe(document.body,{attributes:true,attributeFilter:['data-call-state']});" +
                "navigator.share=function(d){try{HPKNative.share((d&&d.title)||'',(d&&d.text)||'',(d&&d.url)||'');return Promise.resolve();}catch(e){return Promise.reject(e);}};navigator.canShare=function(){return true;};" +
                "var sp=document.getElementById('speakerBtn');if(sp&&!sp.dataset.nativeBound){sp.dataset.nativeBound='1';sp.addEventListener('click',function(){setTimeout(function(){HPKNative.toggleSpeaker();},0);});}" +
                "}catch(e){}})();";
        webView.evaluateJavascript(js, null);
    }

    private void scheduleAutoAnswer() {
        if (!pendingAutoAnswer || pendingRoom.trim().isEmpty()) return;
        if (autoAnswerAttempts++ > 24) {
            pendingAutoAnswer = false;
            Toast.makeText(this, "Incoming call opened. Tap Answer to continue.", Toast.LENGTH_LONG).show();
            return;
        }
        webView.postDelayed(() -> webView.evaluateJavascript(
                "(function(){var d=document.getElementById('incomingDialog'),b=document.getElementById('incomingAcceptBtn');if(d&&d.open&&b){b.click();return 'answered';}return 'waiting';})()",
                result -> {
                    if (result != null && result.contains("answered")) {
                        pendingAutoAnswer = false;
                        CallMonitorService.cancelLastIncoming(MainActivity.this);
                    } else scheduleAutoAnswer();
                }), 550);
    }

    private void onWebCallState(String state) {
        runOnUiThread(() -> {
            boolean active = "connecting".equals(state) || "connected".equals(state) || "ringing".equals(state);
            if (active) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (audioManager != null) audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_NORMAL);
                    if (Build.VERSION.SDK_INT < 31) audioManager.setSpeakerphoneOn(false);
                }
                nativeSpeakerOn = false;
            }
            if ("connecting".equals(state) || "connected".equals(state) || "idle".equals(state))
                CallMonitorService.cancelLastIncoming(this);
        });
    }

    private void toggleNativeSpeaker() {
        runOnUiThread(() -> {
            if (audioManager == null) return;
            nativeSpeakerOn = !nativeSpeakerOn;
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    if (nativeSpeakerOn) {
                        for (android.media.AudioDeviceInfo d : audioManager.getAvailableCommunicationDevices()) {
                            if (d.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                                audioManager.setCommunicationDevice(d);
                                break;
                            }
                        }
                    } else audioManager.clearCommunicationDevice();
                } else audioManager.setSpeakerphoneOn(nativeSpeakerOn);
            } catch (Exception ignored) {}
        });
    }

    private void showFullScreenIntentSettingsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                Toast.makeText(this, "For lock-screen incoming calls, allow full-screen notifications for HPK Calls in Android settings.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        showFullScreenIntentSettingsIfNeeded();
        IdentityStore.Identity i = IdentityStore.load(this);
        if (i.isValid()) CallMonitorService.start(this);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    private final class NativeBridge {
        @android.webkit.JavascriptInterface
        public void syncIdentity(String userId, String token, String displayName) {
            if (userId == null || token == null) return;
            String id = userId.trim().toUpperCase();
            if (!id.matches("HPK-U[A-Z0-9]{6,10}") || token.length() < 20) return;
            IdentityStore.save(MainActivity.this, id, token, displayName);
            CallMonitorService.start(MainActivity.this);
        }

        @android.webkit.JavascriptInterface
        public void share(String title, String text, String url) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                StringBuilder body = new StringBuilder();
                if (text != null && !text.trim().isEmpty()) body.append(text.trim());
                if (url != null && !url.trim().isEmpty()) {
                    if (body.length() > 0) body.append("\n");
                    body.append(url.trim());
                }
                send.putExtra(Intent.EXTRA_TEXT, body.toString());
                if (title != null && !title.trim().isEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, title);
                startActivity(Intent.createChooser(send, title == null || title.trim().isEmpty() ? "Share HPK Call" : title));
            });
        }

        @android.webkit.JavascriptInterface
        public void callState(String state) { onWebCallState(state == null ? "idle" : state); }

        @android.webkit.JavascriptInterface
        public void toggleSpeaker() { toggleNativeSpeaker(); }

        @android.webkit.JavascriptInterface
        public String nativeVersion() { return BuildConfig.VERSION_NAME; }
    }
}
