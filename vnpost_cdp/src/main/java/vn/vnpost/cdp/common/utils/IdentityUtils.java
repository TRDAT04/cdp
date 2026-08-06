package vn.vnpost.cdp.common.utils;

import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Bộ chuẩn hoá định danh DUY NHẤT của hệ thống.
 *
 * <p>Mọi nơi ghi giá trị định danh vào {@code master_profiles} và mọi nơi truy vấn/so sánh chúng
 * đều phải đi qua đây. Nếu tồn tại hai bộ chuẩn hoá song song thì giá trị được LƯU sẽ khác giá trị
 * dùng để TRUY VẤN, và hai hồ sơ của cùng một người sẽ không bao giờ được ghép cặp để so — dù nếu
 * được ghép thì scorer lại kết luận là khớp.
 */
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

    /**
     * Dạng chuẩn của SĐT là dạng nội địa có số 0 đầu: {@code 0912345678}.
     * {@code +84 912 345 678}, {@code 84912345678}, {@code 0084912345678} đều về cùng dạng này.
     */
    public static String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        // Dạng gọi quốc tế viết 00 thay cho dấu '+'
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("84") && digits.length() > 9) {
            digits = "0" + digits.substring(2);
        }
        // Trả null thay vì chuỗi rỗng: chuỗi rỗng lọt vào DB sẽ khiến findByPhone("") khớp hàng loạt.
        return StringUtils.hasText(digits) ? digits : null;
    }

    /**
     * Dạng chuẩn của CCCD/CMND và MST: bỏ mọi khoảng trắng, in hoa.
     *
     * <p>KHÔNG bỏ dấu gạch ngang: với MST, {@code 0101234567-001} là chi nhánh còn
     * {@code 0101234567} là trụ sở — hai pháp nhân khác nhau, gộp lại là sai.
     */
    public static String normalizeIdentityNo(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", "").toUpperCase();
        return StringUtils.hasText(cleaned) ? cleaned : null;
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
