package com.splitmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class InviteResponse {
    private UUID groupId;
    private UUID inviteToken;
    private String inviteLink;
    /** Echoed back when the caller supplied an email in the request body. */
    private String email;
}
