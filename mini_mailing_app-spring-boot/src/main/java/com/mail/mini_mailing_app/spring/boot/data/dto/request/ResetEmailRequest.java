package com.mail.mini_mailing_app.spring.boot.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static com.mail.mini_mailing_app.spring.boot.utilities.MailAppUtils.EMAIL_REGEX_STRING;
import static com.mail.mini_mailing_app.spring.boot.utilities.MailAppUtils.PHONE_NUMBER_REGEX;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ResetEmailRequest {

    @NotNull(message = "field phone number cannot be null")
    @NotBlank(message = "field phone number cannot be blank")
    @NotEmpty(message = "field phone number cannot be empty")
    @Pattern(message = "Enter your registered phone number", regexp = PHONE_NUMBER_REGEX)
    private String phoneNumber;

    @NotNull(message = "field email cannot be null")
    @NotBlank(message = "field email cannot be blank")
    @NotEmpty(message = "field email cannot be empty")
    @Pattern(message = "Invalid email format", regexp = EMAIL_REGEX_STRING)
    private String email;
}
