package vn.vnpost.cdp.profile.service.match;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchReasonCreateItem;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchScoreResult;
import vn.vnpost.cdp.profile.entity.MasterProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ProfileMatchScoreService {

    private static final int SCORE_IDENTITY_NO = 50;
    private static final int SCORE_PHONE        = 35;
    private static final int SCORE_EMAIL        = 30;
    private static final int SCORE_NAME_EXACT   = 25;
    private static final int SCORE_NAME_SIM_90  = 20;
    private static final int SCORE_NAME_SIM_85  = 15;
    private static final int SCORE_NAME_SIM_75  = 10;
    private static final int SCORE_DOB          = 20;
    private static final int SCORE_PROVINCE     = 10;
    private static final int SCORE_UNIT         = 5;
    private static final int MAX_SCORE          = 100;

    public ProfileMatchScoreResult calculate(MasterProfile left, MasterProfile right) {
        List<ProfileMatchReasonCreateItem> reasons = new ArrayList<>();
        int rawScore = 0;
        boolean identityConflict = false;

        String leftIdentityNo  = trim(left.getIdentityNo());
        String rightIdentityNo = trim(right.getIdentityNo());
        String leftPhone       = normalizePhone(left.getPhone());
        String rightPhone      = normalizePhone(right.getPhone());
        String leftEmail       = normalizeEmail(left.getEmail());
        String rightEmail      = normalizeEmail(right.getEmail());
        String leftName        = normalizeName(left.getFullName());
        String rightName       = normalizeName(right.getFullName());

        // 1. Identity number
        if (StringUtils.hasText(leftIdentityNo) && StringUtils.hasText(rightIdentityNo)) {
            if (leftIdentityNo.equals(rightIdentityNo)) {
                rawScore += SCORE_IDENTITY_NO;
                reasons.add(reason("IDENTITY_NO_MATCH", "Identity number matched",
                        left.getIdentityNo(), right.getIdentityNo(), SCORE_IDENTITY_NO));
            } else {
                identityConflict = true;
                reasons.add(reason("IDENTITY_CONFLICT", "Identity numbers differ",
                        left.getIdentityNo(), right.getIdentityNo(), 0));
            }
        }

        // 2. Phone
        if (StringUtils.hasText(leftPhone) && StringUtils.hasText(rightPhone)) {
            if (leftPhone.equals(rightPhone)) {
                rawScore += SCORE_PHONE;
                reasons.add(reason("PHONE_MATCH", "Phone number matched",
                        left.getPhone(), right.getPhone(), SCORE_PHONE));
            } else if (!StringUtils.hasText(leftIdentityNo) && !StringUtils.hasText(rightIdentityNo)
                    && !leftEmail.equals(rightEmail)) {
                identityConflict = true;
                reasons.add(reason("PHONE_CONFLICT", "Phone numbers differ",
                        left.getPhone(), right.getPhone(), 0));
            }
        }

        // 3. Email
        if (StringUtils.hasText(leftEmail) && StringUtils.hasText(rightEmail)) {
            if (leftEmail.equals(rightEmail)) {
                rawScore += SCORE_EMAIL;
                reasons.add(reason("EMAIL_MATCH", "Email matched",
                        left.getEmail(), right.getEmail(), SCORE_EMAIL));
            } else if (!StringUtils.hasText(leftPhone) && !StringUtils.hasText(rightPhone)) {
                identityConflict = true;
                reasons.add(reason("EMAIL_CONFLICT", "Emails differ",
                        left.getEmail(), right.getEmail(), 0));
            }
        }

        // 4 & 5. Full name
        if (StringUtils.hasText(leftName) && StringUtils.hasText(rightName)) {
            if (leftName.equals(rightName)) {
                rawScore += SCORE_NAME_EXACT;
                reasons.add(reason("NAME_EXACT_MATCH", "Full name matched",
                        left.getFullName(), right.getFullName(), SCORE_NAME_EXACT));
            } else {
                double similarity = calculateNameSimilarity(leftName, rightName);
                if (similarity >= 90) {
                    rawScore += SCORE_NAME_SIM_90;
                    reasons.add(reason("NAME_SIMILAR", "Name similarity >= 90%",
                            left.getFullName(), right.getFullName(), SCORE_NAME_SIM_90));
                } else if (similarity >= 85) {
                    rawScore += SCORE_NAME_SIM_85;
                    reasons.add(reason("NAME_SIMILAR", "Name similarity >= 85%",
                            left.getFullName(), right.getFullName(), SCORE_NAME_SIM_85));
                } else if (similarity >= 75) {
                    rawScore += SCORE_NAME_SIM_75;
                    reasons.add(reason("NAME_SIMILAR", "Name similarity >= 75%",
                            left.getFullName(), right.getFullName(), SCORE_NAME_SIM_75));
                }
            }
        }

        // 6. Date of birth
        if (left.getDateOfBirth() != null && right.getDateOfBirth() != null
                && left.getDateOfBirth().equals(right.getDateOfBirth())) {
            rawScore += SCORE_DOB;
            reasons.add(reason("DATE_OF_BIRTH_MATCH", "Date of birth matched",
                    left.getDateOfBirth().toString(), right.getDateOfBirth().toString(), SCORE_DOB));
        }

        // 7. Province
        String leftProvince  = trim(left.getProvinceCode());
        String rightProvince = trim(right.getProvinceCode());
        if (StringUtils.hasText(leftProvince) && StringUtils.hasText(rightProvince)
                && leftProvince.equals(rightProvince)) {
            rawScore += SCORE_PROVINCE;
            reasons.add(reason("PROVINCE_MATCH", "Province matched",
                    left.getProvinceCode(), right.getProvinceCode(), SCORE_PROVINCE));
        }

        // 8. Unit
        String leftUnit  = trim(left.getUnitCode());
        String rightUnit = trim(right.getUnitCode());
        if (StringUtils.hasText(leftUnit) && StringUtils.hasText(rightUnit)
                && leftUnit.equals(rightUnit)) {
            rawScore += SCORE_UNIT;
            reasons.add(reason("UNIT_MATCH", "Unit matched",
                    left.getUnitCode(), right.getUnitCode(), SCORE_UNIT));
        }

        int finalScore = Math.min(rawScore, MAX_SCORE);
        boolean autoMergeRecommended = finalScore >= 98 && !identityConflict;
        String matchLevel = resolveMatchLevel(finalScore);

        log.debug("ProfileMatchScoreService - left={}, right={}, score={}, level={}, conflict={}",
                left.getId(), right.getId(), finalScore, matchLevel, identityConflict);

        return ProfileMatchScoreResult.builder()
                .score(BigDecimal.valueOf(finalScore).setScale(2, RoundingMode.HALF_UP))
                .matchLevel(matchLevel)
                .autoMergeRecommended(autoMergeRecommended)
                .identityConflict(identityConflict)
                .reasons(reasons)
                .build();
    }

    public String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) return "";
        return phone.replaceAll("[\\s\\-.()+]", "").trim();
    }

    public String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) return "";
        return email.trim().toLowerCase();
    }

    public String normalizeName(String name) {
        if (!StringUtils.hasText(name)) return "";
        String normalized = Normalizer.normalize(name.trim().toLowerCase(), Normalizer.Form.NFD);
        normalized = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized).replaceAll("");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        normalized = normalized.replaceAll("[^a-z0-9 ]", "");
        return normalized;
    }

    public double calculateNameSimilarity(String left, String right) {
        if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) return 0;
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) return 0;
        int distance = levenshteinDistance(left, right);
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) return 100.0;
        return (1.0 - (double) distance / maxLength) * 100.0;
    }

    public boolean hasIdentityConflict(MasterProfile left, MasterProfile right) {
        String leftId  = trim(left.getIdentityNo());
        String rightId = trim(right.getIdentityNo());
        String leftPh  = normalizePhone(left.getPhone());
        String rightPh = normalizePhone(right.getPhone());
        String leftEm  = normalizeEmail(left.getEmail());
        String rightEm = normalizeEmail(right.getEmail());

        if (StringUtils.hasText(leftId) && StringUtils.hasText(rightId) && !leftId.equals(rightId)) {
            return true;
        }
        if (!StringUtils.hasText(leftId) && !StringUtils.hasText(rightId)
                && StringUtils.hasText(leftPh) && StringUtils.hasText(rightPh)
                && !leftPh.equals(rightPh)
                && StringUtils.hasText(leftEm) && StringUtils.hasText(rightEm)
                && !leftEm.equals(rightEm)) {
            return true;
        }
        if (!StringUtils.hasText(leftPh) && !StringUtils.hasText(rightPh)
                && StringUtils.hasText(leftEm) && StringUtils.hasText(rightEm)
                && !leftEm.equals(rightEm)) {
            return true;
        }
        return false;
    }

    private String resolveMatchLevel(int score) {
        if (score >= 95) return "VERY_HIGH";
        if (score >= 85) return "HIGH";
        if (score >= 70) return "MEDIUM";
        return "LOW";
    }

    private ProfileMatchReasonCreateItem reason(String type, String message,
                                                String leftVal, String rightVal, int score) {
        return ProfileMatchReasonCreateItem.builder()
                .reasonType(type)
                .reasonMessage(message)
                .leftValue(leftVal)
                .rightValue(rightVal)
                .score(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private int levenshteinDistance(String a, String b) {
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
