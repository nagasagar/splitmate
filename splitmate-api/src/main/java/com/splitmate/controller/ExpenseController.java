package com.splitmate.controller;

import com.splitmate.dto.request.CreateExpenseRequest;
import com.splitmate.dto.response.ExpenseResponse;
import com.splitmate.entity.User;
import com.splitmate.service.ExpenseService;
import com.splitmate.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Expense CRUD — use POST /api/groups/{id}/expenses for the group feed")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create a new expense",
               description = "groupId, payerId, splitType and shares are required. " +
                             "For EQUAL split the 'value' field on each share entry is ignored — " +
                             "just list the participant userIds.")
    public ResponseEntity<ExpenseResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateExpenseRequest req) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.createExpense(user.getSupabaseUserId(), req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an expense by ID")
    public ResponseEntity<ExpenseResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.ok(expenseService.getExpense(id, user.getSupabaseUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an expense (creator or payer only)")
    public ResponseEntity<ExpenseResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CreateExpenseRequest req) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.ok(expenseService.updateExpense(id, user.getSupabaseUserId(), req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an expense (creator only)")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        User user = userService.getOrCreateUser(jwt);
        expenseService.deleteExpense(id, user.getSupabaseUserId());
        return ResponseEntity.noContent().build();
    }
}
