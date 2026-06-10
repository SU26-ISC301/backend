package com.su26isc301.backend.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Converter
public class CccdEncryptionConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String DEFAULT_SECRET = "MySecretCccdEncryptionKeyForShopVN"; // 34-character fallback

    private static SecretKeySpec secretKey;
    private static IvParameterSpec ivSpec;

    static {
        try {
            String keyStr = System.getenv("SECURITY_ENCRYPTION_KEY");
            if (keyStr == null || keyStr.isBlank()) {
                keyStr = DEFAULT_SECRET;
            }
            // Derive a 256-bit key from keyStr using SHA-256
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(keyStr.getBytes(StandardCharsets.UTF_8));
            secretKey = new SecretKeySpec(keyBytes, "AES");

            // Derive a 16-byte IV from keyBytes for CBC mode
            byte[] ivBytes = new byte[16];
            System.arraycopy(keyBytes, 0, ivBytes, 0, 16);
            ivSpec = new IvParameterSpec(ivBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error initializing encryption keys", e);
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting field", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(dbData));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Trả về dữ liệu gốc nếu đó là dữ liệu chưa mã hóa trước đây
            return dbData;
        }
    }
}
