package vn.vnpost.cdp.ingestion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.vnpost.cdp.ingestion.dto.MergeDecisionResult;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.ingestion.enums.MergeDecision;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.entity.ProfileIdentityLink;
import vn.vnpost.cdp.profile.repository.ProfileIdentityLinkRepository;
import vn.vnpost.cdp.profile.service.match.ProfileMatchScoreService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bảng quyết định của luồng ingest — đây là logic quyết định dữ liệu khách hàng bị gộp vào đâu,
 * và merge không có cơ chế hoàn tác, nên nó cần test chặt hơn mọi phần khác.
 *
 * <p>Dùng fake repository viết tay thay vì Mockito: các assert ở đây phụ thuộc vào việc lọc theo
 * status/type của link, fake tường minh dễ đọc hơn một chuỗi when/thenReturn.
 */
class ProfileMergeDecisionServiceTest {

    private FakeIdentityLinkRepository linkRepository;
    private ProfileMergeDecisionService service;

    @BeforeEach
    void setUp() {
        linkRepository = new FakeIdentityLinkRepository();
        service = new ProfileMergeDecisionService(linkRepository, new ProfileMatchScoreService());
    }

    // =====================================================================
    // Kiểm tra đầu vào
    // =====================================================================

    @Test
    @DisplayName("Nguồn không hợp lệ → REJECT")
    void unknownSourceSystemIsRejected() {
        NormalizedProfileData data = data().sourceSystem("KHONG_TON_TAI").phone("0912345678").build();

        assertThat(service.decide(data, List.of()).decision()).isEqualTo(MergeDecision.REJECT);
    }

    @Test
    @DisplayName("Payload không có khóa nào dùng được → REJECT")
    void payloadWithoutAnyUsableIdentityIsRejected() {
        // Không dùng helper data(): nó set sẵn sourceCustomerId, mà bản thân sourceCustomerId đã là
        // một khóa dùng được. Chỉ có tên thì không đủ để đối sánh bất cứ thứ gì.
        NormalizedProfileData data = NormalizedProfileData.builder()
                .sourceSystem("CRM")
                .eventType("PROFILE_CREATED")
                .fullName("Nguyễn Văn A")
                .build();

        assertThat(service.decide(data, List.of()).decision()).isEqualTo(MergeDecision.REJECT);
    }

    @Test
    @DisplayName("Chỉ có sourceCustomerId cũng đủ để được xử lý")
    void payloadWithOnlySourceCustomerIdIsAccepted() {
        NormalizedProfileData data = NormalizedProfileData.builder()
                .sourceSystem("CRM")
                .sourceCustomerId("CRM-001")
                .eventType("PROFILE_CREATED")
                .build();

        assertThat(service.decide(data, List.of()).decision())
                .isEqualTo(MergeDecision.CREATE_NEW_PROFILE);
    }

    @Test
    @DisplayName("Record CHỈ có MST vẫn được xử lý, KHÔNG bị REJECT")
    void payloadWithOnlyTaxCodeIsAccepted() {
        // MST là khóa mà ProfileMatchingService thực sự tra candidate bằng — reject nó là sai.
        // Không set sourceCustomerId, nếu không test sẽ pass nhờ khóa khác chứ không nhờ taxCode.
        NormalizedProfileData data = NormalizedProfileData.builder()
                .sourceSystem("CRM")
                .eventType("PROFILE_CREATED")
                .taxCode("0101234567")
                .build();

        assertThat(service.decide(data, List.of()).decision())
                .isEqualTo(MergeDecision.CREATE_NEW_PROFILE);
    }

    @Test
    @DisplayName("Record CHỈ có PostID vẫn được xử lý, KHÔNG bị REJECT")
    void payloadWithOnlyPostIdIsAccepted() {
        NormalizedProfileData data = NormalizedProfileData.builder()
                .sourceSystem("CRM")
                .eventType("PROFILE_CREATED")
                .postId("PID-001")
                .build();

        assertThat(service.decide(data, List.of()).decision())
                .isEqualTo(MergeDecision.CREATE_NEW_PROFILE);
    }

    @Test
    @DisplayName("Không có candidate → CREATE_NEW_PROFILE, không kèm target")
    void noCandidateCreatesNewProfileWithoutTarget() {
        MergeDecisionResult result = service.decide(data().phone("0912345678").build(), List.of());

        assertThat(result.decision()).isEqualTo(MergeDecision.CREATE_NEW_PROFILE);
        assertThat(result.target()).isNull();
    }

