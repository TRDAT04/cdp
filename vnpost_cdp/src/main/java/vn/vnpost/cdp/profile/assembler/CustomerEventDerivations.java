package vn.vnpost.cdp.profile.assembler;

import org.springframework.util.CollectionUtils;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;

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

    // Khóa property theo data mẫu thật (test-data/customer-event-samples.txt)
    static final String PROP_ORDER_ID = "orderId";
    static final String PROP_AMOUNT = "amount";
    static final String PROP_SERVICE_CODE = "serviceCode";
    static final String PROP_ORDER_STATUS = "orderStatus";
    static final String PROP_DEVICE = "device";
    static final String PROP_PAYMENT_METHOD = "paymentMethod";

    /**
     * "Mảng dịch vụ chính": các {@code serviceCode} distinct từ event {@code createOrder},
     * giữ thứ tự xuất hiện (events truyền vào phải đã sort mới nhất trước), cắt còn {@code limit}.
     */
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
            if (serviceCode != null && !serviceCode.isBlank()) {
                lines.add(serviceCode.trim());
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
