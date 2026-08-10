package vn.vnpost.cdp.common.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RequestContext {
    private final ThreadLocal<String> requestIdHolder = new ThreadLocal<>();

    public void setRequestId(String requestId) {
        requestIdHolder.set(requestId);
    }

    public String getRequestId() {
        return requestIdHolder.get();
    }

    public static void clear() {
        requestIdHolder.remove();
    }

}