    // =====================================================================
    // Khóa mạnh — một candidate
    // =====================================================================

    @Nested
    @DisplayName("Một candidate")
    class SingleCandidate {

        @Test
        @DisplayName("CCCD khớp → AUTO_MERGE")
        void matchingIdentityNoAutoMerges() {
            MasterProfile candidate = profile(1L, p -> p.setIdentityNo("001234567890"));
            NormalizedProfileData data = data().identityNo("001234567890").build();

            MergeDecisionResult result = service.decide(data, List.of(candidate));

            assertThat(result.decision()).isEqualTo(MergeDecision.AUTO_MERGE);
            assertThat(result.target()).isSameAs(candidate);
        }

        @Test
        @DisplayName("CCCD khớp nhưng ghi khác dạng (có khoảng trắng) vẫn AUTO_MERGE")
        void identityNoMatchIsWhitespaceInsensitive() {
            MasterProfile candidate = profile(1L, p -> p.setIdentityNo("001234567890"));
            NormalizedProfileData data = data().identityNo("001 234 567 890").build();

            assertThat(service.decide(data, List.of(candidate)).decision())
                    .isEqualTo(MergeDecision.AUTO_MERGE);
        }

        @Test
        @DisplayName("CCCD lệch → NEED_REVIEW, kể cả khi mọi tín hiệu khác đều khớp")
        void conflictingIdentityNoNeedsReviewEvenWhenEverythingElseMatches() {
            MasterProfile candidate = profile(1L, p -> {
                p.setIdentityNo("999999999999");
                p.setPhone("0912345678");
                p.setEmail("a@vnpost.vn");
                p.setFullName("Nguyễn Văn A");
            });
            NormalizedProfileData data = data()
                    .identityNo("001234567890")
                    .phone("0912345678")
                    .email("a@vnpost.vn")
                    .fullName("Nguyễn Văn A")
                    .build();

            MergeDecisionResult result = service.decide(data, List.of(candidate));

            assertThat(result.decision()).isEqualTo(MergeDecision.NEED_REVIEW);
            assertThat(result.target()).isSameAs(candidate);
        }

        @Test
        @DisplayName("MST khớp + tên hợp → AUTO_MERGE")
        void matchingTaxCodeWithCompatibleNameAutoMerges() {
            MasterProfile candidate = profile(1L, p -> {
                p.setTaxCode("0101234567");
                p.setFullName("Công ty TNHH ABC");
            });
            NormalizedProfileData data = data()
                    .taxCode("0101234567")
                    .fullName("Cong ty TNHH ABC")
                    .build();

            assertThat(service.decide(data, List.of(candidate)).decision())
                    .isEqualTo(MergeDecision.AUTO_MERGE);
        }

        @Test
        @DisplayName("MST khớp nhưng tên lệch xa → NEED_REVIEW (phòng dùng chung MST công ty)")
        void matchingTaxCodeWithVeryDifferentNameNeedsReview() {
            MasterProfile candidate = profile(1L, p -> {
                p.setTaxCode("0101234567");
                p.setFullName("Nguyễn Văn A");
            });
            NormalizedProfileData data = data()
                    .taxCode("0101234567")
                    .fullName("Trần Thị Bích Ngọc")
                    .build();

            assertThat(service.decide(data, List.of(candidate)).decision())
                    .isEqualTo(MergeDecision.NEED_REVIEW);
        }

        @Test
        @DisplayName("MST lệch → NEED_REVIEW")
        void conflictingTaxCodeNeedsReview() {
            MasterProfile candidate = profile(1L, p -> p.setTaxCode("9999999999"));
            NormalizedProfileData data = data().taxCode("0101234567").build();

            assertThat(service.decide(data, List.of(candidate)).decision())
                    .isEqualTo(MergeDecision.NEED_REVIEW);
        }

        @Test
        @DisplayName("Khớp PostID → AUTO_MERGE")
        void matchingPostIdAutoMerges() {
            MasterProfile candidate = profile(1L, p -> p.setPhone("0900000000"));
            linkRepository.addLink(1L, "POST_ID", "PID-001", (short) 1);
            NormalizedProfileData data = data().postId("PID-001").build();

            assertThat(service.decide(data, List.of(candidate)).decision())
                    .isEqualTo(MergeDecision.AUTO_MERGE);
        }

