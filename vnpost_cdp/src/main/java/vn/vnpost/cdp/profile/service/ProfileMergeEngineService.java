package vn.vnpost.cdp.profile.service;

import java.time.LocalDateTime;

public interface ProfileMergeEngineService {
    boolean shouldOverwrite(
            Long masterProfileId,
            String propertyName,
            String incomingSource,
            LocalDateTime incomingReceivedAt);
}
