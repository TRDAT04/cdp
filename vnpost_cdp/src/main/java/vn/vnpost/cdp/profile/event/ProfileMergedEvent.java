package vn.vnpost.cdp.profile.event;

import org.springframework.context.ApplicationEvent;
import vn.vnpost.cdp.profile.entity.MasterProfile;

public class ProfileMergedEvent extends ApplicationEvent {

    private final MasterProfile profile;
    private final String syncType; // "CREATE" or "UPDATE"

    public ProfileMergedEvent(Object source, MasterProfile profile, String syncType) {
        super(source);
        this.profile = profile;
        this.syncType = syncType;
    }

    public MasterProfile getProfile() {
        return profile;
    }

    public String getSyncType() {
        return syncType;
    }
}
