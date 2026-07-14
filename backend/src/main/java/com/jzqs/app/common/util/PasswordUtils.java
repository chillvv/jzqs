package com.jzqs.app.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PasswordUtils {

    private static final String HASH_PREFIX = "{sha256}";

    private PasswordUtils() {
    }

    public static String hash(String password, String salt) {
        String raw = password + ":" + salt;
        byte[] digest = sha256(raw.getBytes(StandardCharsets.UTF_8));
        return HASH_PREFIX + bytesToHex(digest);
    }

    public static boolean verify(String password, String salt, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (!storedHash.startsWith(HASH_PREFIX)) {
            return false;
        }
        String expected = hash(password, salt);
        return expected.equals(storedHash);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
