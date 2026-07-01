package vn.vnpost.cdp.profile.service;

public interface ProfileMergeEngineService {
    boolean shouldOverwrite(
            Long masterProfileId,
            String propertyName,
            String incomingSource);
}
