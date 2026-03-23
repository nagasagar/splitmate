package com.splitmate.repository;

import com.splitmate.entity.GroupMember;
import com.splitmate.entity.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    List<GroupMember> findByIdGroupId(UUID groupId);

    boolean existsByIdGroupIdAndIdUserId(UUID groupId, String userId);

    @Modifying
    @Query("DELETE FROM GroupMember gm WHERE gm.id.groupId = :groupId AND gm.id.userId = :userId")
    void deleteByIdGroupIdAndIdUserId(@Param("groupId") UUID groupId, @Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM GroupMember gm WHERE gm.id.groupId = :groupId")
    void deleteByIdGroupId(@Param("groupId") UUID groupId);
}
