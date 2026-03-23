package com.splitmate.controller;

import com.splitmate.dto.request.CreateGroupRequest;
import com.splitmate.dto.request.InviteRequest;
import com.splitmate.dto.response.BalanceResponse;
import com.splitmate.dto.response.ExpenseResponse;
import com.splitmate.dto.response.GroupResponse;
import com.splitmate.dto.response.InviteResponse;
import com.splitmate.dto.response.MemberResponse;
import com.splitmate.entity.User;
import com.splitmate.service.ExpenseService;
import com.splitmate.service.GroupService;
import com.splitmate.service.SettlementService;
import com.splitmate.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Groups", description = "Group management, invite links, members, balances")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final SettlementService settlementService;
    private final UserService userService;

    // ── group CRUD ───────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all groups the authenticated user belongs to")
    public ResponseEntity<List<GroupResponse>> listGroups(@AuthenticationPrincipal Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.ok(groupService.getUserGroups(user.getSupabaseUserId()));
    }

    @PostMapping
    @Operation(summary = "Create a new group (creator is auto-added as ADMIN)")
    public ResponseEntity<GroupResponse> createGroup(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateGroupRequest req) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.createGroup(user.getSupabaseUserId(), req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get group details")
    public ResponseEntity<GroupResponse> getGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.ok(groupService.getGroup(id, user.getSupabaseUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a group (creator only) — cascades expenses & members")
    public ResponseEntity<Void> deleteGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        User user = userService.getOrCreateUser(jwt);
        groupService.deleteGroup(id, user.getSupabaseUserId());
        return ResponseEntity.noContent().build();
    }

    // ── invite ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/invite")
    @Operation(summary = "Generate (or retrieve) a shareable invite link",
               description = "Optionally supply {\"email\": \"friend@example.com\"} to echo the target email in the response.")
    public ResponseEntity<InviteResponse> invite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid InviteRequest req,
            HttpServletRequest httpReq) {
        User user = userService.getOrCreateUser(jwt);
        String baseUrl = httpReq.getRequestURL().toString()
                .replace(httpReq.getRequestURI(), "");
        return ResponseEntity.ok(
                groupService.generateInviteLink(id, user.getSupabaseUserId(), req, baseUrl));
    }

    @PostMapping("/join/{token}")
    @Operation(summary = "Join a group via invite token (no auth required to allow deep-links)")
    public ResponseEntity<GroupResponse> joinGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID token) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.ok(groupService.joinGroup(token, user.getSupabaseUserId()));
    }

    @DeleteMapping("/{id}/leave")
    @Operation(summary = "Leave a group (creator cannot leave)")
    public ResponseEntity<Void> leaveGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        User user = userService.getOrCreateUser(jwt);
        groupService.leaveGroup(id, user.getSupabaseUserId());
        return ResponseEntity.noContent().build();
    }

    // ── members ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/members")
    @Operation(summary = "List all members of a group")
    public ResponseEntity<List<MemberResponse>> listMembers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.ok(groupService.getMembers(id, user.getSupabaseUserId()));
    }

    // ── expenses (group-scoped) ──────────────────────────────────────────────

    @GetMapping("/{id}/expenses")
    @Operation(summary = "Paginated list of expenses for a group")
    public ResponseEntity<Page<ExpenseResponse>> listExpenses(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.ok(
                expenseService.getGroupExpenses(id, user.getSupabaseUserId(), pageable));
    }

    // ── balances / settlements ────────────────────────────────────────────────

    @GetMapping("/{id}/balances")
    @Operation(summary = "Get simplified owes/owed balances for a group",
               description = "Returns per-user net balances and the minimum set of transfers to settle all debts.")
    public ResponseEntity<BalanceResponse> getBalances(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.ok(
                settlementService.getGroupBalances(id, user.getSupabaseUserId()));
    }
}
