package vn.vnpost.cdp.common.utils;

import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class IdentityUtils {

    public static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public static String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    public static String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("84") && digits.length() > 9) {
            digits = "0" + digits.substring(2);
        }
        return digits.trim();
    }

    public static String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String normalized = Normalizer.normalize(name.trim().toLowerCase(), Normalizer.Form.NFD);
        normalized = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized).replaceAll("");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        normalized = normalized.replaceAll("[^a-z0-9 ]", "");
        return normalized;
    }

    public static double calculateNameSimilarity(String left, String right) {
        if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) return 0;
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) return 0;
        int distance = levenshteinDistance(left, right);
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) return 100.0;
        return (1.0 - (double) distance / maxLength) * 100.0;
    }

    private static int levenshteinDistance(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[la][lb];
    }
}
