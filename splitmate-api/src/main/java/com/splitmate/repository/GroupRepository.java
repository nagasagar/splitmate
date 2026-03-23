package com.splitmate.repository;

import com.splitmate.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    /**
     * Returns all groups where the user is a member (via GroupMember join table).
     */
    @Query("""
        SELECT g FROM Group g
        WHERE g.id IN (
            SELECT gm.id.groupId FROM GroupMember gm WHERE gm.id.userId = :userId
        )
        ORDER BY g.createdAt DESC
        """)
    List<Group> findGroupsByUserId(@Param("userId") String userId);

    Optional<Group> findByInviteToken(UUID inviteToken);
}
