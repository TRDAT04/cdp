package vn.vnpost.cdp.profile.dto.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ProfileSearchRequest {
    private String keyword;
    private String customerType;
    private Short status;
    private String warningStatus;
    private String sourceSystem;
    private LocalDate fromLastActivityAt;
    private LocalDate toLastActivityAt;
    private int page = 0;
    private int size = 10;
    private String sort;
}
