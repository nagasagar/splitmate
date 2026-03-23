package com.splitmate.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class InviteRequest {

    /** Optional – when provided the link response echoes the email for client-side email delivery. */
    @Email(message = "Must be a valid email address")
    private String email;
}
