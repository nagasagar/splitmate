package com.splitmate.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExpenseShareRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    /**
     * Meaning depends on the parent expense's splitType:
     * <ul>
     *   <li>EQUAL      – value is ignored (participants are still listed here)</li>
     *   <li>EXACT      – exact amount this user owes</li>
     *   <li>PERCENTAGE – percentage (0-100) this user owes</li>
     *   <li>SHARES     – relative share weight (e.g. 2 = double share)</li>
     * </ul>
     */
    @NotNull(message = "value is required")
    @DecimalMin(value = "0", message = "value must be >= 0")
    private BigDecimal value;
}
