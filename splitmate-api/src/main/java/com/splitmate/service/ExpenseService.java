package com.splitmate.service;

import com.splitmate.dto.request.CreateExpenseRequest;
import com.splitmate.dto.request.ExpenseShareRequest;
import com.splitmate.dto.response.ExpenseResponse;
import com.splitmate.entity.Expense;
import com.splitmate.entity.ExpenseShare;
import com.splitmate.entity.ExpenseShareId;
import com.splitmate.entity.User;
import com.splitmate.enums.SplitType;
import com.splitmate.exception.BusinessException;
import com.splitmate.exception.ForbiddenException;
import com.splitmate.exception.ResourceNotFoundException;
import com.splitmate.repository.ExpenseRepository;
import com.splitmate.repository.ExpenseShareRepository;
import com.splitmate.repository.GroupMemberRepository;
import com.splitmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    // ── queries ─────────────────────────────────────────────────────────────

    public ExpenseResponse getExpense(UUID expenseId, String userId) {
        Expense expense = requireExpense(expenseId);
        requireMember(expense.getGroupId(), userId);
        return toResponse(expense, expenseShareRepository.findByIdExpenseId(expenseId));
    }

    public Page<ExpenseResponse> getGroupExpenses(UUID groupId, String userId, Pageable pageable) {
        requireMember(groupId, userId);
        return expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId, pageable)
                .map(e -> toResponse(e, expenseShareRepository.findByIdExpenseId(e.getId())));
    }

    // ── mutations ────────────────────────────────────────────────────────────

    @Transactional
    public ExpenseResponse createExpense(String userId, CreateExpenseRequest req) {
        requireMember(req.getGroupId(), userId);

        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(req.getGroupId(), req.getPayerId())) {
            throw new BusinessException("Payer '" + req.getPayerId() + "' is not a member of this group");
        }

        Expense expense = expenseRepository.save(Expense.builder()
                .groupId(req.getGroupId())
                .description(req.getDescription())
                .amount(req.getAmount())
                .payerId(req.getPayerId())
                .splitType(req.getSplitType())
                .createdBy(userId)
                .build());

        List<ExpenseShare> shares = computeShares(expense.getId(), req);
        expenseShareRepository.saveAll(shares);

        return toResponse(expense, shares);
    }

    @Transactional
    public ExpenseResponse updateExpense(UUID expenseId, String userId, CreateExpenseRequest req) {
        Expense expense = requireExpense(expenseId);
        requireMember(expense.getGroupId(), userId);

        if (!expense.getCreatedBy().equals(userId) && !expense.getPayerId().equals(userId)) {
            throw new ForbiddenException("Only the expense creator or payer can update this expense");
        }
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(req.getGroupId(), req.getPayerId())) {
            throw new BusinessException("Payer '" + req.getPayerId() + "' is not a member of this group");
        }

        expense.setDescription(req.getDescription());
        expense.setAmount(req.getAmount());
        expense.setPayerId(req.getPayerId());
        expense.setSplitType(req.getSplitType());
        expense.setUpdatedAt(Instant.now());
        expense = expenseRepository.save(expense);

        expenseShareRepository.deleteByIdExpenseId(expenseId);
        List<ExpenseShare> shares = computeShares(expenseId, req);
        expenseShareRepository.saveAll(shares);

        return toResponse(expense, shares);
    }

    @Transactional
    public void deleteExpense(UUID expenseId, String userId) {
        Expense expense = requireExpense(expenseId);
        requireMember(expense.getGroupId(), userId);

        if (!expense.getCreatedBy().equals(userId)) {
            throw new ForbiddenException("Only the expense creator can delete this expense");
        }

        expenseShareRepository.deleteByIdExpenseId(expenseId);
        expenseRepository.delete(expense);
    }

    // ── split calculators ────────────────────────────────────────────────────

    private List<ExpenseShare> computeShares(UUID expenseId, CreateExpenseRequest req) {
        return switch (req.getSplitType()) {
            case EQUAL      -> equalShares(expenseId, req.getShares(), req.getAmount());
            case EXACT      -> exactShares(expenseId, req.getShares(), req.getAmount());
            case PERCENTAGE -> percentageShares(expenseId, req.getShares(), req.getAmount());
            case SHARES     -> ratioShares(expenseId, req.getShares(), req.getAmount());
        };
    }

    /**
     * Divides total evenly; any rounding remainder is added to the first participant.
     */
    private List<ExpenseShare> equalShares(UUID expenseId,
                                            List<ExpenseShareRequest> participants,
                                            BigDecimal total) {
        int n = participants.size();
        if (n == 0) throw new BusinessException("At least one participant is required");

        BigDecimal base      = total.divide(BigDecimal.valueOf(n), 4, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(base.multiply(BigDecimal.valueOf(n)));

        List<ExpenseShare> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal amt = (i == 0) ? base.add(remainder) : base;
            out.add(share(expenseId, participants.get(i).getUserId(), amt));
        }
        return out;
    }

    /**
     * Caller provides the exact amount per person; must sum to total.
     */
    private List<ExpenseShare> exactShares(UUID expenseId,
                                            List<ExpenseShareRequest> reqs,
                                            BigDecimal total) {
        BigDecimal sum = reqs.stream().map(ExpenseShareRequest::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) != 0) {
            throw new BusinessException(
                    "EXACT shares must sum to " + total + " but got " + sum);
        }
        return reqs.stream()
                .map(r -> share(expenseId, r.getUserId(), r.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Caller provides percentage (0-100) per person; must sum to 100.
     * Rounding remainder absorbed by the last entry.
     */
    private List<ExpenseShare> percentageShares(UUID expenseId,
                                                 List<ExpenseShareRequest> reqs,
                                                 BigDecimal total) {
        BigDecimal pctSum = reqs.stream().map(ExpenseShareRequest::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pctSum.compareTo(new BigDecimal("100")) != 0) {
            throw new BusinessException(
                    "PERCENTAGE values must sum to 100 but got " + pctSum);
        }

        List<ExpenseShare> out  = new ArrayList<>();
        BigDecimal assigned     = BigDecimal.ZERO;
        for (int i = 0; i < reqs.size(); i++) {
            BigDecimal amt;
            if (i == reqs.size() - 1) {
                amt = total.subtract(assigned);
            } else {
                amt = total.multiply(reqs.get(i).getValue())
                        .divide(new BigDecimal("100"), 4, RoundingMode.DOWN);
                assigned = assigned.add(amt);
            }
            out.add(share(expenseId, reqs.get(i).getUserId(), amt));
        }
        return out;
    }

    /**
     * Caller provides relative share weights (e.g. 1, 1, 2).
     * Rounding remainder absorbed by the last entry.
     */
    private List<ExpenseShare> ratioShares(UUID expenseId,
                                            List<ExpenseShareRequest> reqs,
                                            BigDecimal total) {
        BigDecimal totalWeight = reqs.stream().map(ExpenseShareRequest::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("Total share weight must be > 0");
        }

        List<ExpenseShare> out = new ArrayList<>();
        BigDecimal assigned    = BigDecimal.ZERO;
        for (int i = 0; i < reqs.size(); i++) {
            BigDecimal amt;
            if (i == reqs.size() - 1) {
                amt = total.subtract(assigned);
            } else {
                amt = total.multiply(reqs.get(i).getValue())
                        .divide(totalWeight, 4, RoundingMode.DOWN);
                assigned = assigned.add(amt);
            }
            out.add(share(expenseId, reqs.get(i).getUserId(), amt));
        }
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ExpenseShare share(UUID expenseId, String userId, BigDecimal amount) {
        return ExpenseShare.builder()
                .id(new ExpenseShareId(expenseId, userId))
                .shareAmount(amount)
                .build();
    }

    private ExpenseResponse toResponse(Expense e, List<ExpenseShare> shares) {
        User payer = userRepository.findById(e.getPayerId()).orElse(null);
        List<ExpenseResponse.ShareDetail> details = shares.stream()
                .map(s -> {
                    User u = userRepository.findById(s.getId().getUserId()).orElse(null);
                    return ExpenseResponse.ShareDetail.builder()
                            .userId(s.getId().getUserId())
                            .userName(u != null ? u.getName() : "Unknown")
                            .shareAmount(s.getShareAmount())
                            .build();
                })
                .collect(Collectors.toList());

        return ExpenseResponse.builder()
                .id(e.getId())
                .groupId(e.getGroupId())
                .description(e.getDescription())
                .amount(e.getAmount())
                .payerId(e.getPayerId())
                .payerName(payer != null ? payer.getName() : "Unknown")
                .splitType(e.getSplitType())
                .shares(details)
                .createdAt(e.getCreatedAt())
                .build();
    }

    private Expense requireExpense(UUID id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id));
    }

    private void requireMember(UUID groupId, String userId) {
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, userId)) {
            throw new ForbiddenException("You are not a member of this group");
        }
    }
}
