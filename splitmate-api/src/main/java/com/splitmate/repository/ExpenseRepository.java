package com.splitmate.repository;

import com.splitmate.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    /** Paginated list for the group expenses feed. */
    Page<Expense> findByGroupIdOrderByCreatedAtDesc(UUID groupId, Pageable pageable);

    /** Full list used by the settlement/balance calculator. */
    List<Expense> findByGroupId(UUID groupId);
}
