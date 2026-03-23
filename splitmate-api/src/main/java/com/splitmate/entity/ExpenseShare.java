package com.splitmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "expense_shares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseShare {

    @EmbeddedId
    private ExpenseShareId id;

    @Column(name = "share_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal shareAmount;
}
