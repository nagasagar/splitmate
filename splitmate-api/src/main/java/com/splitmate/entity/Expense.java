package com.splitmate.entity;

import com.splitmate.enums.SplitType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** The single primary payer of this expense. */
    @Column(name = "payer_id", nullable = false)
    private String payerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false)
    private SplitType splitType;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
