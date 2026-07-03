package vn.vnpost.cdp.unomi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnomiEventCollectorPayload {
    private String sessionId;
    private List<UnomiEventItem> events;
}
