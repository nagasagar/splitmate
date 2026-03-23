package com.splitmate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ExpenseShareId implements Serializable {

    @Column(name = "expense_id")
    private UUID expenseId;

    @Column(name = "user_id")
    private String userId;
}
