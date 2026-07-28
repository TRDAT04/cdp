package vn.vnpost.example.profile.service.match;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vn.vnpost.example.profile.dto.match.ProfileMatchReasonCreateItem;
import vn.vnpost.example.profile.dto.match.ProfileMatchScoreResult;
import vn.vnpost.example.profile.entity.MasterProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import vn.vnpost.example.common.utils.IdentityUtils;
import vn.vnpost.example.ingestion.dto.NormalizedProfileData;

@Slf4j
@Service
public class ProfileMatchScoreService {

    private static final int SCORE_IDENTITY_NO = 50;
    private static final int SCORE_PHONE = 40;
    private static final int SCORE_EMAIL = 35;
    private static final int SCORE_NAME_EXACT = 30;
    private static final int SCORE_NAME_SIM_90 = 20;
    private static final int SCORE_NAME_SIM_85 = 15;
    private static final int SCORE_NAME_SIM_75 = 5;
    private static final int SCORE_DOB = 20;
    private static final int SCORE_PROVINCE = 10;
    private static final int SCORE_UNIT = 5;
    private static final int MAX_SCORE = 100;

    public ProfileMatchScoreResult calculate(MasterProfile left, MasterProfile right) {
        return calculateCore(
                left.getIdentityNo(), left.getPhone(), left.getEmail(), left.getFullName(), left.getDateOfBirth() != null ? left.getDateOfBirth().toString() : null, left.getProvinceCode(), left.getUnitCode(), left.getId().toString(),
                right.getIdentityNo(), right.getPhone(), right.getEmail(), right.getFullName(), right.getDateOfBirth() != null ? right.getDateOfBirth().toString() : null, right.getProvinceCode(), right.getUnitCode(), right.getId().toString()
        );
    }

    public ProfileMatchScoreResult calculate(NormalizedProfileData left, MasterProfile right) {
        return calculateCore(
                left.getIdentityNo(), left.getPhone(), left.getEmail(), left.getFullName(), left.getDateOfBirth() != null ? left.getDateOfBirth().toString() : null, left.getProvinceCode(), left.getUnitCode(), "incoming",
                right.getIdentityNo(), right.getPhone(), right.getEmail(), right.getFullName(), right.getDateOfBirth() != null ? right.getDateOfBirth().toString() : null, right.getProvinceCode(), right.getUnitCode(), right.getId().toString()
        );
    }

