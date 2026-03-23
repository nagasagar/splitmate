package com.splitmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class GroupResponse {
    private UUID id;
    private String name;
    private String creatorId;
    private int memberCount;
    private Instant createdAt;
}
