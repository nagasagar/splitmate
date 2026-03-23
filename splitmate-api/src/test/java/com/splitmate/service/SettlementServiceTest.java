package com.splitmate.service;

import com.splitmate.dto.response.BalanceResponse;
import com.splitmate.entity.Expense;
import com.splitmate.entity.ExpenseShare;
import com.splitmate.entity.ExpenseShareId;
import com.splitmate.entity.User;
import com.splitmate.enums.SplitType;
import com.splitmate.repository.ExpenseRepository;
import com.splitmate.repository.ExpenseShareRepository;
import com.splitmate.repository.GroupMemberRepository;
import com.splitmate.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService – debt-simplification algorithm")
class SettlementServiceTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseShareRepository expenseShareRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private SettlementService settlementService;

    private final UUID groupId = UUID.randomUUID();
    private final String alice = "alice-id";
    private final String bob   = "bob-id";
    private final String carol = "carol-id";

    @BeforeEach
    void stubUsers() {
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, alice)).thenReturn(true);
        when(userRepository.findById(alice)).thenReturn(Optional.of(user(alice, "Alice")));
        when(userRepository.findById(bob))  .thenReturn(Optional.of(user(bob,   "Bob")));
        when(userRepository.findById(carol)).thenReturn(Optional.of(user(carol, "Carol")));
    }

    // ── 1 payer, 3-way equal split ───────────────────────────────────────────

    @Test
    @DisplayName("Alice pays $30 split equally → Bob and Carol each owe Alice $10")
    void equalSplitThreePeople() {
        UUID expId = UUID.randomUUID();
        when(expenseRepository.findByGroupId(groupId))
                .thenReturn(List.of(expense(expId, alice, "30.00")));
        when(expenseShareRepository.findByIdExpenseId(expId))
                .thenReturn(equalShares(expId, "10.00", alice, bob, carol));

        BalanceResponse r = settlementService.getGroupBalances(groupId, alice);

        Map<String, BigDecimal> bal = balanceMap(r);
        assertThat(bal.get(alice)).isEqualByComparingTo("20.00");
        assertThat(bal.get(bob))  .isEqualByComparingTo("-10.00");
        assertThat(bal.get(carol)).isEqualByComparingTo("-10.00");

        assertThat(r.getSettlements()).hasSize(2);
        r.getSettlements().forEach(s -> {
            assertThat(s.getToUserId()).isEqualTo(alice);
            assertThat(s.getAmount()).isEqualByComparingTo("10.00");
        });
    }

    // ── 2 payers → net simplification ───────────────────────────────────────

    @Test
    @DisplayName("Alice pays $30, Bob pays $30 (both 3-way equal) → Carol owes $20 in 2 transfers")
    void debtSimplificationTwoPayers() {
        UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID();

        when(expenseRepository.findByGroupId(groupId))
                .thenReturn(List.of(expense(e1, alice, "30.00"), expense(e2, bob, "30.00")));
        when(expenseShareRepository.findByIdExpenseId(e1))
                .thenReturn(equalShares(e1, "10.00", alice, bob, carol));
        when(expenseShareRepository.findByIdExpenseId(e2))
                .thenReturn(equalShares(e2, "10.00", alice, bob, carol));

        BalanceResponse r = settlementService.getGroupBalances(groupId, alice);

        Map<String, BigDecimal> bal = balanceMap(r);
        assertThat(bal.get(alice)).isEqualByComparingTo("10.00");
        assertThat(bal.get(bob))  .isEqualByComparingTo("10.00");
        assertThat(bal.get(carol)).isEqualByComparingTo("-20.00");

        // Minimum 2 transfers: Carol → Alice $10, Carol → Bob $10
        assertThat(r.getSettlements()).hasSize(2);
        BigDecimal totalTransferred = r.getSettlements().stream()
                .map(BalanceResponse.Settlement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalTransferred).isEqualByComparingTo("20.00");
        r.getSettlements().forEach(s -> assertThat(s.getFromUserId()).isEqualTo(carol));
    }

    // ── no expenses ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Group with no expenses → empty balances and no settlements")
    void noExpenses() {
        when(expenseRepository.findByGroupId(groupId)).thenReturn(Collections.emptyList());

        BalanceResponse r = settlementService.getGroupBalances(groupId, alice);

        assertThat(r.getUserBalances()).isEmpty();
        assertThat(r.getSettlements()) .isEmpty();
    }

    // ── all-settled ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Alice pays $30 and Bob pays $30 for different 2-person splits → net zero, no settlements")
    void alreadySettled() {
        UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID();

        // Alice pays $30 for Alice+Bob equally
        when(expenseRepository.findByGroupId(groupId))
                .thenReturn(List.of(expense(e1, alice, "30.00"), expense(e2, bob, "30.00")));
        when(expenseShareRepository.findByIdExpenseId(e1))
                .thenReturn(equalShares(e1, "15.00", alice, bob));
        when(expenseShareRepository.findByIdExpenseId(e2))
                .thenReturn(equalShares(e2, "15.00", alice, bob));

        BalanceResponse r = settlementService.getGroupBalances(groupId, alice);

        Map<String, BigDecimal> bal = balanceMap(r);
        assertThat(bal.get(alice)).isEqualByComparingTo("0.00");
        assertThat(bal.get(bob))  .isEqualByComparingTo("0.00");
        assertThat(r.getSettlements()).isEmpty();
    }

    // ── simplifyDebts unit test (package-private method) ────────────────────

    @Test
    @DisplayName("simplifyDebts: chain A→B→C→A can be fully simplified")
    void simplifyDebtsChain() {
        // A owes B $10, B owes C $10, C owes A $10 → net zero, no transfers needed
        Map<String, BigDecimal> net = new HashMap<>();
        net.put("A", new BigDecimal("0"));
        net.put("B", new BigDecimal("0"));
        net.put("C", new BigDecimal("0"));
        Map<String, User> cache = Map.of(
                "A", user("A", "Alice"),
                "B", user("B", "Bob"),
                "C", user("C", "Carol"));

        List<BalanceResponse.Settlement> s = settlementService.simplifyDebts(net, cache);
        assertThat(s).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User user(String id, String name) {
        return User.builder().supabaseUserId(id).name(name).email(id + "@test.com").build();
    }

    private Expense expense(UUID id, String payerId, String amount) {
        return Expense.builder()
                .id(id).groupId(groupId)
                .description("Test").amount(new BigDecimal(amount))
                .payerId(payerId).splitType(SplitType.EQUAL).createdBy(payerId)
                .build();
    }

    private List<ExpenseShare> equalShares(UUID expId, String amount, String... userIds) {
        List<ExpenseShare> out = new ArrayList<>();
        for (String uid : userIds) {
            out.add(ExpenseShare.builder()
                    .id(new ExpenseShareId(expId, uid))
                    .shareAmount(new BigDecimal(amount))
                    .build());
        }
        return out;
    }

    private Map<String, BigDecimal> balanceMap(BalanceResponse r) {
        Map<String, BigDecimal> m = new HashMap<>();
        r.getUserBalances().forEach(b -> m.put(b.getUserId(), b.getNetBalance()));
        return m;
    }
}
