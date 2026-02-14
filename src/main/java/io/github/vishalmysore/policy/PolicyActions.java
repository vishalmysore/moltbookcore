package io.github.vishalmysore.policy;

import com.t4a.annotations.Action;
import com.t4a.annotations.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Policy enforcement actions for the AI agent
 * Allows the agent to check its own action permissions
 */
@Agent(groupName = "PolicyAgent", groupDescription = "AI agent that enforces action policies and safety constraints")
@Component
@Slf4j
public class PolicyActions {

    @Action(description = "Check if a specific action is allowed by the policy. Returns whether the action can be executed autonomously or requires restrictions")
    public String checkActionPolicy(String actionName) {
        log.info("Checking policy for action: {}", actionName);

        MoltbookPolicy.ValidationResult result = MoltbookPolicy.validate(actionName);

        if (!result.allowed) {
            return "❌ Action RESTRICTED: " + result.reason + "\n" +
                    "This action cannot be executed autonomously for safety reasons.";
        }

        if (MoltbookPolicy.requiresConfirmation(actionName)) {
            return "⚠️ CONFIRMATION REQUIRED: " + result.reason + "\n" +
                    "This action can be executed but requires human confirmation first.";
        }

        return "✅ Action ALLOWED: " + result.reason;
    }

    @Action(description = "List all restricted actions that cannot be executed autonomously")
    public String listRestrictedActions() {
        log.info("Listing all restricted actions");

        StringBuilder sb = new StringBuilder();
        sb.append("🚫 RESTRICTED ACTIONS (Cannot Execute):\n\n");

        for (String action : MoltbookPolicy.getRestrictedActions()) {
            sb.append("  • ").append(action).append("\n");
        }

        sb.append("\nThese actions are blocked for safety and require human intervention.");

        return sb.toString();
    }

    @Action(description = "List all actions that require human confirmation before execution")
    public String listConfirmationActions() {
        log.info("Listing all actions requiring confirmation");

        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ ACTIONS REQUIRING CONFIRMATION:\n\n");

        for (String action : MoltbookPolicy.getConfirmationActions()) {
            sb.append("  • ").append(action).append("\n");
        }

        sb.append("\nThese actions can be executed but need human approval first.");

        return sb.toString();
    }

    @Action(description = "Explain the policy system and why certain actions are restricted")
    public String explainPolicySystem() {
        return "🛡️ MOLTBOOK AGENT POLICY SYSTEM\n\n" +
                "This agent follows a safety-first policy framework:\n\n" +
                "1. ALLOWED ACTIONS:\n" +
                "   - Reading information (car details, feed, search)\n" +
                "   - Providing recommendations\n" +
                "   - Answering questions\n\n" +
                "2. CONFIRMATION REQUIRED:\n" +
                "   - Posting to Moltbook\n" +
                "   - Following other agents\n" +
                "   - Subscribing to communities\n\n" +
                "3. RESTRICTED (BLOCKED):\n" +
                "   - Financial transactions (bookings, purchases)\n" +
                "   - Scheduling real appointments\n" +
                "   - Deleting content\n" +
                "   - Administrative actions\n\n" +
                "Why? To ensure:\n" +
                "✓ Human oversight for impactful decisions\n" +
                "✓ No autonomous financial commitments\n" +
                "✓ Safe and responsible AI behavior\n" +
                "✓ Compliance with Moltbook community guidelines";
    }
}
