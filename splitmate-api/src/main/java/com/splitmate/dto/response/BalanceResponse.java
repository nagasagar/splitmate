package com.splitmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Balance summary for a group.
 *
 * <p>{@code userBalances} shows the net position of every member:<br>
 *   positive = they are owed money, negative = they owe money.
 *
 * <p>{@code settlements} is the <em>minimum</em> set of transfers that clears
 *   all debts (greedy debt-simplification algorithm).
 */
@Data
@Builder
public class BalanceResponse {

    private List<UserBalance> userBalances;
    private List<Settlement> settlements;

    @Data
    @Builder
    public static class UserBalance {
        private String userId;
        private String userName;
        /** Positive = owed money by others. Negative = owes money to others. */
        private BigDecimal netBalance;
    }

    @Data
    @Builder
    public static class Settlement {
        private String fromUserId;
        private String fromUserName;
        private String toUserId;
        private String toUserName;
        private BigDecimal amount;
    }
}
