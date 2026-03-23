package com.splitmate.service;

import com.splitmate.dto.response.BalanceResponse;
import com.splitmate.entity.Expense;
import com.splitmate.entity.ExpenseShare;
import com.splitmate.entity.User;
import com.splitmate.exception.ForbiddenException;
import com.splitmate.repository.ExpenseRepository;
import com.splitmate.repository.ExpenseShareRepository;
import com.splitmate.repository.GroupMemberRepository;
import com.splitmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes per-user net balances and produces the minimum set of transactions
 * needed to settle all debts within a group (greedy debt-simplification).
 *
 * <p><b>Balance formula per user U:</b>
 * <pre>
 *   netBalance[U] = Σ expense.amount  where expense.payerId == U   (what U paid for everyone)
 *                 − Σ share.shareAmount where share.userId == U    (what U owes)
 * </pre>
 * Positive → U is owed money.  Negative → U owes money.
 *
 * <p><b>Debt-simplification algorithm:</b>
 * <ol>
 *   <li>Separate users into creditors (+) and debtors (−).</li>
 *   <li>Greedily pair the largest debtor with the largest creditor.</li>
 *   <li>The transfer amount = min(|debt|, credit).</li>
 *   <li>Repeat until all balances are within a 1-cent threshold.</li>
 * </ol>
 * This minimises the total number of transfers (optimal in practice for small groups).
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final BigDecimal THRESHOLD = new BigDecimal("0.01");

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public BalanceResponse getGroupBalances(UUID groupId, String userId) {
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, userId)) {
            throw new ForbiddenException("You are not a member of this group");
        }

        // 1. Accumulate net balances
        Map<String, BigDecimal> netBalance = new LinkedHashMap<>();

        for (Expense expense : expenseRepository.findByGroupId(groupId)) {
            // Payer is credited the full amount
            netBalance.merge(expense.getPayerId(), expense.getAmount(), BigDecimal::add);

            // Each participant is debited their share
            for (ExpenseShare share : expenseShareRepository.findByIdExpenseId(expense.getId())) {
                netBalance.merge(share.getId().getUserId(),
                        share.getShareAmount().negate(), BigDecimal::add);
            }
        }

        // 2. Resolve user names
        Map<String, User> userCache = resolveUsers(netBalance.keySet());

        // 3. Build UserBalance list
        List<BalanceResponse.UserBalance> userBalances = netBalance.entrySet().stream()
                .map(e -> BalanceResponse.UserBalance.builder()
                        .userId(e.getKey())
                        .userName(nameOf(e.getKey(), userCache))
                        .netBalance(e.getValue().setScale(2, RoundingMode.HALF_UP))
                        .build())
                .collect(Collectors.toList());

        // 4. Simplify debts
        List<BalanceResponse.Settlement> settlements = simplifyDebts(netBalance, userCache);

        return BalanceResponse.builder()
                .userBalances(userBalances)
                .settlements(settlements)
                .build();
    }

    // ── debt-simplification ──────────────────────────────────────────────────

    /**
     * Greedy O(n²) simplification. For typical group sizes (≤ 50 members) this is
     * effectively instant and produces the minimum number of transfers.
     */
    List<BalanceResponse.Settlement> simplifyDebts(Map<String, BigDecimal> netBalance,
                                                    Map<String, User> userCache) {
        // Work on a mutable copy so the original balances are not modified
        Map<String, BigDecimal> bal = new HashMap<>(netBalance);
        List<BalanceResponse.Settlement> settlements = new ArrayList<>();

        while (true) {
            // Find the user with the maximum credit and the maximum debt
            String creditor = null;
            BigDecimal maxCredit = THRESHOLD;
            String debtor = null;
            BigDecimal maxDebt = THRESHOLD;

            for (Map.Entry<String, BigDecimal> entry : bal.entrySet()) {
                BigDecimal v = entry.getValue();
                if (v.compareTo(maxCredit) > 0) {
                    maxCredit = v;
                    creditor  = entry.getKey();
                }
                if (v.negate().compareTo(maxDebt) > 0) {
                    maxDebt = v.negate();
                    debtor  = entry.getKey();
                }
            }

            if (creditor == null || debtor == null) break; // all settled

            BigDecimal transfer = maxCredit.min(maxDebt).setScale(2, RoundingMode.HALF_UP);

            settlements.add(BalanceResponse.Settlement.builder()
                    .fromUserId(debtor)
                    .fromUserName(nameOf(debtor, userCache))
                    .toUserId(creditor)
                    .toUserName(nameOf(creditor, userCache))
                    .amount(transfer)
                    .build());

            bal.merge(creditor, transfer.negate(), BigDecimal::add);
            bal.merge(debtor,   transfer,           BigDecimal::add);
        }

        return settlements;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, User> resolveUsers(Set<String> userIds) {
        Map<String, User> cache = new HashMap<>();
        userIds.forEach(id -> userRepository.findById(id).ifPresent(u -> cache.put(id, u)));
        return cache;
    }

    private String nameOf(String userId, Map<String, User> cache) {
        User u = cache.get(userId);
        return u != null ? u.getName() : "Unknown";
    }
}
