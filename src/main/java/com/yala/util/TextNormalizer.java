package com.yala.util;

import java.text.Normalizer;

/**
 * Normalizes free text for tolerant comparison: strips accents/diacritics, lowercases,
 * trims and collapses internal whitespace. Used to compare the names entered at
 * registration against the official RENIEC names (which differ in case/accents).
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String stripped = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{Mn}+", "");
        return stripped.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /** True when both strings are equal after normalization. */
    public static boolean equalsNormalized(String a, String b) {
        return normalize(a).equals(normalize(b));
    }
}
