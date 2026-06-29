package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.*;

import java.util.List;

public interface ProfileMergeRuleService {

    ProfileMergeRuleResponse create(ProfileMergeRuleCreateRequest request);

    ProfileMergeRuleResponse update(Long id, ProfileMergeRuleUpdateRequest request);

    ProfileMergeRuleResponse getById(Long id);

    List<ProfileMergeRuleResponse> listActive();

    List<ProfileMergeRuleResponse> listByPropertyName(String propertyName);

    ProfileMergeRuleResponse changeStatus(Long id, Short status);

    ProfileMergeRuleTestResponse testRule(ProfileMergeRuleTestRequest request);
}
