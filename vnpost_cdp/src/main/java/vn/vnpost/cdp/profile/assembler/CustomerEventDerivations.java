package vn.vnpost.cdp.profile.assembler;

import org.springframework.util.CollectionUtils;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;
import vn.vnpost.cdp.profile.enums.ServiceLine;
import vn.vnpost.cdp.profile.service.serviceline.ServiceCodeMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Suy diễn dùng chung từ {@code customer_events} cho các assembler của profile.
 * Giữ một nguồn duy nhất cho tên eventType và cách đọc {@code properties} (jsonb).
 */
final class CustomerEventDerivations {

    private CustomerEventDerivations() {
    }

    static final String EVENT_LOGIN = "customerLogin";
    static final String EVENT_CREATE_ORDER = "createOrder";
    // TODO: xác nhận tên eventType phản hồi campaign với nguồn dữ liệu (tạm dùng "campaignResponse").
    static final String EVENT_CAMPAIGN_RESPONSE = "campaignResponse";
    static final String EVENT_COMPLAINT_CREATED = "complaintCreated";
    static final String EVENT_COMPLAINT_RESOLVED = "complaintResolved";

    // Khóa property theo data mẫu thật (test-data/customer-event-samples.txt)
    static final String PROP_ORDER_ID = "orderId";
    static final String PROP_AMOUNT = "amount";
    static final String PROP_SERVICE_CODE = "serviceCode";
    static final String PROP_ORDER_STATUS = "orderStatus";
    static final String PROP_DEVICE = "device";
    static final String PROP_PAYMENT_METHOD = "paymentMethod";
    static final String PROP_CAMPAIGN_CODE = "campaignCode";
    static final String PROP_CHANNEL = "channel";
    // CSKH — khiếu nại
    static final String PROP_COMPLAINT_ID = "complaintId";
    static final String PROP_STATUS = "status";
    static final String PROP_SLA_DEADLINE = "slaDeadline";
    static final String PROP_RESOLVED_AT = "resolvedAt";
    static final String PROP_SATISFACTION_SCORE = "satisfactionScore";


    static List<String> resolveTopServiceLines(List<CustomerEvent> events, int limit) {
        if (CollectionUtils.isEmpty(events) || limit <= 0) {
            return List.of();
        }
        Set<String> lines = new LinkedHashSet<>();
        for (CustomerEvent e : events) {
            if (!EVENT_CREATE_ORDER.equals(e.getEventType())) {
                continue;
            }
            String serviceCode = asString(e.getProperties(), PROP_SERVICE_CODE);
            ServiceLine line = ServiceCodeMapper.resolve(serviceCode);
            if (line != null) {
                lines.add(line.name());
                if (lines.size() >= limit) {
                    break;
                }
            }
        }
        return new ArrayList<>(lines);
    }

    static String asString(Map<String, Object> props, String key) {
        if (props == null) {
            return null;
        }
        Object v = props.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * Đọc property dạng ISO local date-time (vd "2026-06-10T16:00:00"). Trả null nếu thiếu
     * hoặc không parse được (không ném lỗi để API không vỡ vì data lệch format).
     */
    static java.time.LocalDateTime asLocalDateTime(Map<String, Object> props, String key) {
        String v = asString(props, key);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(v.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    static BigDecimal asBigDecimal(Map<String, Object> props, String key) {
        if (props == null) {
            return null;
        }
        Object v = props.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof java.math.BigInteger bi) {
            return new BigDecimal(bi);
        }
        // Số nguyên (Integer/Long/Short/Byte): giữ nguyên, tránh dạng khoa học (4.5E+7)
        if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
            return BigDecimal.valueOf(((Number) v).longValue());
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            // new BigDecimal(String) giữ đúng biểu diễn thập phân, không sinh số mũ
            return new BigDecimal(v.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