    private ProfileMatchScoreResult calculateCore(
            String leftIdNo, String leftPh, String leftEm, String leftFn, String leftDob, String leftProv, String leftUnit, String leftLogId,
            String rightIdNo, String rightPh, String rightEm, String rightFn, String rightDob, String rightProv, String rightUnit, String rightLogId) {

        List<ProfileMatchReasonCreateItem> reasons = new ArrayList<>();
        int rawScore = 0;
        boolean identityConflict = false;

        String leftIdentityNo  = IdentityUtils.normalizeText(leftIdNo);
        String rightIdentityNo = IdentityUtils.normalizeText(rightIdNo);
        String leftPhone       = IdentityUtils.normalizePhone(leftPh);
        String rightPhone      = IdentityUtils.normalizePhone(rightPh);
        String leftEmail       = IdentityUtils.normalizeEmail(leftEm);
        String rightEmail      = IdentityUtils.normalizeEmail(rightEm);
        String leftName        = IdentityUtils.normalizeName(leftFn);
        String rightName       = IdentityUtils.normalizeName(rightFn);

        // 1. Identity number
        if (StringUtils.hasText(leftIdentityNo) && StringUtils.hasText(rightIdentityNo)) {
            if (leftIdentityNo.equals(rightIdentityNo)) {
                rawScore += SCORE_IDENTITY_NO;
                reasons.add(reason("IDENTITY_NO_MATCH", "Identity number matched",
                        leftIdNo, rightIdNo, SCORE_IDENTITY_NO));
            } else {
                identityConflict = true;
                reasons.add(reason("IDENTITY_CONFLICT", "Identity numbers differ",
                        leftIdNo, rightIdNo, 0));
            }
        }

        // 2. Phone
        if (StringUtils.hasText(leftPhone) && StringUtils.hasText(rightPhone)) {
            if (leftPhone.equals(rightPhone)) {
                rawScore += SCORE_PHONE;
                reasons.add(reason("PHONE_MATCH", "Phone number matched",
                        leftPh, rightPh, SCORE_PHONE));
            } else {
                reasons.add(reason("PHONE_CONFLICT", "Phone numbers differ",
                        leftPh, rightPh, 0));
            }
        }

        // 3. Email
        if (StringUtils.hasText(leftEmail) && StringUtils.hasText(rightEmail)) {
            if (leftEmail.equals(rightEmail)) {
                rawScore += SCORE_EMAIL;
                reasons.add(reason("EMAIL_MATCH", "Email matched",
                        leftEm, rightEm, SCORE_EMAIL));
            } else {
                reasons.add(reason("EMAIL_CONFLICT", "Emails differ",
                        leftEm, rightEm, 0));
            }
        }

        // 4 & 5. Full name
        if (StringUtils.hasText(leftName) && StringUtils.hasText(rightName)) {
            if (leftName.equals(rightName)) {
                rawScore += SCORE_NAME_EXACT;
                reasons.add(reason("NAME_EXACT_MATCH", "Full name matched",
                        leftFn, rightFn, SCORE_NAME_EXACT));
            } else {
                double similarity = IdentityUtils.calculateNameSimilarity(leftName, rightName);
                if (similarity >= 90) {
                    rawScore += SCORE_NAME_SIM_90;
                    reasons.add(reason("NAME_SIMILAR", "Name similarity >= 90%",
                            leftFn, rightFn, SCORE_NAME_SIM_90));
                } else if (similarity >= 85) {
                    rawScore += SCORE_NAME_SIM_85;
                    reasons.add(reason("NAME_SIMILAR", "Name similarity >= 85%",
                            leftFn, rightFn, SCORE_NAME_SIM_85));
                } else if (similarity >= 75) {
                    rawScore += SCORE_NAME_SIM_75;
                    reasons.add(reason("NAME_SIMILAR", "Name similarity >= 75%",
                            leftFn, rightFn, SCORE_NAME_SIM_75));
                }
            }
        }

        // 6. Date of birth
        if (leftDob != null && rightDob != null
                && leftDob.equals(rightDob)) {
            rawScore += SCORE_DOB;
            reasons.add(reason("DATE_OF_BIRTH_MATCH", "Date of birth matched",
                    leftDob, rightDob, SCORE_DOB));
        }

        // 7. Province
        String leftProvince  = IdentityUtils.normalizeText(leftProv);
        String rightProvince = IdentityUtils.normalizeText(rightProv);
        if (StringUtils.hasText(leftProvince) && StringUtils.hasText(rightProvince)
                && leftProvince.equals(rightProvince)) {
            rawScore += SCORE_PROVINCE;
            reasons.add(reason("PROVINCE_MATCH", "Province matched",
                    leftProv, rightProv, SCORE_PROVINCE));
        }

        // 8. Unit
        String leftUnitNorm  = IdentityUtils.normalizeText(leftUnit);
        String rightUnitNorm = IdentityUtils.normalizeText(rightUnit);
        if (StringUtils.hasText(leftUnitNorm) && StringUtils.hasText(rightUnitNorm)
                && leftUnitNorm.equals(rightUnitNorm)) {
            rawScore += SCORE_UNIT;
            reasons.add(reason("UNIT_MATCH", "Unit matched",
                    leftUnit, rightUnit, SCORE_UNIT));
        }

        int finalScore = Math.min(rawScore, MAX_SCORE);
        boolean autoMergeRecommended = finalScore >= 95 && !identityConflict;
        String matchLevel = resolveMatchLevel(finalScore);

        log.debug("ProfileMatchScoreService - left={}, right={}, score={}, level={}, conflict={}",
                leftLogId, rightLogId, finalScore, matchLevel, identityConflict);

        return ProfileMatchScoreResult.builder()
                .score(BigDecimal.valueOf(finalScore).setScale(2, RoundingMode.HALF_UP))
                .matchLevel(matchLevel)
                .autoMergeRecommended(autoMergeRecommended)
                .identityConflict(identityConflict)
                .reasons(reasons)
                .build();
    }

    public boolean hasIdentityConflict(MasterProfile left, MasterProfile right) {
        return checkIdentityConflictCore(left.getIdentityNo(), right.getIdentityNo());
    }

    public boolean hasIdentityConflict(NormalizedProfileData left, MasterProfile right) {
        return checkIdentityConflictCore(left.getIdentityNo(), right.getIdentityNo());
    }

    private boolean checkIdentityConflictCore(String leftIdNo, String rightIdNo) {
        String leftId  = IdentityUtils.normalizeText(leftIdNo);
        String rightId = IdentityUtils.normalizeText(rightIdNo);

        if (StringUtils.hasText(leftId) && StringUtils.hasText(rightId) && !leftId.equals(rightId)) {
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


}
