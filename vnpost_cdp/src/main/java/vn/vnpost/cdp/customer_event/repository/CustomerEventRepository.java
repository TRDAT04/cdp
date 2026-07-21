package vn.vnpost.cdp.customer_event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;

import java.util.Collection;
import java.util.List;

@Repository
public interface CustomerEventRepository extends JpaRepository<CustomerEvent, Long>, JpaSpecificationExecutor<CustomerEvent> {

    /**
     * 50 event gần nhất của một profile, mới nhất trước — dùng cho tab Hành vi số (detail).
     */
    List<CustomerEvent> findTop50ByMasterProfileIdOrderByOccurredAtDesc(Long masterProfileId);

    /**
     * Lấy event cho nhiều profile trong một query (tránh N+1 khi build danh sách).
     * Sắp xếp mới nhất trước để caller group theo masterProfileId và giữ thứ tự thời gian.
     */
    List<CustomerEvent> findByMasterProfileIdInOrderByOccurredAtDesc(Collection<Long> masterProfileIds);
}