        @Test
        @DisplayName("Link PostID đã bị vô hiệu (status khác 1) thì KHÔNG tính là khớp")
        void inactivePostIdLinkDoesNotAutoMerge() {
            MasterProfile candidate = profile(1L, p -> p.setPhone("0900000000"));
            linkRepository.addLink(1L, "POST_ID", "PID-001", (short) 3);
            NormalizedProfileData data = data().postId("PID-001").build();

            assertThat(service.decide(data, List.of(candidate)).decision())
                    .isNotEqualTo(MergeDecision.AUTO_MERGE);
        }

        @Test
        @DisplayName("Chỉ trùng SĐT (40đ) → CREATE_NEW_PROFILE, không tự gộp")
        void phoneOnlyMatchDoesNotMerge() {
            // Dưới ngưỡng 70 của luồng ingest: hồ sơ mới được tạo qua đường đầy đủ (có Unomi sync),
            // việc gắn cờ đối soát do detectAndCreateCandidatesForProfile() async đảm nhiệm (sàn 35).
            MasterProfile candidate = profile(1L, p -> p.setPhone("0912345678"));
            NormalizedProfileData data = data().phone("0912345678").build();

            assertThat(service.decide(data, List.of(candidate)).decision())
                    .isEqualTo(MergeDecision.CREATE_NEW_PROFILE);
        }

        @Test
        @DisplayName("SĐT + tên khớp đầy đủ (70đ) → CREATE_MATCH_CANDIDATE, vẫn không tự gộp")
        void phonePlusExactNameCreatesReviewCandidate() {
            MasterProfile candidate = profile(1L, p -> {
                p.setPhone("0912345678");
                p.setFullName("Nguyễn Văn A");
            });
            NormalizedProfileData data = data()
                    .phone("0912345678")
                    .fullName("Nguyễn Văn A")
                    .build();

            MergeDecisionResult result = service.decide(data, List.of(candidate));

            assertThat(result.decision()).isEqualTo(MergeDecision.CREATE_MATCH_CANDIDATE);
            assertThat(result.target()).isSameAs(candidate);
        }

        @Test
        @DisplayName("Đã có link nguồn ACTIVE → AUTO_MERGE, dù CCCD lệch (khách sửa lại CCCD)")
        void existingSourceLinkAutoMergesEvenWithDifferentIdentityNo() {
            MasterProfile candidate = profile(1L, p -> p.setIdentityNo("999999999999"));
            linkRepository.addSourceLink(1L, "CRM", "CRM-001", (short) 1);
            NormalizedProfileData data = data()
                    .sourceCustomerId("CRM-001")
                    .identityNo("001234567890")
                    .build();

            assertThat(service.decide(data, List.of(candidate)).decision())
                    .isEqualTo(MergeDecision.AUTO_MERGE);
        }

        @Test
        @DisplayName("Event PROFILE_ENRICHED khớp SĐT → AUTO_MERGE")
        void enrichmentEventWithPhoneMatchAutoMerges() {
            MasterProfile candidate = profile(1L, p -> p.setPhone("0912345678"));
            NormalizedProfileData data = data()
                    .eventType("PROFILE_ENRICHED")
                    .phone("0912345678")
                    .build();

            assertThat(service.decide(data, List.of(candidate)).decision())
                    .isEqualTo(MergeDecision.AUTO_MERGE);
        }
    }

    // =====================================================================
    // Nhiều candidate — nguyên tắc "khóa mạnh xét trước"
    // =====================================================================

    @Nested
    @DisplayName("Nhiều candidate")
    class MultipleCandidates {

        @Test
        @DisplayName("Đúng một candidate khớp CCCD → chọn nó, KHÔNG trả CONFLICT")
        void oneCandidateMatchingIdentityNoWinsOverPhoneSharers() {
            // Ca này trước đây luôn ra CONFLICT: chuỗi rule khóa mạnh bị bỏ qua hoàn toàn chỉ vì có
            // hồ sơ khác dùng chung SĐT.
            MasterProfile sharesPhoneOnly = profile(1L, p -> p.setPhone("0912345678"));
            MasterProfile matchesIdentity = profile(2L, p -> {
                p.setPhone("0912345678");
                p.setIdentityNo("001234567890");
            });
            NormalizedProfileData data = data()
                    .phone("0912345678")
                    .identityNo("001234567890")
                    .build();

            MergeDecisionResult result = service.decide(data, List.of(sharesPhoneOnly, matchesIdentity));

            assertThat(result.decision()).isEqualTo(MergeDecision.AUTO_MERGE);
            assertThat(result.target()).isSameAs(matchesIdentity);
        }

