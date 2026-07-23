package vn.vnpost.cdp.profile.dto.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ProfileConsentResponse {

    private List<ConsentRow> consentMatrix;

    private String collectionSource;

    private LocalDateTime lastUpdated;

    private String termsVersion;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class ConsentRow {
        private String purpose;
        private String purposeLabel;
        private Map<String, String> channels;
    }
}
