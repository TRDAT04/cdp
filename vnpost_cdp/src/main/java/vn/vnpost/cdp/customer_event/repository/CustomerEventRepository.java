package vn.vnpost.cdp.customer_event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface CustomerEventRepository extends JpaRepository<CustomerEvent, Long>, JpaSpecificationExecutor<CustomerEvent> {

    /**
     * 50 event gần nhất của một profile, mới nhất trước — dùng cho tab Hành vi số (detail).
     */
    List<CustomerEvent> findTop50ByMasterProfileIdOrderByOccurredAtDesc(Long masterProfileId);

    /**
     * TOÀN BỘ event của một profile, mới nhất trước (KHÔNG giới hạn 50). Dùng cho các tính toán
     * cần đủ lịch sử (VD scoring: CLV/churn/engagement) thay vì bản top-50 của tab Hành vi số.
     */
    List<CustomerEvent> findByMasterProfileIdOrderByOccurredAtDesc(Long masterProfileId);

    /**
     * Lấy event cho nhiều profile trong một query (tránh N+1 khi build danh sách).
     * Sắp xếp mới nhất trước để caller group theo masterProfileId và giữ thứ tự thời gian.
     */
    List<CustomerEvent> findByMasterProfileIdInOrderByOccurredAtDesc(Collection<Long> masterProfileIds);

    /**
     * RFM theo phương pháp phân vị CHUẨN ngành (percentile/quintile) trên TOÀN BỘ khách hàng.
     *
     * <p>Bước 1 — với MỌI {@code master_profiles} tính chỉ số thô từ event {@code createOrder}:
     * <ul>
     *   <li>recency_days = số ngày từ đơn gần nhất (mọi thời điểm) đến {@code :now}; NULL nếu chưa từng mua</li>
     *   <li>frequency = số đơn trong cửa sổ {@code :windowStart}..now (12 tháng)</li>
     *   <li>monetary  = tổng {@code properties->>'amount'} trong cửa sổ 12 tháng</li>
     * </ul>
     *
     * <p>Bước 2 — xếp hạng bằng {@code NTILE(5)}, quy ước <b>5 = tốt nhất</b> (khớp segment mapping,
     * JSON mẫu và RFM chuẩn ngành). Vì {@code NTILE} gán bucket 1 cho dòng đứng đầu ORDER BY nên:
     * Recency dùng {@code DESC} (ngày nhỏ/gần đây → bucket cao = tốt), Frequency/Monetary dùng
     * {@code ASC} (giá trị lớn → bucket cao = tốt). Khách chưa mua (recency NULL) → {@code NULLS FIRST}
     * → bucket 1 (recency tệ nhất).
     *
     * <p>Chạy được với số profile bất kỳ (kể cả 3-5): NTILE không lỗi khi ít dòng, chỉ là các bucket
     * cao có thể trống. Trả về 1 dòng của {@code :profileId}: {@code [recency_score, frequency_score, monetary_score]}.
     */
    @Query(value = """
            SELECT scored.recency_score, scored.frequency_score, scored.monetary_score
            FROM (
                SELECT raw.mp_id,
                       NTILE(5) OVER (ORDER BY raw.recency_days DESC NULLS FIRST) AS recency_score,
                       NTILE(5) OVER (ORDER BY raw.frequency ASC)                 AS frequency_score,
                       NTILE(5) OVER (ORDER BY raw.monetary ASC)                  AS monetary_score
                FROM (
                    SELECT mp.id AS mp_id,
                           CASE WHEN MAX(ce.occurred_at) IS NULL THEN NULL
                                ELSE EXTRACT(EPOCH FROM (CAST(:now AS timestamp) - MAX(ce.occurred_at))) / 86400.0
                           END AS recency_days,
                           COUNT(ce.id) FILTER (WHERE ce.occurred_at >= :windowStart) AS frequency,
                           COALESCE(SUM(cast(ce.properties ->> 'amount' as numeric))
                                    FILTER (WHERE ce.occurred_at >= :windowStart), 0) AS monetary
                    FROM master_profiles mp
                    LEFT JOIN customer_events ce
                           ON ce.master_profile_id = mp.id
                          AND ce.event_type = 'createOrder'
                    GROUP BY mp.id
                ) raw
            ) scored
            WHERE scored.mp_id = :profileId
            """, nativeQuery = true)
    List<Object[]> findRfmScores(@Param("profileId") Long profileId,
                                 @Param("now") LocalDateTime now,
                                 @Param("windowStart") LocalDateTime windowStart);
}