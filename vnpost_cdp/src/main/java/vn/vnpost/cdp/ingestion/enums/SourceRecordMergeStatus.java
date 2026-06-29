package vn.vnpost.cdp.ingestion.enums;

public enum SourceRecordMergeStatus {
    PENDING((short) 0),
    MERGED((short) 1),
    CONFLICT((short) 2),
    REJECTED((short) 3),
    NEED_REVIEW((short) 4),
    ERROR((short) 5);

    private final short code;

    SourceRecordMergeStatus(short code) { this.code = code; }

    public short getCode() { return code; }
}
