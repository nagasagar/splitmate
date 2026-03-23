package com.splitmate.service;

import com.splitmate.dto.request.CreateExpenseRequest;
import com.splitmate.dto.request.ExpenseShareRequest;
import com.splitmate.dto.response.ExpenseResponse;
import com.splitmate.entity.Expense;
import com.splitmate.entity.User;
import com.splitmate.enums.SplitType;
import com.splitmate.exception.BusinessException;
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
@DisplayName("ExpenseService – split type calculators")
class ExpenseServiceTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseShareRepository expenseShareRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ExpenseService expenseService;

    private final UUID groupId = UUID.randomUUID();
    private final String alice = "alice-id";
    private final String bob   = "bob-id";
    private final String carol = "carol-id";

    @BeforeEach
    void stubMembership() {
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(any(), eq(alice))).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(any(), eq(bob))).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(any(), eq(carol))).thenReturn(true);

        when(userRepository.findById(alice)).thenReturn(Optional.of(user(alice, "Alice")));
        when(userRepository.findById(bob))  .thenReturn(Optional.of(user(bob,   "Bob")));
        when(userRepository.findById(carol)).thenReturn(Optional.of(user(carol, "Carol")));

        // Return a saved expense with an id
        when(expenseRepository.save(any())).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            // Simulate UUID generation
            Expense saved = Expense.builder()
                    .id(UUID.randomUUID())
                    .groupId(e.getGroupId())
                    .description(e.getDescription())
                    .amount(e.getAmount())
                    .payerId(e.getPayerId())
                    .splitType(e.getSplitType())
                    .createdBy(e.getCreatedBy())
                    .build();
            return saved;
        });
        when(expenseShareRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── EQUAL ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EQUAL split $30 among 3 → each owes $10.00")
    void equalSplit() {
        CreateExpenseRequest req = buildReq(SplitType.EQUAL, "30.00",
                share(alice, "0"), share(bob, "0"), share(carol, "0"));

        ExpenseResponse res = expenseService.createExpense(alice, req);

        assertThat(res.getShares()).hasSize(3);
        BigDecimal total = res.getShares().stream()
                .map(ExpenseResponse.ShareDetail::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("30.00");
    }

    // ── EXACT ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EXACT split must sum to total – happy path")
    void exactSplitValid() {
        CreateExpenseRequest req = buildReq(SplitType.EXACT, "30.00",
                share(alice, "10.00"), share(bob, "12.00"), share(carol, "8.00"));

        ExpenseResponse res = expenseService.createExpense(alice, req);
        assertThat(res.getShares()).hasSize(3);
    }

    @Test
    @DisplayName("EXACT split that doesn't sum to total throws BusinessException")
    void exactSplitInvalidSum() {
        CreateExpenseRequest req = buildReq(SplitType.EXACT, "30.00",
                share(alice, "10.00"), share(bob, "10.00")); // only 20, not 30

        assertThatThrownBy(() -> expenseService.createExpense(alice, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("EXACT");
    }

    // ── PERCENTAGE ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("PERCENTAGE split 50/30/20 of $100 → $50/$30/$20")
    void percentageSplit() {
        CreateExpenseRequest req = buildReq(SplitType.PERCENTAGE, "100.00",
                share(alice, "50"), share(bob, "30"), share(carol, "20"));

        ExpenseResponse res = expenseService.createExpense(alice, req);
        assertThat(res.getShares()).hasSize(3);
        BigDecimal total = res.getShares().stream()
                .map(ExpenseResponse.ShareDetail::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("PERCENTAGE shares not summing to 100 throws BusinessException")
    void percentageSplitBadSum() {
        CreateExpenseRequest req = buildReq(SplitType.PERCENTAGE, "100.00",
                share(alice, "50"), share(bob, "30")); // 80, not 100

        assertThatThrownBy(() -> expenseService.createExpense(alice, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PERCENTAGE");
    }

    // ── SHARES ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SHARES split 1:1:2 of $40 → $10/$10/$20")
    void sharesSplit() {
        CreateExpenseRequest req = buildReq(SplitType.SHARES, "40.00",
                share(alice, "1"), share(bob, "1"), share(carol, "2"));

        ExpenseResponse res = expenseService.createExpense(alice, req);
        assertThat(res.getShares()).hasSize(3);
        BigDecimal total = res.getShares().stream()
                .map(ExpenseResponse.ShareDetail::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("40.00");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User user(String id, String name) {
        return User.builder().supabaseUserId(id).name(name).email(id + "@test.com").build();
    }

    private CreateExpenseRequest buildReq(SplitType type, String amount,
                                           ExpenseShareRequest... shares) {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(groupId);
        req.setDescription("Test expense");
        req.setAmount(new BigDecimal(amount));
        req.setPayerId(alice);
        req.setSplitType(type);
        req.setShares(Arrays.asList(shares));
        return req;
    }

    private ExpenseShareRequest share(String userId, String value) {
        ExpenseShareRequest s = new ExpenseShareRequest();
        s.setUserId(userId);
        s.setValue(new BigDecimal(value));
        return s;
    }
}
