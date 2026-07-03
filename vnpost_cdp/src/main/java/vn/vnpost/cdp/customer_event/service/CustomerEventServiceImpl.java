package vn.vnpost.cdp.customer_event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.customer_event.dto.CustomerEventMessage;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;
import vn.vnpost.cdp.customer_event.repository.CustomerEventRepository;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;
import vn.vnpost.cdp.profile.repository.MasterProfileRepository;
import vn.vnpost.cdp.profile.repository.ProfileSourceRecordRepository;
import vn.vnpost.cdp.unomi.dto.UnomiEventRequest;
import vn.vnpost.cdp.unomi.service.UnomiService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerEventServiceImpl implements CustomerEventService {

    private static final short SYNC_PENDING = 0;
    private static final short SYNC_SUCCESS = 1;
    private static final short SYNC_FAILED = 2;
    private static final short SYNC_UNMATCHED = 3;

    private static final Duration UNOMI_CALL_TIMEOUT = Duration.ofSeconds(5);

    private final ProfileSourceRecordRepository profileSourceRecordRepository;
    private final MasterProfileRepository masterProfileRepository;
    private final CustomerEventRepository customerEventRepository;
    private final UnomiService unomiService;

    @Value("${app.unomi.scope}")
    private String unomiScope;

    @Transactional
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

        ProfileSourceRecord sourceRecord = profileSourceRecordRepository
                .findFirstBySourceSystemAndSourceCustomerIdOrderByReceivedAtDesc(
                        message.getSourceSystem(),
                        message.getSourceCustomerId())
                .orElse(null);

        MasterProfile profile = null;

        if (sourceRecord != null) {
            profile = masterProfileRepository.findById(sourceRecord.getMasterProfileId())
                    .orElse(null);
            if (profile == null) {
                log.warn("CustomerEventService - masterProfileId={} referenced by sourceRecord not found, treating as UNMATCHED",
                        sourceRecord.getMasterProfileId());
            }
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

        if (!matched) {
            return;
        }

        UnomiEventRequest request = UnomiEventRequest.builder()
                .eventType(event.getEventType())
                .scope(unomiScope)
                .profileId(profile.getProfileCode()) 
                .sessionId(event.getSessionId())
                .source(event.getSource())
                .target(event.getTarget())
                .properties(event.getProperties())
                .build();

        try {
            log.info("CustomerEventService - sending to Unomi: eventCode={}, profileCode={}",
                    event.getEventCode(),
                    profile.getProfileCode());

            Object response = unomiService.sendEventToUnomi(request)
                    .block(UNOMI_CALL_TIMEOUT);

            log.info("CustomerEventService - Unomi response={}", response);

            event.setSyncStatus(SYNC_SUCCESS);
            event.setSyncedToUnomiAt(LocalDateTime.now());
            customerEventRepository.save(event);

        } catch (Exception ex) {

            event.setSyncStatus(SYNC_FAILED);
            event.setSyncedToUnomiAt(LocalDateTime.now());
            customerEventRepository.save(event);

            log.error("CustomerEventService - failed sync event={}",
                    event.getEventCode(), ex);
        }
    }
}