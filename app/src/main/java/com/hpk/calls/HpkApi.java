package com.hpk.calls;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class HpkApi {
    private HpkApi() {}

    public static final class ApiException extends Exception {
        public final int statusCode;
        public final String responseBody;
        ApiException(int statusCode, String responseBody) {
            super("HPK API HTTP " + statusCode);
            this.statusCode = statusCode;
            this.responseBody = responseBody == null ? "" : responseBody;
        }
    }

    public static JSONObject heartbeat(String userId, String token, String displayName) throws Exception {
        JSONObject body = new JSONObject().put("displayName", displayName == null ? "HPK User" : displayName);
        return request("POST", "/api/users/" + Uri.encode(userId) + "/heartbeat?token=" + Uri.encode(token), body);
    }

    public static JSONObject inbox(String userId, String token) throws Exception {
        return request("GET", "/api/users/" + Uri.encode(userId) + "/inbox?token=" + Uri.encode(token), null);
    }

    public static JSONObject register(String userId, String displayName) throws Exception {
        JSONObject body = new JSONObject().put("displayName", displayName == null ? "HPK User" : displayName);
        if (userId != null && !userId.trim().isEmpty()) body.put("userId", userId);
        return request("POST", "/api/users/register", body);
    }

    public static void ringing(String roomId) {
        runQuiet(() -> request("POST", "/api/rooms/" + Uri.encode(roomId) + "/ringing", new JSONObject()));
    }

    public static void decline(String roomId, String reason) {
        runQuiet(() -> request("POST", "/api/rooms/" + Uri.encode(roomId) + "/decline",
                new JSONObject().put("reason", reason == null ? "declined" : reason)));
    }

    private static JSONObject request(String method, String path, JSONObject body) throws Exception {
        URL url = new URL(BuildConfig.HPK_BASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("User-Agent", "HPKCallsAndroid/" + BuildConfig.VERSION_NAME);
        c.setUseCaches(false);
        if (body != null && !"GET".equals(method)) {
            c.setDoOutput(true);
            byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            try (OutputStream out = c.getOutputStream()) { out.write(b); }
        }
        int status = c.getResponseCode();
        InputStream in = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
        String text = readAll(in);
        c.disconnect();
        if (status < 200 || status >= 300) throw new ApiException(status, text);
        return text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private interface ThrowingCall { void run() throws Exception; }
    private static void runQuiet(ThrowingCall call) {
        new Thread(() -> { try { call.run(); } catch (Exception ignored) {} }, "hpk-api-action").start();
    }

    public static IncomingCall firstIncoming(JSONObject inbox) {
        if (inbox == null) return null;
        JSONArray calls = inbox.optJSONArray("calls");
        if (calls == null || calls.length() == 0) return null;
        JSONObject c = calls.optJSONObject(0);
        if (c == null) return null;
        JSONObject meta = c.optJSONObject("meta");
        return new IncomingCall(
                c.optString("roomId", ""),
                meta == null ? "HPK Caller" : meta.optString("fromName", "HPK Caller"),
                meta == null ? "" : meta.optString("fromUserId", ""),
                meta == null ? "audio" : meta.optString("mode", "audio")
        );
    }

    public static final class IncomingCall {
        public final String roomId;
        public final String callerName;
        public final String callerUserId;
        public final String mode;

        IncomingCall(String roomId, String callerName, String callerUserId, String mode) {
            this.roomId = roomId == null ? "" : roomId;
            this.callerName = callerName == null || callerName.trim().isEmpty() ? "HPK Caller" : callerName;
            this.callerUserId = callerUserId == null ? "" : callerUserId;
            this.mode = "video".equals(mode) ? "video" : "audio";
        }

        public boolean isValid() { return roomId.matches("HPK-[A-Z0-9]{4,10}"); }
    }
}
