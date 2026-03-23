package com.splitmate.service;

import com.splitmate.dto.request.CreateGroupRequest;
import com.splitmate.dto.request.InviteRequest;
import com.splitmate.dto.response.GroupResponse;
import com.splitmate.dto.response.InviteResponse;
import com.splitmate.dto.response.MemberResponse;
import com.splitmate.entity.Expense;
import com.splitmate.entity.Group;
import com.splitmate.entity.GroupMember;
import com.splitmate.entity.GroupMemberId;
import com.splitmate.entity.User;
import com.splitmate.enums.MemberRole;
import com.splitmate.exception.BusinessException;
import com.splitmate.exception.ForbiddenException;
import com.splitmate.exception.ResourceNotFoundException;
import com.splitmate.repository.ExpenseRepository;
import com.splitmate.repository.ExpenseShareRepository;
import com.splitmate.repository.GroupMemberRepository;
import com.splitmate.repository.GroupRepository;
import com.splitmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;

    // ── queries ─────────────────────────────────────────────────────────────

    public List<GroupResponse> getUserGroups(String userId) {
        return groupRepository.findGroupsByUserId(userId).stream()
                .map(g -> toResponse(g, memberCount(g.getId())))
                .collect(Collectors.toList());
    }

    public GroupResponse getGroup(UUID groupId, String userId) {
        Group group = requireGroup(groupId);
        requireMember(groupId, userId);
        return toResponse(group, memberCount(groupId));
    }

    public List<MemberResponse> getMembers(UUID groupId, String userId) {
        requireMember(groupId, userId);
        return groupMemberRepository.findByIdGroupId(groupId).stream()
                .map(this::toMemberResponse)
                .collect(Collectors.toList());
    }

    // ── mutations ────────────────────────────────────────────────────────────

    @Transactional
    public GroupResponse createGroup(String userId, CreateGroupRequest req) {
        Group group = groupRepository.save(Group.builder()
                .name(req.getName())
                .creatorId(userId)
                .inviteToken(UUID.randomUUID())
                .build());

        groupMemberRepository.save(GroupMember.builder()
                .id(new GroupMemberId(group.getId(), userId))
                .role(MemberRole.ADMIN)
                .build());

        return toResponse(group, 1);
    }

    @Transactional
    public void deleteGroup(UUID groupId, String userId) {
        Group group = requireGroup(groupId);
        if (!group.getCreatorId().equals(userId)) {
            throw new ForbiddenException("Only the group creator can delete this group");
        }

        // Cascade: delete all expense shares → expenses → members → group
        List<Expense> expenses = expenseRepository.findByGroupId(groupId);
        expenses.forEach(e -> expenseShareRepository.deleteByIdExpenseId(e.getId()));
        expenseRepository.deleteAll(expenses);
        groupMemberRepository.deleteByIdGroupId(groupId);
        groupRepository.delete(group);
    }

    @Transactional
    public InviteResponse generateInviteLink(UUID groupId, String userId,
                                              InviteRequest req, String baseUrl) {
        Group group = requireGroup(groupId);
        requireMember(groupId, userId);

        if (group.getInviteToken() == null) {
            group.setInviteToken(UUID.randomUUID());
            group = groupRepository.save(group);
        }

        String link = baseUrl + "/api/groups/join/" + group.getInviteToken();
        return InviteResponse.builder()
                .groupId(group.getId())
                .inviteToken(group.getInviteToken())
                .inviteLink(link)
                .email(req != null ? req.getEmail() : null)
                .build();
    }

    @Transactional
    public GroupResponse joinGroup(UUID inviteToken, String userId) {
        Group group = groupRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired invite token"));

        if (groupMemberRepository.existsByIdGroupIdAndIdUserId(group.getId(), userId)) {
            throw new BusinessException("You are already a member of this group");
        }

        groupMemberRepository.save(GroupMember.builder()
                .id(new GroupMemberId(group.getId(), userId))
                .role(MemberRole.MEMBER)
                .build());

        return toResponse(group, memberCount(group.getId()));
    }

    @Transactional
    public void leaveGroup(UUID groupId, String userId) {
        Group group = requireGroup(groupId);
        requireMember(groupId, userId);

        if (group.getCreatorId().equals(userId)) {
            throw new BusinessException("Group creator cannot leave. Delete the group or transfer ownership first.");
        }

        groupMemberRepository.deleteByIdGroupIdAndIdUserId(groupId, userId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Group requireGroup(UUID id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + id));
    }

    private void requireMember(UUID groupId, String userId) {
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, userId)) {
            throw new ForbiddenException("You are not a member of this group");
        }
    }

    private int memberCount(UUID groupId) {
        return groupMemberRepository.findByIdGroupId(groupId).size();
    }

    private GroupResponse toResponse(Group g, int memberCount) {
        return GroupResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .creatorId(g.getCreatorId())
                .memberCount(memberCount)
                .createdAt(g.getCreatedAt())
                .build();
    }

    private MemberResponse toMemberResponse(GroupMember m) {
        User user = userRepository.findById(m.getId().getUserId()).orElse(null);
        return MemberResponse.builder()
                .userId(m.getId().getUserId())
                .name(user != null ? user.getName() : "Unknown")
                .email(user != null ? user.getEmail() : "")
                .role(m.getRole())
                .joinedAt(m.getJoinedAt())
                .build();
    }
}
