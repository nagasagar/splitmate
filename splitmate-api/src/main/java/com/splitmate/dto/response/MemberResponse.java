package com.splitmate.dto.response;

import com.splitmate.enums.MemberRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class MemberResponse {
    private String userId;
    private String name;
    private String email;
    private MemberRole role;
    private Instant joinedAt;
}
