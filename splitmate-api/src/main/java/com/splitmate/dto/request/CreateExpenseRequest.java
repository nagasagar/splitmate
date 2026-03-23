package com.splitmate.dto.request;

import com.splitmate.enums.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateExpenseRequest {

    @NotNull(message = "groupId is required")
    private UUID groupId;

    @NotBlank(message = "description is required")
    @Size(max = 255)
    private String description;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "payerId is required")
    private String payerId;

    @NotNull(message = "splitType is required")
    private SplitType splitType;

    @NotEmpty(message = "shares must contain at least one entry")
    @Valid
    private List<ExpenseShareRequest> shares;
}
