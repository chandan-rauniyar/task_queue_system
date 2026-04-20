package com.taskqueue.service;

import com.taskqueue.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * AES-256 encryption for SMTP passwords stored in DB.
 *
 * encrypt() → called when admin saves an SMTP config
 * decrypt() → called when worker needs to send email
 *
 * The encryption key comes from application.yml: app.encryption.key
 * Must be exactly 32 characters.
 */
@Service
@RequiredArgsConstructor
public class EncryptionService {

    private static final String ALGORITHM = "AES";

    private final AppProperties appProperties;

    public String encrypt(String plainText) {
        try {
            SecretKeySpec key = buildKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            // uncommet if need debug
//            e.printStackTrace(); // IMPORTANT
//            throw new RuntimeException("Encryption failed: " + e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            SecretKeySpec key = buildKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            return new String(cipher.doFinal(decoded));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private SecretKeySpec buildKey() {
        String keyStr = appProperties.getEncryption().getKey();
        if (keyStr == null || keyStr.length() != 32) {
            throw new RuntimeException(
                    "Encryption key must be exactly 32 characters. Check app.encryption.key in application.yml"
            );
        }
        return new SecretKeySpec(keyStr.getBytes(), ALGORITHM);
    }
}