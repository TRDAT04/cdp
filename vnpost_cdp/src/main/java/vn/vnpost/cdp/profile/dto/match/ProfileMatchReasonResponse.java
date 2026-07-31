package vn.vnpost.cdp.profile.dto.match;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMatchReasonResponse {

    private Long id;
    private String reasonType;
    /** Nhãn tiếng Việt của reasonType — {@code reasonMessage} đang là tiếng Anh. */
    private String reasonTypeText;
    private String reasonMessage;
    private String leftValue;
    private String rightValue;
    private BigDecimal score;
}
