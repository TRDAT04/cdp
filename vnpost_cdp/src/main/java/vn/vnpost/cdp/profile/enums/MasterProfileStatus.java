package vn.vnpost.cdp.profile.enums;

public enum MasterProfileStatus {
    ACTIVE(1),
    INACTIVE(2),
    MERGED(3),
    BLOCKED(4),
    DELETED(5);

    private final int value;

    MasterProfileStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
