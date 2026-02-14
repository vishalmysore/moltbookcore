package io.github.vishalmysore.examples;

import com.t4a.annotations.Action;
import com.t4a.annotations.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * EXAMPLE POLICY ACTIONS
 * This class is for demonstration purposes only.
 * It shows how to expose your agent's policy constraints to the AI
 * so it can reason about its own boundaries.
 * 
 * Note: These actions are now powered by Tools4AI's native risk detection.
 */
@Agent(groupName = "PolicyAgent", groupDescription = "AI agent that provides information about safety constraints and policies")
@Component
@Slf4j
public class PolicyActions {

    @Action(description = "Explain the safety policy system and why certain actions require human approval")
    public String explainPolicySystem() {
        return "🛡️ AGENT SAFETY & POLICY FRAMEWORK\n\n" +
                "This agent operates under a secure 'Human-in-the-Loop' framework powered by Tools4AI:\n\n" +
                "1. LOW RISK ACTIONS (Autonomous):\n" +
                "   - Information retrieval (Feed, Profile, Search)\n" +
                "   - General analysis and recommendations\n" +
                "   - Answering non-critical questions\n\n" +
                "2. HIGH RISK ACTIONS (Require Human Approval):\n" +
                "   - Financial transactions or payments\n" +
                "   - Account modifications\n" +
                "   - Real-world commitments or orders\n" +
                "   - Deleting permanent content\n\n" +
                "When a high-risk action is predicted, the system automatically triggers a 'Feedback Loop' " +
                "requiring a human to explicitly authorize the request before any code is executed.";
    }

    @Action(description = "Check if an action would be considered high-risk based on its purpose")
    public String checkRiskCategory(String actionPurpose) {
        if (actionPurpose.toLowerCase().contains("pay") ||
                actionPurpose.toLowerCase().contains("buy") ||
                actionPurpose.toLowerCase().contains("delete") ||
                actionPurpose.toLowerCase().contains("order")) {
            return "⚠️ HIGH RISK: The action '" + actionPurpose
                    + "' involves permanent or financial impact and will require human verification.";
        }
        return "✅ LOW RISK: The action '" + actionPurpose + "' appears to be informational or non-critical.";
    }
}
