package com.yala.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

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

    /**
     * True when {@code userValue} matches {@code reniecValue} after normalization, treating the
     * Unicode replacement char (U+FFFD '�') in the RENIEC value as a single-character wildcard.
     * JSON.pe corrupts Ñ/special chars into '�' at the source (the original letter is lost), so an
     * exact match would wrongly block real users (e.g. RENIEC "ZU�IGA" vs typed "ZUÑIGA"/"ZUNIGA").
     * Each '�' matches exactly one character, so the rest of the name must still match exactly.
     */
    public static boolean matchesReniec(String reniecValue, String userValue) {
        String r = normalize(reniecValue);
        String u = normalize(userValue);
        if (r.equals(u)) {
            return true;
        }
        if (r.indexOf('�') < 0) {
            return false;
        }
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < r.length(); i++) {
            char c = r.charAt(i);
            if (c == '�') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return u.matches(regex.toString());
    }
}
