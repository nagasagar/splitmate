package com.splitmate.entity;

import com.splitmate.enums.MemberRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "group_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMember {

    @EmbeddedId
    private GroupMemberId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private Instant joinedAt;
}
