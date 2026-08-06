package vn.vnpost.cdp.customer_event.service;

import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import vn.vnpost.cdp.customer_event.dto.CustomerEventDetailResponse;
import vn.vnpost.cdp.customer_event.dto.CustomerEventMessage;
import vn.vnpost.cdp.customer_event.dto.CustomerEventSearchRequest;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;
import vn.vnpost.cdp.customer_event.repository.CustomerEventRepository;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;
import vn.vnpost.cdp.profile.repository.MasterProfileRepository;
import vn.vnpost.cdp.profile.repository.ProfileSourceRecordRepository;
import vn.vnpost.cdp.unomi.dto.UnomiEventRequest;
import vn.vnpost.cdp.unomi.service.UnomiService;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerEventServiceImpl implements CustomerEventService {

    private static final short SYNC_PENDING   = 0;
    private static final short SYNC_SUCCESS   = 1;
    private static final short SYNC_FAILED    = 2;
    private static final short SYNC_UNMATCHED = 3;

    private static final Duration UNOMI_CALL_TIMEOUT = Duration.ofSeconds(5);

    private final ProfileSourceRecordRepository profileSourceRecordRepository;
    private final MasterProfileRepository masterProfileRepository;
    private final CustomerEventRepository customerEventRepository;
    private final UnomiService unomiService;

    @Value("${app.unomi.scope}")
    private String unomiScope;

 
    @Lazy
    @Autowired
    private CustomerEventServiceImpl self;

    // ======================== Ingestion Pipeline ========================

    @Override
    public void process(CustomerEventMessage message) {
        if (message == null) {
            log.warn("CustomerEventService - message is null");
            return;
        }
        log.info("Processing customer event: messageId={}, sourceSystem={}, sourceCustomerId={}",
                message.getMessageId(),
                message.getSourceSystem(),
                message.getSourceCustomerId());

        Long savedEventId = self.saveEvent(message);
        self.syncToUnomi(savedEventId, message.getMessageId());
    }


    @Transactional
    public Long saveEvent(CustomerEventMessage message) {
        ProfileSourceRecord sourceRecord = profileSourceRecordRepository
                .findFirstBySourceSystemAndSourceCustomerIdOrderByReceivedAtDesc(
                        message.getSourceSystem(),
                        message.getSourceCustomerId())
                .orElse(null);

        MasterProfile profile = null;

        if (sourceRecord != null && sourceRecord.getMasterProfileId() != null) {
            profile = masterProfileRepository.findById(sourceRecord.getMasterProfileId())
                    .orElse(null);

            if (profile == null) {
                log.warn("CustomerEventService - masterProfileId={} referenced by sourceRecord not found, treating as UNMATCHED",
                        sourceRecord.getMasterProfileId());
            }
        } else if (sourceRecord != null) {
            // Nhánh CONFLICT/REJECT của luồng ingest để masterProfileId = null. Không chặn null ở đây
            // thì findById(null) ném InvalidDataAccessApiUsageException, consumer bắt exception rồi vẫn
            // acknowledge() → event MẤT HẲN, không có cả dòng UNMATCHED để truy lại.
            log.warn("CustomerEventService - sourceRecord id={} chưa gắn masterProfileId "
                            + "(ingest ra CONFLICT/REJECT), treating as UNMATCHED",
                    sourceRecord.getId());
        }

        boolean matched = profile != null;

        if (matched) {
            log.info("CustomerEventService - matched profileCode={}, eventType={}",
                    profile.getProfileCode(),
                    message.getEventType());
        } else {
            log.warn("CustomerEventService - UNMATCHED event: sourceSystem={}, sourceCustomerId={}",
                    message.getSourceSystem(),
                    message.getSourceCustomerId());
        }

        Map<String, Object> sourceItem = message.getSource() != null
                ? message.getSource()
                : Map.of(
                "itemType", "system",
                "scope", unomiScope,
                "itemId", message.getSourceSystem()
        );

        CustomerEvent event = CustomerEvent.builder()
                .eventCode(message.getMessageId())
                .masterProfileId(matched ? profile.getId() : null)
                .profileCode(matched ? profile.getProfileCode() : null)
                .sourceSystem(message.getSourceSystem())
                .sourceCustomerId(message.getSourceCustomerId())
                .eventType(message.getEventType())
                .occurredAt(message.getOccurredAt())
                .sessionId(message.getSessionId())
                .properties(message.getProperties())
                .source(sourceItem)
                .target(message.getTarget())
                .syncStatus(matched ? SYNC_PENDING : SYNC_UNMATCHED)
                .build();

        event = customerEventRepository.save(event);

        log.info("CustomerEventService - saved event={}, status={}",
                event.getEventCode(),
                event.getSyncStatus());

        return event.getId();
    }


    public void syncToUnomi(Long eventId, String eventCode) {
        CustomerEvent event = customerEventRepository.findById(eventId).orElse(null);

        if (event == null) {
            log.error("CustomerEventService - syncToUnomi: eventId={} not found, skipping", eventId);
            return;
        }
        if (event.getSyncStatus() == SYNC_UNMATCHED) {
            return;
        }

        Map<String, Object> properties = event.getProperties();

        if ("createOrder".equals(event.getEventType())) {
            properties.put(
                    "transactionDate",
                    event.getOccurredAt()
                            .atZone(ZoneOffset.UTC)
                            .toInstant()
                            .truncatedTo(ChronoUnit.MILLIS)
                            .toString()
            );
        }

        UnomiEventRequest request = UnomiEventRequest.builder()
                .eventType(event.getEventType())
                .scope(unomiScope)
                .profileId(event.getProfileCode())
                .sessionId(event.getSessionId())
                .source(event.getSource())
                .target(event.getTarget())
                .properties(event.getProperties())
                .build();

        try {
            log.info("CustomerEventService - sending to Unomi: eventCode={}, profileCode={}",
                    event.getEventCode(),
                    event.getProfileCode());

            Object response = unomiService.sendEventToUnomi(request)
                    .block(UNOMI_CALL_TIMEOUT);

            log.info("CustomerEventService - Unomi response={}", response);

            self.updateSyncStatus(event, SYNC_SUCCESS);

        } catch (Exception ex) {
            self.updateSyncStatus(event, SYNC_FAILED);
            log.error("CustomerEventService - failed sync event={}", eventCode, ex);
        }
    }


    @Transactional
    public void updateSyncStatus(CustomerEvent event, short status) {
        event.setSyncStatus(status);
        event.setSyncedToUnomiAt(LocalDateTime.now());
        customerEventRepository.save(event);
    }

    // ======================== Search / Query ========================

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerEventDetailResponse> searchEvents(
            CustomerEventSearchRequest request,
            Pageable pageable) {

        Specification<CustomerEvent> spec = (root, query, cb) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(request.getEventCode())) {
                predicates.add(cb.equal(root.get("eventCode"), request.getEventCode()));
            }

            if (request.getMasterProfileId() != null) {
                predicates.add(cb.equal(root.get("masterProfileId"), request.getMasterProfileId()));
            }

            if (StringUtils.hasText(request.getEventType())) {
                predicates.add(cb.equal(root.get("eventType"), request.getEventType()));
            }

            if (StringUtils.hasText(request.getSourceSystem())) {
                predicates.add(cb.equal(root.get("sourceSystem"), request.getSourceSystem()));
            }

            if (StringUtils.hasText(request.getSessionId())) {
                predicates.add(cb.equal(root.get("sessionId"), request.getSessionId()));
            }

            LocalDateTime from = request.getFromDate();
            LocalDateTime to = request.getToDate();

            if (request.getTimeRangeDays() != null && request.getTimeRangeDays() > 0) {
                if (request.getFromDate() != null || request.getToDate() != null) {
                    log.warn("searchEvents - timeRangeDays={} is set alongside fromDate/toDate; " +
                                    "timeRangeDays takes precedence and fromDate/toDate will be ignored.",
                            request.getTimeRangeDays());
                }
                from = LocalDateTime.now().minusDays(request.getTimeRangeDays());
                to = LocalDateTime.now();
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }

            if (StringUtils.hasText(request.getScope())) {
                predicates.add(cb.equal(
                        cb.function(
                                "jsonb_extract_path_text",
                                String.class,
                                root.get("source"),
                                cb.literal("scope")
                        ),
                        request.getScope()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return customerEventRepository.findAll(spec, pageable)
                .map(this::toDetailResponse);
    }

    private CustomerEventDetailResponse toDetailResponse(CustomerEvent event) {
        return CustomerEventDetailResponse.builder()
                .id(event.getId())
                .eventCode(event.getEventCode())
                .masterProfileId(event.getMasterProfileId())
                .profileCode(event.getProfileCode())
                .eventType(event.getEventType())
                .sessionId(event.getSessionId())
                .sourceSystem(event.getSourceSystem())
                .sourceCustomerId(event.getSourceCustomerId())
                .occurredAt(event.getOccurredAt())
                .properties(event.getProperties())
                .source(event.getSource())
                .target(event.getTarget())
                .syncStatus(event.getSyncStatus())
                .syncedToUnomiAt(event.getSyncedToUnomiAt())
                .build();
    }
}