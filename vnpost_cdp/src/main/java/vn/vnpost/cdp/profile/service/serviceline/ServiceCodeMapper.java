package vn.vnpost.cdp.profile.service.serviceline;

import lombok.extern.slf4j.Slf4j;
import vn.vnpost.cdp.profile.enums.ServiceLine;

import java.util.Map;

/**
 * Ánh xạ {@code serviceCode} (trong {@code customer_events.properties}) sang {@link ServiceLine}.
 *
 * <p><b>TẠM — DATA DEMO:</b> bảng ánh xạ dưới đây là mã dịch vụ tạm để demo, CẦN THAY BẰNG
 * mã dịch vụ THẬT từ CAS / MPITS / PayPost / TMS-WMS khi nghiệp vụ xác nhận. Cố ý dùng
 * static map trong code (KHÔNG tạo bảng DB) vì đây là data ít thay đổi ở giai đoạn demo.</p>
 */
@Slf4j
public final class ServiceCodeMapper {

    private ServiceCodeMapper() {
    }

    private static final Map<String, ServiceLine> MAPPING = Map.ofEntries(
            // --- BCCP: Bưu chính chuyển phát ---
            Map.entry("EMS", ServiceLine.BCCP),
            Map.entry("BUU_PHAM_THUONG", ServiceLine.BCCP),
            // --- TCBC: Tài chính bưu chính ---
            Map.entry("THU_HO_COD", ServiceLine.TCBC),
            Map.entry("CHUYEN_TIEN", ServiceLine.TCBC),
            // --- PPBL: Phân phối - bán lẻ ---
            Map.entry("POS_RETAIL", ServiceLine.PPBL),
            // --- HCC: Hành chính công ---
            Map.entry("HCC_CONG_MOT_CUA", ServiceLine.HCC),
            // --- LOGISTICS ---
            Map.entry("KHO_VAN", ServiceLine.LOGISTICS),
            // --- TMĐT: Thương mại điện tử ---
            Map.entry("FULFILLMENT_TMDT", ServiceLine.TMDT),
            // --- MVNO: Di động ---
            Map.entry("SIM_DATA_MVNO", ServiceLine.MVNO)
    );


    public static ServiceLine resolve(String serviceCode) {
        if (serviceCode == null || serviceCode.isBlank()) {
            return null;
        }
        ServiceLine line = MAPPING.get(serviceCode.trim().toUpperCase());
        if (line == null) {
            log.warn("ServiceCodeMapper - serviceCode chưa được ánh xạ: '{}' (cần bổ sung vào MAPPING)", serviceCode);
        }
        return line;
    }
}
