package vn.vnpost.cdp.ingestion.enums;

public enum ProfileSourceSystemCode {
    CRM, CMS, PORTAL, MYVNPOST, POSTID, CAS;
    ;

    public static boolean isValid(String code) {
        if (code == null) return false;
        for (ProfileSourceSystemCode v : values()) {
            if (v.name().equalsIgnoreCase(code)) return true;
        }
        return false;
    }
}
