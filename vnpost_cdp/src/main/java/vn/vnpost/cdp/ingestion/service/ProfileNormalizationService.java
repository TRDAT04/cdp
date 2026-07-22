package vn.vnpost.cdp.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.ingestion.dto.ProfileIngestionMessage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
public class ProfileNormalizationService {

    public NormalizedProfileData normalize(ProfileIngestionMessage message) {
        Map<String, Object> payload = message.getPayload() != null ? message.getPayload() : Collections.emptyMap();

        String fullName = normalizeFullName(getString(payload, "fullName"));
        String phone = normalizePhone(getString(payload, "phone"));
        String email = normalizeEmail(getString(payload, "email"));
        String identityNo = normalizeIdentityNo(getString(payload, "identityNo"));
        String taxCode = normalizeIdentityNo(getString(payload, "taxCode"));
        String gender = trimString(getString(payload, "gender"));
        String customerType = trimString(getString(payload, "customerType"));
        String customerTier = trimString(getString(payload, "customerTier"));
        String provinceCode = trimString(getString(payload, "provinceCode"));
        String provinceName = trimString(getString(payload, "provinceName"));
        String unitCode = trimString(getString(payload, "unitCode"));
        String unitName = trimString(getString(payload, "unitName"));
        LocalDate dateOfBirth = parseDate(getString(payload, "dateOfBirth"));
        LocalDateTime lastVisitAt = parseDateTime(getString(payload, "lastVisitAt"));
        List<String> interestedServices = extractStringList(payload, "interestedServices");

        // --- Định danh liên nguồn (enrichment) ---
        String postId = trimString(getString(payload, "postId"));
        String crmId = trimString(getString(payload, "crmId"));
        String khlCode = trimString(getString(payload, "khlCode"));
        String appUserId = trimString(getString(payload, "appUserId"));
        String deviceId = trimString(getString(payload, "deviceId"));
        String cookieId = trimString(getString(payload, "cookieId"));
        String paymentId = trimString(getString(payload, "paymentId"));

        Map<String, Object> normalizedPayload = new LinkedHashMap<>();
        putIfNotNull(normalizedPayload, "sourceSystem", message.getSourceSystem());
        putIfNotNull(normalizedPayload, "sourceCustomerId", message.getSourceCustomerId());
        putIfNotNull(normalizedPayload, "eventType", message.getEventType());
        putIfNotNull(normalizedPayload, "fullName", fullName);
        putIfNotNull(normalizedPayload, "phone", phone);
        putIfNotNull(normalizedPayload, "email", email);
        putIfNotNull(normalizedPayload, "identityNo", identityNo);
        putIfNotNull(normalizedPayload, "taxCode", taxCode);
        putIfNotNull(normalizedPayload, "gender", gender);
        putIfNotNull(normalizedPayload, "customerType", customerType);
        putIfNotNull(normalizedPayload, "customerTier", customerTier);
        putIfNotNull(normalizedPayload, "provinceCode", provinceCode);
        putIfNotNull(normalizedPayload, "provinceName", provinceName);
        putIfNotNull(normalizedPayload, "unitCode", unitCode);
        putIfNotNull(normalizedPayload, "unitName", unitName);
        putIfNotNull(normalizedPayload, "postId", postId);
        putIfNotNull(normalizedPayload, "crmId", crmId);
        putIfNotNull(normalizedPayload, "khlCode", khlCode);
        putIfNotNull(normalizedPayload, "appUserId", appUserId);
        putIfNotNull(normalizedPayload, "deviceId", deviceId);
        putIfNotNull(normalizedPayload, "cookieId", cookieId);
        putIfNotNull(normalizedPayload, "paymentId", paymentId);
        if (dateOfBirth != null) normalizedPayload.put("dateOfBirth", dateOfBirth.toString());
        if (lastVisitAt != null) normalizedPayload.put("lastVisitAt", lastVisitAt.toString());
        if (!interestedServices.isEmpty()) normalizedPayload.put("interestedServices", interestedServices);

        return NormalizedProfileData.builder()
                .sourceSystem(message.getSourceSystem())
                .sourceCustomerId(message.getSourceCustomerId())
                .eventType(message.getEventType())
                .fullName(fullName)
                .phone(phone)
                .email(email)
                .identityNo(identityNo)
                .taxCode(taxCode)
                .gender(gender)
                .dateOfBirth(dateOfBirth)
                .customerType(customerType)
                .customerTier(customerTier)
                .provinceCode(provinceCode)
                .provinceName(provinceName)
                .unitCode(unitCode)
                .unitName(unitName)
                .postId(postId)
                .crmId(crmId)
                .khlCode(khlCode)
                .appUserId(appUserId)
                .deviceId(deviceId)
                .cookieId(cookieId)
                .paymentId(paymentId)
                .interestedServices(interestedServices)
                .lastVisitAt(lastVisitAt)
                .normalizedPayload(normalizedPayload)
                .build();
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private String trimString(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }

    private String normalizeFullName(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim().replaceAll("\\s+", " ");
    }

    private String normalizePhone(String s) {
        if (s == null || s.isBlank()) return null;
        // Keep digits and optional leading '+'
        String cleaned = s.replaceAll("[\\s\\-]", "");
        if (cleaned.isBlank()) return null;
        return cleaned;
    }

    private String normalizeEmail(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim().toLowerCase();
    }

    private String normalizeIdentityNo(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim().replaceAll("\\s+", "");
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String[] formats = {"yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy"};
        for (String fmt : formats) {
            try {
                return LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern(fmt));
            } catch (DateTimeParseException ignored) {}
        }
        log.warn("ProfileNormalizationService - cannot parse date: {}", s);
        return null;
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        String[] formats = {"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String fmt : formats) {
            try {
                if (fmt.equals("yyyy-MM-dd")) {
                    return LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern(fmt)).atStartOfDay();
                }
                return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern(fmt));
            } catch (DateTimeParseException ignored) {}
        }
        log.warn("ProfileNormalizationService - cannot parse datetime: {}", s);
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return Collections.emptyList();
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) result.add(item.toString());
            }
            return result;
        }
        return Collections.emptyList();
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null && !(value instanceof String str && str.isBlank())) {
            map.put(key, value);
        }
    }
}
