package com.hyeona.widget;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

final class TokenStoreInstrumentation {
    static void verify(Context base) {
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("token_store_security_test", mode);
            }
        };
        SharedPreferences prefs = isolated.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        try {
            prefs.edit().putString("token", "dummy-notion-token").putString("brainDraft", "메모").commit();
            if (!"dummy-notion-token".equals(TokenStore.read(isolated))) throw new AssertionError("Migration read failed");
            if (prefs.contains("token")) throw new AssertionError("Plaintext token remains");
            if (!prefs.contains("token_ciphertext_v1")) throw new AssertionError("Ciphertext missing");
            if (!"dummy-notion-token".equals(TokenStore.read(isolated))) throw new AssertionError("Decrypt failed");
            String firstIv = prefs.getString("token_iv_v1", "");
            TokenStore.save(isolated, "dummy-notion-token");
            if (firstIv.equals(prefs.getString("token_iv_v1", ""))) throw new AssertionError("IV reused");
            prefs.edit().putString("token_ciphertext_v1", "invalid").commit();
            if (!TokenStore.read(isolated).isEmpty()) throw new AssertionError("Corrupt token accepted");
            TokenStore.clear(isolated);
            if (prefs.contains("token_ciphertext_v1") || prefs.contains("token_iv_v1")) throw new AssertionError("Token not removed");
            if (!"메모".equals(prefs.getString("brainDraft", ""))) throw new AssertionError("Unrelated data lost");
        } finally {
            prefs.edit().clear().commit();
        }
    }
}
