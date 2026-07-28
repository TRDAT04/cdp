package vn.vnpost.example.profile.assembler;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import vn.vnpost.example.customer_event.entity.CustomerEvent;
import vn.vnpost.example.profile.dto.query.ProfileListItemResponse;
import vn.vnpost.example.profile.entity.MasterProfile;
import vn.vnpost.example.profile.entity.ProfileIdentityLink;
import vn.vnpost.example.profile.enums.IdentityType;
import vn.vnpost.example.unomi.dto.UnomiProfileProperties;
import vn.vnpost.example.unomi.dto.UnomiProfileResponse;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Component
public class ProfileListAssembler {

    /** Số dịch vụ chính tối đa hiển thị trên mỗi dòng danh sách. */
    private static final int SERVICE_LINE_LIMIT = 5;

    public Map<String, UnomiProfileResponse> buildUnomiIndex(List<UnomiProfileResponse> unomiProfiles) {
        if (CollectionUtils.isEmpty(unomiProfiles)) {
            return Collections.emptyMap();
        }
        return unomiProfiles.stream()
                .filter(p -> p.getProperties() != null
                        && StringUtils.hasText(p.getProperties().getCdpProfileCode()))
                .collect(Collectors.toMap(
                        p -> p.getProperties().getCdpProfileCode(),
                        Function.identity(),
                        (existing, duplicate) -> existing
                ));
    }


    public ProfileListItemResponse assemble(
            MasterProfile profile,
            UnomiProfileResponse unomiData,
            String[] warning,
            List<String> sourceSystems,
            LocalDateTime lastActivityAt,
            List<CustomerEvent> events,
            List<ProfileIdentityLink> links) {

        ProfileListItemResponse.ProfileListItemResponseBuilder builder = ProfileListItemResponse.builder()
                // ---- Dữ liệu từ PostgreSQL ----
                .id(profile.getId())
                .fullName(profile.getFullName())
                .avatarText(buildAvatarText(profile.getFullName()))
                .profileCode(profile.getProfileCode())
                .phone(profile.getPhone())
                .email(profile.getEmail())
                .customerType(profile.getCustomerType())
                .customerTypeText(mapCustomerTypeText(profile.getCustomerType()))
                .customerTier(profile.getCustomerTier())
                .customerGroup(profile.getCustomerGroup())
                .taxCode(profile.getTaxCode())
                .khlCode(resolveKhlCode(links))
                .warningStatus(warning[0])
                .warningText(warning[1])
                .sourceSystems(sourceSystems)
                .lastActivityAt(lastActivityAt)
                .status(profile.getStatus())
                .statusText(mapStatusText(profile.getStatus()))
                .serviceLines(CustomerEventDerivations.resolveTopServiceLines(events, SERVICE_LINE_LIMIT));

        // ---- Dữ liệu hành vi từ Unomi (graceful degradation nếu null) ----
        if (unomiData != null) {
            builder.segments(
                    CollectionUtils.isEmpty(unomiData.getSegments())
                            ? Collections.emptyList()
                            : unomiData.getSegments());

            UnomiProfileProperties props = unomiData.getProperties();
            if (props != null) {
                builder.firstVisit(props.getFirstVisit())
                        .previousVisit(props.getPreviousVisit())
                        .lastVisit(props.getLastVisit())
                        .nbOfVisits(props.getNbOfVisits())
                        .purchaseCount(props.getPurchaseCount())
                        .totalSpent(props.getTotalSpent())
                        .lastTransactionDate(props.getLastTransactionDate());
            }
        }

        return builder.build();
    }

    // =====================================================================
    // HELPER MAPPERS (giữ nguyên logic hiện tại từ ProfileQueryServiceImpl)
    // =====================================================================

    /** Mã KHL ACTIVE (identity_type = KHL_CODE); null nếu profile không phải KH lớn. */
    private String resolveKhlCode(List<ProfileIdentityLink> links) {
        if (CollectionUtils.isEmpty(links)) return null;
        return links.stream()
                .filter(l -> IdentityType.KHL_CODE.name().equalsIgnoreCase(l.getIdentityType()))
                .filter(l -> l.getStatus() == null || l.getStatus() == 1)
                .map(ProfileIdentityLink::getIdentityValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String buildAvatarText(String fullName) {
        if (!StringUtils.hasText(fullName)) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        String first = parts[0].substring(0, 1).toUpperCase();
        String last  = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + last;
    }

    private String mapCustomerTypeText(String type) {
        return vn.vnpost.example.profile.enums.CustomerType.textOf(type);
    }

    private String mapStatusText(Short status) {
        if (status == null) return null;
        return switch (status) {
            case 1 -> "Hoạt động";
            case 2 -> "Không hoạt động";
            case 3 -> "Đã merge";
            case 4 -> "Bị chặn";
            case 5 -> "Đã xóa";
            default -> String.valueOf(status);
        };
    }
}
