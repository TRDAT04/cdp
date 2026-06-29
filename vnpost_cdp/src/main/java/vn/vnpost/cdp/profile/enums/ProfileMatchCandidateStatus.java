package vn.vnpost.cdp.profile.enums;

public enum ProfileMatchCandidateStatus {
    PENDING(0),
    MERGED(1),
    IGNORED(2),
    REJECTED(3),
    EXPIRED(4);

    private final int value;

    ProfileMatchCandidateStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ProfileMatchCandidateStatus fromValue(int value) {
        for (ProfileMatchCandidateStatus s : values()) {
            if (s.value == value) return s;
        }
        throw new IllegalArgumentException("Unknown ProfileMatchCandidateStatus value: " + value);
    }
}
