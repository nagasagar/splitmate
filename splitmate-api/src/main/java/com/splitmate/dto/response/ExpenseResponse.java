package com.splitmate.dto.response;

import com.splitmate.enums.SplitType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ExpenseResponse {
    private UUID id;
    private UUID groupId;
    private String description;
    private BigDecimal amount;
    private String payerId;
    private String payerName;
    private SplitType splitType;
    private List<ShareDetail> shares;
    private Instant createdAt;

    @Data
    @Builder
    public static class ShareDetail {
        private String userId;
        private String userName;
        private BigDecimal shareAmount;
    }
}
