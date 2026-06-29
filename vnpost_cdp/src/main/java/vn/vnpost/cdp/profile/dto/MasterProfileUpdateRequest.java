package vn.vnpost.cdp.profile.dto;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MasterProfileUpdateRequest {

    private String fullName;

    private String phone;

    @Email(message = "email must be a valid email address")
    private String email;

    private String gender;

    private LocalDate dateOfBirth;

    private String identityNo;

    private String customerType;

    private String provinceCode;

    private String provinceName;

    private String unitCode;

    private String unitName;
}
