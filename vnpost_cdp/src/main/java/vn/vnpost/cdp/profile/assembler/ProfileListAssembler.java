package vn.vnpost.cdp.profile.assembler;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.profile.dto.query.ProfileListItemResponse;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.unomi.dto.UnomiProfileProperties;
import vn.vnpost.cdp.unomi.dto.UnomiProfileResponse;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Component
public class ProfileListAssembler {


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
            LocalDateTime lastActivityAt) {

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
                .warningStatus(warning[0])
                .warningText(warning[1])
                .sourceSystems(sourceSystems)
                .lastActivityAt(lastActivityAt)
                .status(profile.getStatus())
                .statusText(mapStatusText(profile.getStatus()));

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

    private String buildAvatarText(String fullName) {
        if (!StringUtils.hasText(fullName)) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        String first = parts[0].substring(0, 1).toUpperCase();
        String last  = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + last;
    }

    private String mapCustomerTypeText(String type) {
        if (type == null) return null;
        return switch (type.toUpperCase()) {
            case "PERSONAL", "CA_NHAN"       -> "Cá nhân";
            case "FREQUENT", "THUONG_XUYEN"  -> "Thường xuyên";
            case "VIP"                       -> "VIP";
            default                          -> type;
        };
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
