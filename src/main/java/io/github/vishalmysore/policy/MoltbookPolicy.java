package io.github.vishalmysore.policy;

import java.util.Set;

/**
 * Policy enforcement for Moltbook agent actions
 * Defines which actions are allowed vs restricted
 */
public final class MoltbookPolicy {

    // Actions that should NOT be executed autonomously
    private static final Set<String> RESTRICTED_ACTIONS = Set.of(
            "bookCarService",
            "initiateCarPurchase",
            "authorizePayment",
            "scheduleAppointment",
            "confirmOrder",
            "deletePost",
            "createSubmolt",
            "addModerator");

    // Actions that require human confirmation
    private static final Set<String> CONFIRMATION_REQUIRED = Set.of(
            "createMoltbookPost",
            "followAgent",
            "subscribeToSubmolt");

    private MoltbookPolicy() {
        // Utility class - no instantiation
    }

    /**
     * Check if an action is allowed to be executed
     * 
     * @param actionName The name of the action to check
     * @return true if action is allowed, false if restricted
     */
    public static boolean isActionAllowed(String actionName) {
        return !RESTRICTED_ACTIONS.contains(actionName);
    }

    /**
     * Check if an action requires human confirmation
     * 
     * @param actionName The name of the action to check
     * @return true if confirmation is required
     */
    public static boolean requiresConfirmation(String actionName) {
        return CONFIRMATION_REQUIRED.contains(actionName);
    }

    /**
     * Get all restricted actions
     * 
     * @return Set of restricted action names
     */
    public static Set<String> getRestrictedActions() {
        return Set.copyOf(RESTRICTED_ACTIONS);
    }

    /**
     * Get all actions requiring confirmation
     * 
     * @return Set of action names requiring confirmation
     */
    public static Set<String> getConfirmationActions() {
        return Set.copyOf(CONFIRMATION_REQUIRED);
    }

    /**
     * Validate action with reason
     * 
     * @param actionName The action to validate
     * @return Validation result with reason
     */
    public static ValidationResult validate(String actionName) {
        if (!isActionAllowed(actionName)) {
            return new ValidationResult(
                    false,
                    "Action '" + actionName + "' is restricted and cannot be executed autonomously");
        }

        if (requiresConfirmation(actionName)) {
            return new ValidationResult(
                    true,
                    "Action '" + actionName + "' requires human confirmation");
        }

        return new ValidationResult(true, "Action allowed");
    }

    /**
     * Result of policy validation
     */
    public static class ValidationResult {
        public final boolean allowed;
        public final String reason;

        public ValidationResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return String.format("ValidationResult{allowed=%s, reason='%s'}", allowed, reason);
        }
    }
}
