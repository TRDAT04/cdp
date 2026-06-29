package vn.vnpost.cdp.ingestion.enums;

public enum ConflictResolutionStatus {
    OPEN((short) 0),
    RESOLVED((short) 1),
    REJECTED((short) 2),
    IGNORED((short) 3);

    private final short code;

    ConflictResolutionStatus(short code) { this.code = code; }

    public short getCode() { return code; }
}
