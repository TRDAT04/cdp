package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDetailResponse {
    // Core profile fields
    private Long id;
    private String profileCode;
    private String fullName;
    private String phone;
    private String email;
    private String identityNo;
    private String gender;
    private LocalDate dateOfBirth;
    private String customerType;
    private String customerTypeText;
    private String provinceCode;
    private String provinceName;
    private String unitCode;
    private String unitName;
    private Short status;
    private String statusText;
    private Long mergedIntoProfileId;
    private LocalDateTime lastMergedAt;
    private LocalDateTime syncedToUnomiAt;
    private LocalDateTime created;
    private LocalDateTime modified;

    // Computed fields
    private String warningStatus;
    private String warningText;
    private List<String> sourceSystems;
    private LocalDateTime lastActivityAt;

    // Related data
    private List<ProfileIdentityLinkDetailResponse> identityLinks;
    private List<ProfileAttributeValueDetailResponse> attributeValues;
    private List<ProfileSourceRecordDetailResponse> sourceRecords;
    private List<ProfileConflictResponse> openConflicts;
    private List<ProfileMatchCandidateSummaryResponse> matchCandidates;
    private List<ProfileChangeLogResponse> changeLogs;
    private ProfileUnomiSyncLogDetailResponse latestUnomiSync;
}