        @Test
        @DisplayName("Target trả về phải là hồ sơ được chọn, KHÔNG phải candidate đầu danh sách")
        void resultCarriesChosenTargetNotFirstCandidate() {
            // Nếu caller đoán đích bằng candidates.get(0) thì đây chính là ca merge vào SAI hồ sơ.
            MasterProfile first = profile(1L, p -> p.setPhone("0912345678"));
            MasterProfile chosen = profile(2L, p -> {
                p.setPhone("0912345678");
                p.setIdentityNo("001234567890");
            });
            NormalizedProfileData data = data()
                    .phone("0912345678")
                    .identityNo("001234567890")
                    .build();

            MergeDecisionResult result = service.decide(data, List.of(first, chosen));

            assertThat(result.target()).isNotSameAs(first);
            assertThat(result.target().getId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Đúng một candidate khớp PostID → chọn nó")
        void oneCandidateMatchingPostIdIsPicked() {
            MasterProfile sharesPhoneOnly = profile(1L, p -> p.setPhone("0912345678"));
            MasterProfile matchesPostId = profile(2L, p -> p.setPhone("0912345678"));
            linkRepository.addLink(2L, "POST_ID", "PID-001", (short) 1);
            NormalizedProfileData data = data().phone("0912345678").postId("PID-001").build();

            MergeDecisionResult result = service.decide(data, List.of(sharesPhoneOnly, matchesPostId));

            assertThat(result.decision()).isEqualTo(MergeDecision.AUTO_MERGE);
            assertThat(result.target()).isSameAs(matchesPostId);
        }

        @Test
        @DisplayName("Không khóa mạnh nào tách được → CONFLICT, không kèm target")
        void noStrongKeyDiscriminatorYieldsConflict() {
            MasterProfile a = profile(1L, p -> p.setPhone("0912345678"));
            MasterProfile b = profile(2L, p -> p.setPhone("0912345678"));
            NormalizedProfileData data = data().phone("0912345678").build();

            MergeDecisionResult result = service.decide(data, List.of(a, b));

            assertThat(result.decision()).isEqualTo(MergeDecision.CONFLICT);
            assertThat(result.target()).isNull();
        }

        @Test
        @DisplayName("Hai candidate cùng khớp một CCCD → vẫn CONFLICT (xung đột thật)")
        void twoCandidatesSharingSameIdentityNoStillConflict() {
            MasterProfile a = profile(1L, p -> p.setIdentityNo("001234567890"));
            MasterProfile b = profile(2L, p -> p.setIdentityNo("001234567890"));
            NormalizedProfileData data = data().identityNo("001234567890").build();

            assertThat(service.decide(data, List.of(a, b)).decision())
                    .isEqualTo(MergeDecision.CONFLICT);
        }

        @Test
        @DisplayName("CCCD được ưu tiên trước MST khi hai candidate khớp hai khóa khác nhau")
        void identityNoTakesPriorityOverTaxCode() {
            MasterProfile matchesTax = profile(1L, p -> {
                p.setPhone("0912345678");
                p.setTaxCode("0101234567");
            });
            MasterProfile matchesIdentity = profile(2L, p -> {
                p.setPhone("0912345678");
                p.setIdentityNo("001234567890");
            });
            NormalizedProfileData data = data()
                    .phone("0912345678")
                    .identityNo("001234567890")
                    .taxCode("0101234567")
                    .build();

            MergeDecisionResult result = service.decide(data, List.of(matchesTax, matchesIdentity));

            assertThat(result.target()).isSameAs(matchesIdentity);
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private NormalizedProfileData.NormalizedProfileDataBuilder data() {
        return NormalizedProfileData.builder()
                .sourceSystem("CRM")
                .sourceCustomerId("CRM-DEFAULT")
                .eventType("PROFILE_CREATED");
    }

    private MasterProfile profile(Long id, java.util.function.Consumer<MasterProfile> customizer) {
        MasterProfile p = new MasterProfile();
        p.setId(id);
        p.setProfileCode("MP_" + id);
        p.setStatus((short) 1);
        customizer.accept(p);
        return p;
    }

    /**
     * Fake tối thiểu: chỉ hiện thực các method mà ProfileMergeDecisionService thực sự gọi.
     * Các method còn lại của JpaRepository không dùng tới nên ném UnsupportedOperationException
     * — nếu service sau này gọi thêm gì, test sẽ báo ngay chứ không âm thầm trả rỗng.
     */
    private static class FakeIdentityLinkRepository implements ProfileIdentityLinkRepository {

        private final List<ProfileIdentityLink> links = new ArrayList<>();

        void addLink(Long profileId, String type, String value, short status) {
            ProfileIdentityLink l = new ProfileIdentityLink();
            l.setMasterProfileId(profileId);
            l.setIdentityType(type);
            l.setIdentityValue(value);
            l.setStatus(status);
            links.add(l);
        }

        void addSourceLink(Long profileId, String sourceSystem, String sourceCustomerId, short status) {
            ProfileIdentityLink l = new ProfileIdentityLink();
            l.setMasterProfileId(profileId);
            l.setSourceSystem(sourceSystem);
            l.setSourceCustomerId(sourceCustomerId);
            l.setStatus(status);
            links.add(l);
        }

        @Override
        public List<ProfileIdentityLink> findBySourceSystemAndSourceCustomerIdAndStatus(
                String sourceSystem, String sourceCustomerId, Short status) {
            return links.stream()
                    .filter(l -> sourceSystem != null && sourceSystem.equals(l.getSourceSystem()))
                    .filter(l -> sourceCustomerId != null && sourceCustomerId.equals(l.getSourceCustomerId()))
                    .filter(l -> status != null && status.equals(l.getStatus()))
                    .toList();
        }

        @Override
        public List<ProfileIdentityLink> findByMasterProfileIdAndIdentityTypeAndIdentityValue(
                Long masterProfileId, String identityType, String identityValue) {
            return links.stream()
                    .filter(l -> masterProfileId.equals(l.getMasterProfileId()))
                    .filter(l -> identityType.equals(l.getIdentityType()))
                    .filter(l -> identityValue.equals(l.getIdentityValue()))
                    .toList();
        }

        // ── Không dùng trong các test này ──
        @Override public List<ProfileIdentityLink> findByMasterProfileId(Long id) { throw unsupported(); }
        @Override public List<ProfileIdentityLink> findByMasterProfileIdAndStatus(Long id, Short s) { throw unsupported(); }
        @Override public List<ProfileIdentityLink> findBySourceSystemAndSourceCustomerId(String a, String b) { throw unsupported(); }
        @Override public List<ProfileIdentityLink> findByIdentityTypeAndIdentityValue(String a, String b) { throw unsupported(); }
        @Override public void flush() { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink> S saveAndFlush(S e) { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink> List<S> saveAllAndFlush(Iterable<S> e) { throw unsupported(); }
        @Override public void deleteAllInBatch(Iterable<ProfileIdentityLink> e) { throw unsupported(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) { throw unsupported(); }
        @Override public void deleteAllInBatch() { throw unsupported(); }
        @Override public ProfileIdentityLink getOne(Long id) { throw unsupported(); }
        @Override public ProfileIdentityLink getById(Long id) { throw unsupported(); }
        @Override public ProfileIdentityLink getReferenceById(Long id) { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort sort) { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink> List<S> saveAll(Iterable<S> e) { throw unsupported(); }
        @Override public List<ProfileIdentityLink> findAll() { throw unsupported(); }
        @Override public List<ProfileIdentityLink> findAllById(Iterable<Long> ids) { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink> S save(S e) { throw unsupported(); }
        @Override public Optional<ProfileIdentityLink> findById(Long id) { throw unsupported(); }
        @Override public boolean existsById(Long id) { throw unsupported(); }
        @Override public long count() { throw unsupported(); }
        @Override public void deleteById(Long id) { throw unsupported(); }
        @Override public void delete(ProfileIdentityLink e) { throw unsupported(); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) { throw unsupported(); }
        @Override public void deleteAll(Iterable<? extends ProfileIdentityLink> e) { throw unsupported(); }
        @Override public void deleteAll() { throw unsupported(); }
        @Override public List<ProfileIdentityLink> findAll(org.springframework.data.domain.Sort sort) { throw unsupported(); }
        @Override public org.springframework.data.domain.Page<ProfileIdentityLink> findAll(org.springframework.data.domain.Pageable p) { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink> long count(org.springframework.data.domain.Example<S> ex) { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink> boolean exists(org.springframework.data.domain.Example<S> ex) { throw unsupported(); }
        @Override public <S extends ProfileIdentityLink, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> fn) { throw unsupported(); }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Không dùng trong test này");
        }
    }
}
