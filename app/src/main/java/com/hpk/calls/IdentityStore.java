package com.hpk.calls;

import android.content.Context;
import android.content.SharedPreferences;

public final class IdentityStore {
    private static final String PREFS = "hpk_native_identity";
    private static final String USER_ID = "user_id";
    private static final String TOKEN = "token";
    private static final String NAME = "display_name";

    private IdentityStore() {}

    public static void save(Context context, String userId, String token, String displayName) {
        if (userId == null || token == null || userId.trim().isEmpty() || token.trim().isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(USER_ID, userId)
                .putString(TOKEN, token)
                .putString(NAME, displayName == null || displayName.trim().isEmpty() ? "HPK User" : displayName)
                .apply();
    }

    public static Identity load(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Identity(
                p.getString(USER_ID, ""),
                p.getString(TOKEN, ""),
                p.getString(NAME, "HPK User")
        );
    }

    public static final class Identity {
        public final String userId;
        public final String token;
        public final String displayName;

        Identity(String userId, String token, String displayName) {
            this.userId = userId == null ? "" : userId;
            this.token = token == null ? "" : token;
            this.displayName = displayName == null ? "HPK User" : displayName;
        }

        public boolean isValid() {
            return userId.matches("HPK-U[A-Z0-9]{6,10}") && token.length() >= 20;
        }
    }
}
