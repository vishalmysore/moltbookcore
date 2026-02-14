package io.github.vishalmysore.startup;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.vishalmysore.client.MoltbookClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Startup check for Moltbook configuration
 * Helps users register if they don't have an API key
 */
@Component
@Slf4j
public class MoltbookStartupCheck implements CommandLineRunner {

    private final MoltbookClient moltbookClient;

    @Value("${moltbook.agent.name:GenericAIAgent}")
    private String agentName;

    @Value("${moltbook.agent.description:AI agent with dynamic capabilities powered by Tools4AI}")
    private String agentDescription;

    public MoltbookStartupCheck(MoltbookClient moltbookClient) {
        this.moltbookClient = moltbookClient;
    }

    @Override
    public void run(String... args) {
        log.info("═══════════════════════════════════════════════════════");
        log.info("🦞 Moltbook Agent Starting...");
        log.info("═══════════════════════════════════════════════════════");

        if (!moltbookClient.hasApiKey()) {
            log.warn("");
            log.warn("⚠️  NO MOLTBOOK API KEY FOUND!");
            log.warn("");
            log.warn("To get started, you need to register your agent:");
            log.warn("");
            log.warn("Option 1 - Register via code (uncomment below):");
            log.warn("  // Uncomment in MoltbookStartupCheck.java:");
            log.warn("  // registerNow();");
            log.warn("");
            log.warn("Option 2 - Register manually:");
            log.warn("  curl -X POST https://www.moltbook.com/api/v1/agents/register \\");
            log.warn("    -H 'Content-Type: application/json' \\");
            log.warn("    -d '{\"name\":\"" + agentName + "\",\"description\":\"" + agentDescription + "\"}'");
            log.warn("");
            log.warn("Then set your API key:");
            log.warn("  export MOLTBOOK_API_KEY=\"moltbook_xxx\"");
            log.warn("  # Or add to application.properties: moltbook.api.key=moltbook_xxx");
            log.warn("");
            log.warn("Agent will run in limited mode until API key is configured.");
            log.warn("═══════════════════════════════════════════════════════");

            // Uncomment to auto-register on first run:
            // registerNow();

        } else {
            log.info("✓ Moltbook API key configured");

            try {
                String status = moltbookClient.getAgentStatus();
                JsonObject statusJson = new Gson().fromJson(status, JsonObject.class);
                String claimStatus = statusJson.get("status").getAsString();

                if ("claimed".equals(claimStatus)) {
                    log.info("✓ Agent is CLAIMED and ready!");
                } else {
                    log.warn("⏳ Agent registered but NOT CLAIMED yet");
                    log.warn("   Send the claim URL to your human to activate!");
                }

            } catch (Exception e) {
                log.error("Failed to check agent status", e);
            }

            log.info("═══════════════════════════════════════════════════════");
            log.info("🚀 Heartbeat will run every 5 minutes");
            log.info("═══════════════════════════════════════════════════════");
        }
    }

    /**
     * Uncomment this call in run() to auto-register on first startup
     */
    private void registerNow() {
        try {
            log.info("Registering agent: {}", agentName);
            String response = moltbookClient.registerAgent(agentName, agentDescription);

            log.info("");
            log.info("═══════════════════════════════════════════════════════");
            log.info("✅ REGISTRATION SUCCESSFUL!");
            log.info("═══════════════════════════════════════════════════════");
            log.info("");
            log.info("Response: {}", response);
            log.info("");

            // Parse response to extract API key and claim URL
            JsonObject json = new Gson().fromJson(response, JsonObject.class);
            JsonObject agent = json.getAsJsonObject("agent");
            String apiKey = agent.get("api_key").getAsString();
            String claimUrl = agent.get("claim_url").getAsString();
            String verificationCode = agent.get("verification_code").getAsString();

            log.info("⚠️  SAVE YOUR API KEY NOW:");
            log.info("   {}", apiKey);
            log.info("");
            log.info("📋 Set it for next run:");
            log.info("   export MOLTBOOK_API_KEY=\"{}\"", apiKey);
            log.info("");
            log.info("🔗 Claim URL (send to your human):");
            log.info("   {}", claimUrl);
            log.info("");
            log.info("🔑 Verification Code: {}", verificationCode);
            log.info("═══════════════════════════════════════════════════════");
            log.info("");

            // Set API key for this session
            moltbookClient.setApiKey(apiKey);

        } catch (Exception e) {
            log.error("Auto-registration failed", e);
            log.error("Please register manually using the curl command above");
        }
    }
}
