package com.hyeona.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-bound token encryption. Never falls back to writing plaintext. */
final class TokenStore {
    private static final String ALIAS = "hyeona_notion_token_v1";
    private static final String VALUE = "token_ciphertext_v1";
    private static final String IV = "token_iv_v1";

    private static SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        if (!create) throw new IllegalStateException("Missing token key");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }

    static synchronized String read(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        try {
            String encrypted = prefs.getString(VALUE, "");
            if (!encrypted.isEmpty()) {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128,
                    Base64.decode(prefs.getString(IV, ""), Base64.NO_WRAP)));
                return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
            }
            String legacy = prefs.getString("token", "");
            if (!legacy.isEmpty()) save(context, legacy);
            return legacy;
        } catch (Exception error) {
            // Keep the old data intact. Reconnect explicitly rather than using plaintext.
            return "";
        }
    }

    static synchronized void save(Context context, String token) {
        if (token == null || token.isEmpty()) { clear(context); return; }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
            String encrypted = Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
            if (!context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                .putString(VALUE, encrypted).putString(IV, iv).remove("token").commit()) {
                throw new IllegalStateException("Token persistence failed");
            }
        } catch (Exception error) {
            throw new IllegalStateException("토큰을 안전하게 저장하지 못했어요. 다시 시도해 주세요.");
        }
    }

    static synchronized void clear(Context context) {
        if (!context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
            .remove(VALUE).remove(IV).remove("token").commit()) {
            throw new IllegalStateException("토큰을 지우지 못했어요. 다시 시도해 주세요.");
        }
    }
}
