package com.splitmate.repository;

import com.splitmate.entity.ExpenseShare;
import com.splitmate.entity.ExpenseShareId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, ExpenseShareId> {

    List<ExpenseShare> findByIdExpenseId(UUID expenseId);

    @Modifying
    @Query("DELETE FROM ExpenseShare es WHERE es.id.expenseId = :expenseId")
    void deleteByIdExpenseId(@Param("expenseId") UUID expenseId);
}
