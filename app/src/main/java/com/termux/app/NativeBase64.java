package com.termux.app;

import com.google.common.io.BaseEncoding;

/** API-24-safe Base64 used by protocol payloads that are also exercised by local JVM tests. */
final class NativeBase64 {
    private static final BaseEncoding STANDARD = BaseEncoding.base64();

    private NativeBase64() {}

    static String encode(byte[] value) {
        if (value == null || value.length == 0) return "";
        return STANDARD.encode(value);
    }

    static byte[] decode(String value) {
        if (value == null || value.isEmpty()) return new byte[0];
        String normalized = value.replaceAll("\\s", "");
        int remainder = normalized.length() % 4;
        if (remainder != 0) {
            StringBuilder padded = new StringBuilder(normalized);
            for (int index = remainder; index < 4; index++) padded.append('=');
            normalized = padded.toString();
        }
        return STANDARD.decode(normalized);
    }
}
