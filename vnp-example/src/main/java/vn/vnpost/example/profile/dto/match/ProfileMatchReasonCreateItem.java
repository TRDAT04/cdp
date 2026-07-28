package vn.vnpost.example.profile.dto.match;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMatchReasonCreateItem {

    private String reasonType;
    private String reasonMessage;
    private String leftValue;
    private String rightValue;
    private BigDecimal score;
}
