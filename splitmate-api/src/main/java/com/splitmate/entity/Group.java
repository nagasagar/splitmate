package com.splitmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "expense_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "creator_id", nullable = false)
    private String creatorId;

    /** Token used in shareable invite links. Regenerated on request. */
    @Column(name = "invite_token", unique = true)
    private UUID inviteToken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
