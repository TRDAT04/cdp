package vn.vnpost.cdp.customer_event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.vnpost.cdp.customer_event.entity.EventSchema;


public interface EventSchemaRepository extends JpaRepository<EventSchema, Long> {
    boolean existsByEventTypeAndSchemaVersion(
            String eventType,
            String schemaVersion);

}
