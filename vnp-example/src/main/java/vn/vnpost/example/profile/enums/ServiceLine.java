package vn.vnpost.example.profile.enums;

/**
 * 7 mảng dịch vụ chính của VNPost dùng cho tính năng "Hoạt động theo mảng dịch vụ".
 *
 * <p>Nhãn tiếng Việt ({@link #getLabel()}) là TẠM — diễn giải theo tên viết tắt,
 * cần nghiệp vụ xác nhận lại khi làm chuẩn.</p>
 */
public enum ServiceLine {

    BCCP("Bưu chính chuyển phát"),
    TCBC("Tài chính bưu chính"),
    PPBL("Phân phối - bán lẻ"),
    HCC("Hành chính công"),
    LOGISTICS("Logistics"),
    TMDT("Thương mại điện tử"),
    MVNO("Di động MVNO");

    private final String label;

    ServiceLine(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
