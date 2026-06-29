package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.ProfileSourceSystemCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileSourceSystemResponse;
import vn.vnpost.cdp.profile.dto.ProfileSourceSystemUpdateRequest;

import java.util.List;

public interface ProfileSourceSystemService {
    ProfileSourceSystemResponse create(ProfileSourceSystemCreateRequest request);
    ProfileSourceSystemResponse update(Long id, ProfileSourceSystemUpdateRequest request);
    ProfileSourceSystemResponse getById(Long id);
    ProfileSourceSystemResponse getByCode(String code);
    List<ProfileSourceSystemResponse> listAll();
    ProfileSourceSystemResponse changeStatus(Long id, Short status);
}
