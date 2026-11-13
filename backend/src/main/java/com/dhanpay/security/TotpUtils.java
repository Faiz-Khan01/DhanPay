package com.dhanpay.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard RFC 6238 Time-Based One-Time Password (TOTP) utility
 * compatible with Google Authenticator, Authy, and Microsoft Authenticator.
 */
public final class TotpUtils {

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TIME_STEP_SECONDS = 30;

    private TotpUtils() {
    }

    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public static List<String> generateBackupCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int part1 = 1000 + RANDOM.nextInt(9000);
            int part2 = 1000 + RANDOM.nextInt(9000);
            codes.add(part1 + "-" + part2);
        }
        return codes;
    }

    public static String generateCode(String base32Secret, long timeSeconds) {
        try {
            byte[] key = decodeBase32(base32Secret);
            long counter = timeSeconds / TIME_STEP_SECONDS;
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "RAW"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate TOTP code", ex);
        }
    }

    public static boolean verifyCode(String base32Secret, String code) {
        return verifyCode(base32Secret, code, System.currentTimeMillis() / 1000L);
    }

    public static boolean verifyCode(String base32Secret, String code, long timeSeconds) {
        if (base32Secret == null || code == null) {
            return false;
        }
        String cleanCode = code.trim().replaceAll("\\s+", "");
        if (!cleanCode.matches("^\\d{6}$")) {
            return false;
        }

        for (int delta = -1; delta <= 1; delta++) {
            String candidate = generateCode(base32Secret, timeSeconds + (delta * TIME_STEP_SECONDS));
            if (candidate.equals(cleanCode)) {
                return true;
            }
        }
        return false;
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            buffer <<= (5 - bitsLeft);
            sb.append(BASE32_CHARS.charAt(buffer & 0x1F));
        }
        return sb.toString();
    }

    public static byte[] decodeBase32(String base32) {
        String clean = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        int numBytes = clean.length() * 5 / 8;
        byte[] result = new byte[numBytes];
        int buffer = 0;
        int bitsLeft = 0;
        int outIdx = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                if (outIdx < numBytes) {
                    result[outIdx++] = (byte) ((buffer >> bitsLeft) & 0xFF);
                }
            }
        }
        return result;
    }
}
