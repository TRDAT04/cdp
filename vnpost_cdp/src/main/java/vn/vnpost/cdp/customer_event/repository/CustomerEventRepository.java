package vn.vnpost.cdp.customer_event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;

@Repository
public interface CustomerEventRepository extends JpaRepository<CustomerEvent, Long>, JpaSpecificationExecutor<CustomerEvent> {

}