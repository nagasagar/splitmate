package com.splitmate.enums;

/**
 * How an expense amount is divided among participants.
 *
 * <ul>
 *   <li>EQUAL      – divide total evenly among all participants</li>
 *   <li>EXACT      – caller supplies the exact BigDecimal amount per person (must sum to total)</li>
 *   <li>PERCENTAGE – caller supplies % per person (must sum to 100)</li>
 *   <li>SHARES     – caller supplies a relative share weight per person</li>
 * </ul>
 */
public enum SplitType {
    EQUAL,
    EXACT,
    PERCENTAGE,
    SHARES
}
