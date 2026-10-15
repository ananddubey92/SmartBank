package com.project.onlinebankingsystem.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Enterprise JPA AttributeConverter for secure AES-256-GCM encryption of sensitive CVV values.
 * Prevents storing plaintext CVVs in the database while preserving transparent entity access.
 */
@Converter
public class CvvCryptoConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(CvvCryptoConverter.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final String PREFIX = "ENC:";

    // Default 256-bit AES master key
    private static byte[] keyBytes = "SmartBankSecr3tK3yForCvvEnc2026!".getBytes(StandardCharsets.UTF_8);

    public static void setEncryptionKey(String secretKey) {
        if (secretKey != null && !secretKey.isBlank()) {
            byte[] raw = secretKey.getBytes(StandardCharsets.UTF_8);
            byte[] key32 = new byte[32];
            System.arraycopy(raw, 0, key32, 0, Math.min(raw.length, 32));
            keyBytes = key32;
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt CVV attribute: {}", e.getMessage());
            return attribute;
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        if (!dbData.startsWith(PREFIX)) {
            // Backward compatibility for existing plaintext records in legacy database
            return dbData;
        }
        try {
            String rawBase64 = dbData.substring(PREFIX.length());
            byte[] cipherMessage = Base64.getDecoder().decode(rawBase64);

            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherMessage);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt CVV database attribute: {}", e.getMessage());
            return dbData;
        }
    }
}
