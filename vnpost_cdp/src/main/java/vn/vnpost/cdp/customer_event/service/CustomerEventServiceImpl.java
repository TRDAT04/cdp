package vn.vnpost.cdp.customer_event.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Mono;
import vn.vnpost.cdp.customer_event.dto.CustomerEventDetailResponse;
import vn.vnpost.cdp.customer_event.dto.CustomerEventMessage;
import vn.vnpost.cdp.customer_event.dto.CustomerEventSearchRequest;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;
import vn.vnpost.cdp.customer_event.repository.CustomerEventRepository;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.repository.MasterProfileRepository;
import vn.vnpost.cdp.profile.repository.ProfileSourceRecordRepository;
import vn.vnpost.cdp.unomi.dto.UnomiEventRequest;
import vn.vnpost.cdp.unomi.service.UnomiService;

/**
 * Reactive port. Bản gốc dùng {@code @Lazy @Autowired self} để "tự gọi lại chính mình qua proxy"
 * — cách duy nhất trong Spring MVC/AOP để {@code @Transactional} có hiệu lực khi một method
 * không transactional gọi một method transactional TRÊN CÙNG bean (self-invocation không đi qua
 * proxy). Ở đây bỏ hẳn {@code @Transactional} cho {@code saveEvent}/{@code updateSyncStatus} vì
 * mỗi thao tác chỉ là MỘT lệnh ghi đơn (1 insert / 1 update) — vốn đã atomic tự nhiên qua R2DBC,
 * không cần bọc transaction, nên bỏ luôn workaround self-injection thay vì port nguyên xi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerEventServiceImpl implements CustomerEventService {

    private static final short SYNC_PENDING   = 0;
    private static final short SYNC_SUCCESS   = 1;
    private static final short SYNC_FAILED    = 2;
    private static final short SYNC_UNMATCHED = 3;

    private static final Duration UNOMI_CALL_TIMEOUT = Duration.ofSeconds(5);

    private static final Map<String, String> SORTABLE_COLUMNS = Map.ofEntries(
            Map.entry("id", "id"),
            Map.entry("eventCode", "event_code"),
            Map.entry("masterProfileId", "master_profile_id"),
            Map.entry("eventType", "event_type"),
            Map.entry("sourceSystem", "source_system"),
            Map.entry("sessionId", "session_id"),
            Map.entry("occurredAt", "occurred_at"),
            Map.entry("created", "created"),
            Map.entry("modified", "modified")
    );

    private static final String SEARCH_WHERE_SQL = """
            WHERE (:eventCodeActive = false OR event_code = :eventCode)
              AND (:masterProfileIdActive = false OR master_profile_id = :masterProfileId)
              AND (:eventTypeActive = false OR event_type = :eventType)
              AND (:sourceSystemActive = false OR source_system = :sourceSystem)
              AND (:sessionIdActive = false OR session_id = :sessionId)
              AND (:fromActive = false OR occurred_at >= :fromDate)
              AND (:toActive = false OR occurred_at <= :toDate)
              AND (:scopeActive = false OR jsonb_extract_path_text(source, 'scope') = :scope)
            """;

    private final ProfileSourceRecordRepository profileSourceRecordRepository;
    private final MasterProfileRepository masterProfileRepository;
    private final CustomerEventRepository customerEventRepository;
    private final UnomiService unomiService;
    private final R2dbcEntityTemplate entityTemplate;

    @Value("${app.unomi.scope}")
    private String unomiScope;

    // ======================== Ingestion Pipeline ========================

    @Override
    public Mono<Void> process(CustomerEventMessage message) {
        if (message == null) {
            log.warn("CustomerEventService - message is null");
            return Mono.empty();
        }
        log.info("Processing customer event: messageId={}, sourceSystem={}, sourceCustomerId={}",
                message.getMessageId(),
                message.getSourceSystem(),
                message.getSourceCustomerId());

        return saveEvent(message)
                .flatMap(savedEventId -> syncToUnomi(savedEventId, message.getMessageId()));
    }

    private Mono<Long> saveEvent(CustomerEventMessage message) {
        Mono<Optional<MasterProfile>> profileMono = profileSourceRecordRepository
                .findFirstBySourceSystemAndSourceCustomerIdOrderByReceivedAtDesc(
                        message.getSourceSystem(), message.getSourceCustomerId())
                .flatMap(sourceRecord -> {
                    if (sourceRecord.getMasterProfileId() == null) {
                        // Nhánh CONFLICT/REJECT của luồng ingest để masterProfileId = null. Không chặn
                        // null ở đây thì findById(null) ném lỗi, consumer bắt exception rồi vẫn
                        // acknowledge() → event MẤT HẲN, không có cả dòng UNMATCHED để truy lại.
                        log.warn("CustomerEventService - sourceRecord id={} chưa gắn masterProfileId " +
                                "(ingest ra CONFLICT/REJECT), treating as UNMATCHED", sourceRecord.getId());
                        return Mono.just(Optional.<MasterProfile>empty());
                    }
                    return masterProfileRepository.findById(sourceRecord.getMasterProfileId())
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.empty())
                            .doOnNext(opt -> {
                                if (opt.isEmpty()) {
                                    log.warn("CustomerEventService - masterProfileId={} referenced by sourceRecord " +
                                            "not found, treating as UNMATCHED", sourceRecord.getMasterProfileId());
                                }
                            });
                })
                .defaultIfEmpty(Optional.empty());

        return profileMono.flatMap(profileOpt -> {
            boolean matched = profileOpt.isPresent();
            MasterProfile profile = profileOpt.orElse(null);

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

            return customerEventRepository.save(event)
                    .doOnNext(saved -> log.info("CustomerEventService - saved event={}, status={}",
                            saved.getEventCode(),
                            saved.getSyncStatus()))
                    .map(CustomerEvent::getId);
        });
    }

    private Mono<Void> syncToUnomi(Long eventId, String eventCode) {
        return customerEventRepository.findById(eventId)
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.error("CustomerEventService - syncToUnomi: eventId={} not found, skipping", eventId)))
                .flatMap(event -> {
                    if (event.getSyncStatus() == SYNC_UNMATCHED) {
                        return Mono.<Void>empty();
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

                    log.info("CustomerEventService - sending to Unomi: eventCode={}, profileCode={}",
                            event.getEventCode(),
                            event.getProfileCode());

                    return unomiService.sendEventToUnomi(request)
                            .timeout(UNOMI_CALL_TIMEOUT)
                            .doOnNext(response -> log.info("CustomerEventService - Unomi response={}", response))
                            .flatMap(response -> updateSyncStatus(event, SYNC_SUCCESS))
                            .onErrorResume(ex -> {
                                log.error("CustomerEventService - failed sync event={}", eventCode, ex);
                                return updateSyncStatus(event, SYNC_FAILED);
                            });
                });
    }

    private Mono<Void> updateSyncStatus(CustomerEvent event, short status) {
        event.setSyncStatus(status);
        event.setSyncedToUnomiAt(LocalDateTime.now());
        return customerEventRepository.save(event).then();
    }

    // ======================== Search / Query ========================

    @Override
    public Mono<Page<CustomerEventDetailResponse>> searchEvents(CustomerEventSearchRequest request, Pageable pageable) {
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

        String sortColumn = resolveSortColumn(pageable);
        String direction = resolveSortDirection(pageable);

        String dataSql = "SELECT * FROM customer_events " + SEARCH_WHERE_SQL
                + "ORDER BY " + sortColumn + " " + direction + " LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) FROM customer_events " + SEARCH_WHERE_SQL;

        LocalDateTime finalFrom = from;
        LocalDateTime finalTo = to;

        Mono<List<CustomerEventDetailResponse>> contentMono = bindSearchParams(
                        entityTemplate.getDatabaseClient().sql(dataSql), request, finalFrom, finalTo)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map((row, metadata) -> entityTemplate.getConverter().read(CustomerEvent.class, row, metadata))
                .all()
                .map(this::toDetailResponse)
                .collectList();

        Mono<Long> totalMono = bindSearchParams(
                        entityTemplate.getDatabaseClient().sql(countSql), request, finalFrom, finalTo)
                .map((row, metadata) -> row.get(0, Long.class))
                .one();

        return Mono.zip(contentMono, totalMono)
                .map(t -> new PageImpl<>(t.getT1(), pageable, t.getT2()));
    }

    private DatabaseClient.GenericExecuteSpec bindSearchParams(DatabaseClient.GenericExecuteSpec spec,
                                                                CustomerEventSearchRequest request,
                                                                LocalDateTime from, LocalDateTime to) {
        boolean eventCodeActive = StringUtils.hasText(request.getEventCode());
        boolean masterProfileIdActive = request.getMasterProfileId() != null;
        boolean eventTypeActive = StringUtils.hasText(request.getEventType());
        boolean sourceSystemActive = StringUtils.hasText(request.getSourceSystem());
        boolean sessionIdActive = StringUtils.hasText(request.getSessionId());
        boolean fromActive = from != null;
        boolean toActive = to != null;
        boolean scopeActive = StringUtils.hasText(request.getScope());

        return spec
                .bind("eventCodeActive", eventCodeActive)
                .bind("eventCode", eventCodeActive ? request.getEventCode() : "")
                .bind("masterProfileIdActive", masterProfileIdActive)
                .bind("masterProfileId", masterProfileIdActive ? request.getMasterProfileId() : -1L)
                .bind("eventTypeActive", eventTypeActive)
                .bind("eventType", eventTypeActive ? request.getEventType() : "")
                .bind("sourceSystemActive", sourceSystemActive)
                .bind("sourceSystem", sourceSystemActive ? request.getSourceSystem() : "")
                .bind("sessionIdActive", sessionIdActive)
                .bind("sessionId", sessionIdActive ? request.getSessionId() : "")
                .bind("fromActive", fromActive)
                .bind("fromDate", fromActive ? from : LocalDateTime.MIN)
                .bind("toActive", toActive)
                .bind("toDate", toActive ? to : LocalDateTime.MAX)
                .bind("scopeActive", scopeActive)
                .bind("scope", scopeActive ? request.getScope() : "");
    }

    private String resolveSortColumn(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return "occurred_at";
        }
        Sort.Order order = pageable.getSort().iterator().next();
        String column = SORTABLE_COLUMNS.get(order.getProperty());
        return column != null ? column : "occurred_at";
    }

    private String resolveSortDirection(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return "DESC";
        }
        return pageable.getSort().iterator().next().getDirection().name();
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
