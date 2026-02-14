package io.github.vishalmysore.client;

import com.t4a.detect.HumanInLoop;
import com.t4a.predict.PredictionLoader;
import com.t4a.predict.Tools4AI;
import com.t4a.processor.AIProcessor;
import com.t4a.processor.scripts.ScriptProcessor;
import com.t4a.transform.PromptTransformer;
import io.github.vishalmysore.analyzer.FeedAnalyzer;
import io.github.vishalmysore.model.FeedItem;
import io.github.vishalmysore.service.ActivityTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * Moltbook heartbeat - Pull-based architecture
 * 
 * This demonstrates the practical usage:
 * 1. Pull feed from Moltbook
 * 2. Analyze for relevant content based on agent capabilities
 * 3. Use Tools4AI to generate responses
 * 4. Post actions back to Moltbook
 * 
 * NO inbound requests - everything is outbound!
 */
@Component
@Slf4j
public class MoltbookHeartbeat {

    private final MoltbookClient moltbookClient;
    private final FeedAnalyzer feedAnalyzer;
    private final ActivityTrackingService activityTrackingService;

    private Instant lastCheck;
    private int semanticSearchResultCount = 0;
    private Instant lastPostTime = null;
    private Instant lastCommentTime = null;
    private int postCooldownMinutes = 120; // 2 hours for new agents
    private int commentCooldownSeconds = 20;

    private AIProcessor processor;
    private PromptTransformer promptTransformer;
    private final String capabilityPrompt;
    private HumanInLoop humanInLoop;
    String mySkills;

    public MoltbookHeartbeat(MoltbookClient moltbookClient,
            FeedAnalyzer feedAnalyzer,
            ActivityTrackingService activityTrackingService, HumanInLoop humanInLoop) {
        this.moltbookClient = moltbookClient;
        this.feedAnalyzer = feedAnalyzer;
        this.activityTrackingService = activityTrackingService;
        this.processor = PredictionLoader.getInstance().createOrGetAIProcessor();
        this.humanInLoop = humanInLoop;
        this.promptTransformer = PredictionLoader.getInstance().createOrGetPromptTransformer();

        // Configure MoltbookClient with this heartbeat as the challenge solver
        this.moltbookClient.setChallengeSolver(this::solveVerificationChallenge);

        mySkills = feedAnalyzer.getSkills();

        // Use Tools4AI to create an engaging post with jokes
        capabilityPrompt = "These are my skills -" + mySkills
                + "- Create a fun and engaging Moltbook post (max 500 chars) that:\n" +
                "1. Makes a witty joke about on topics derived from my skills or AI agents helping with my skills\n" +
                "2. Introduces my skill related capabilities: \n" +
                "3. Asks other agents to respond or share their questions on my skills\n" +
                "4. Be friendly, casual, and use 1-2 emojis\n" +
                "5. End with a question to encourage engagement\n\n" +
                "Make it sound natural, not like an advertisement. Be creative!";
    }

    /**
     * Heartbeat runs every 5 minutes
     * This is the main "pull" loop - starts immediately on app startup
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 0)
    public void runHeartbeat() {
        log.info("🦞 Moltbook heartbeat starting...");

        // Double-check protection
        if (lastCheck != null &&
                Duration.between(lastCheck, Instant.now()).toMinutes() < 5) {
            log.debug("Heartbeat ran recently, skipping");
            return;
        }

        try {
            // 1️⃣ Check if we're claimed
            String statusResponse = moltbookClient.getAgentStatus();
            log.info("Agent status: {}", statusResponse);

            if (statusResponse == null) {
                lastCheck = Instant.now();
                return;
            }

            com.google.gson.JsonObject statusObj = new com.google.gson.Gson().fromJson(statusResponse,
                    com.google.gson.JsonObject.class);
            if (!statusObj.has("status") || !"claimed".equals(statusObj.get("status").getAsString())) {
                log.warn("⏳ Agent not claimed yet - waiting for human verification");
                lastCheck = Instant.now();
                return;
            }

            // 2️⃣ Pull feed (50 items) - fallback to posts if feed fails
            log.info("📥 Pulling feed...");
            String feedJson;
            try {
                feedJson = moltbookClient.getFeed(50);
            } catch (RuntimeException e) {
                if (e.getMessage().contains("401") || e.getMessage().contains("Authentication required")) {
                    log.warn("Feed endpoint requires subscriptions - using global posts instead");
                    feedJson = moltbookClient.getPosts("new", 50);
                } else {
                    throw e;
                }
            }
            List<FeedItem> feed = feedAnalyzer.parseFeed(feedJson);
            log.info("Retrieved {} items from feed", feed.size());

            // 3️⃣ Analyze for relevant content based on agent capabilities
            List<FeedItem> relevantItems = feedAnalyzer.findRelevantItems(feed);
            log.info("🔍 Found {} relevant items", relevantItems.size());

            // 4️⃣ Process relevant items
            for (FeedItem item : relevantItems) {
                processRelevantItem(item);
            }

            // 5️⃣ Optional: Semantic search for relevant discussions
            searchForRelevantDiscussions();

            // 6️⃣ Check for pending posts that need verification
            checkPendingPosts();

            // 7️⃣ Post about capabilities if no relevant discussions found via semantic
            // search
            // This ensures we promote our services even when feed has false positives
            if (semanticSearchResultCount == 0) {
                log.info("💡 No relevant discussions found via semantic search - posting about capabilities");
                postAboutCapabilities();
            }

            lastCheck = Instant.now();
            log.info("✅ Heartbeat completed successfully");

        } catch (Exception e) {
            log.error("❌ Heartbeat failed", e);
            lastCheck = Instant.now(); // Still update to avoid spam
        }
    }

    /**
     * Process a relevant feed item based on agent capabilities
     * This is where Tools4AI comes in!
     */
    private void processRelevantItem(FeedItem item) {
        try {
            String text = item.getFullText();
            String author = item.getAuthor().getName();
            log.info("Processing relevant item from @{}: {}", author, text.substring(0, Math.min(100, text.length())));

            // Build a descriptive prompt for the AI to understand the context and decide
            // the action
            String prompt = String.format(
                    "You are an autonomous agent on Moltbook.\n" +
                            "Found a relevant post from @%s:\n\"%s\"\n\n" +
                            "Task: Decide how to engage with this post and execute the appropriate action. " +
                            "if you cannot find any action mapped then do not give any random action " +
                            "Choose the most helpful and engaging action based on your skills.",
                    author, text, item.getId());
            Object result = null;
            String promptAskIfActionCanBeExecuted = String.format(
                    "You are an autonomous agent on Moltbook.\n" +
                            "Found a relevant post from @%s:\n\"%s\"\n\n" +
                            "Task: Can you execute an action to engage with this post based on your skills? " +
                            "Answer YES or NO and if YES, specify the action name " +
                            "if you cannot find any action mapped then just answer NO." + mySkills,
                    author, text, item.getId());
            YesOrNoDecision yesOrNoDecision = (YesOrNoDecision) promptTransformer
                    .transformIntoPojo(promptAskIfActionCanBeExecuted, YesOrNoDecision.class);
            // Wrap with script processor for action execution
            try {
                log.info("🤖 AI is deciding action for post: {}", item.getId());
                if (yesOrNoDecision.isYes()) {
                    result = processor.processSingleAction(prompt, humanInLoop);
                } else {
                    log.info("👀 AI decided not to take action on post: {}", item.getId());
                    result = processor.query(prompt);
                }

                if (result != null) {
                    log.info("✅ Action executed: {}", result);
                    activityTrackingService.trackAction("NLP_ACTION", "Post ID: " + item.getId(), result.toString(),
                            true);
                } else {
                    log.info("👀 AI decided no action was necessary for post: {}", item.getId());
                    activityTrackingService.trackObservation(item.getId(), author + ": " + item.getTitle());
                }

            } catch (Exception e) {
                // Check if this is a high-risk action block
                log.warn("Failed to process action via NLP", e);
                activityTrackingService
                        .trackLog("No Matching action found just returning normal answer" + item.getId() + ": "
                                + e.getMessage());
                prompt = String.format(
                        "You are an autonomous agent on Moltbook.\n" +
                                "Found a relevant post from @%s:\n\"%s\"\n\n" +
                                "Task: Decide how to engage with this post and return funny and engaging response. " +
                                "Choose the most helpful and engaging response based on your skills.",
                        author, text, item.getId());
                result = processor.query(prompt);
                activityTrackingService.trackAction("NLP_REPLY", "Query for @ " + author, result.toString(), true);

            }

            // Rate limit protection
            Thread.sleep(2000);

        } catch (Exception e) {
            log.error("Failed to process item: {}", item.getId(), e);
        }
    }

    /**
     * Post about capabilities using NLP action discovery
     */
    private void postAboutCapabilities() {
        try {
            // Check post cooldown
            if (!canPost()) {
                long minutesRemaining = getMinutesUntilCanPost();
                log.info("⏰ Post cooldown active - need to wait {} more minutes before posting", minutesRemaining);
                return;
            }

            log.info("💡 No interesting discussions found - asking AI to post about our capabilities...");

            try {
                // this will query for text , will not call any action as there is no action
                // mentioned in the prompt and we are not using processSingleAction here
                Object result = processor.query(capabilityPrompt);

                if (result != null) {
                    lastPostTime = Instant.now();
                    log.info("✅ Posted about capabilities successfully. Result: {}", result);
                    activityTrackingService.trackAction("CAPABILITY_POST", "Automated Capability Promotion",
                            result.toString(), true);
                }
            } catch (Exception e) {
                log.error("Failed to post about capabilities via NLP", e);
                activityTrackingService.trackError("Capability post failed: " + e.getMessage());
            }

            // Wait a bit to respect rate limits
            Thread.sleep(3000);

        } catch (Exception e) {
            log.error("Failed to post about capabilities", e);
            activityTrackingService.trackError("Exception in postAboutCapabilities: " + e.getMessage());
        }
    }

    /**
     * Check if we can post (respecting cooldown)
     */
    private boolean canPost() {
        if (lastPostTime == null) {
            return true;
        }
        Duration timeSincePost = Duration.between(lastPostTime, Instant.now());
        return timeSincePost.toMinutes() >= postCooldownMinutes;
    }

    /**
     * Check if we can comment (respecting cooldown)
     */
    private boolean canComment() {
        if (lastCommentTime == null) {
            return true;
        }
        Duration timeSinceComment = Duration.between(lastCommentTime, Instant.now());
        return timeSinceComment.toSeconds() >= commentCooldownSeconds;
    }

    /**
     * Get minutes until we can post again
     */
    private long getMinutesUntilCanPost() {
        if (lastPostTime == null) {
            return 0;
        }
        Duration timeSincePost = Duration.between(lastPostTime, Instant.now());
        long minutesPassed = timeSincePost.toMinutes();
        return Math.max(0, postCooldownMinutes - minutesPassed);
    }

    /**
     * Update cooldown periods from API error messages
     */
    private void updateCooldownFromError(String errorMessage, boolean isPost) {
        try {
            if (isPost && errorMessage.contains("retry_after_minutes")) {
                // Extract retry_after_minutes from error
                int start = errorMessage.indexOf("\"retry_after_minutes\":") + 22;
                int end = errorMessage.indexOf(",", start);
                if (end == -1)
                    end = errorMessage.indexOf("}", start);
                String minutesStr = errorMessage.substring(start, end).trim();
                postCooldownMinutes = Integer.parseInt(minutesStr);
                log.info("📊 Updated post cooldown to {} minutes", postCooldownMinutes);
                lastPostTime = Instant.now();
            } else if (!isPost && errorMessage.contains("retry_after_seconds")) {
                // Extract retry_after_seconds from error
                int start = errorMessage.indexOf("\"retry_after_seconds\":") + 22;
                int end = errorMessage.indexOf(",", start);
                if (end == -1)
                    end = errorMessage.indexOf("}", start);
                String secondsStr = errorMessage.substring(start, end).trim();
                commentCooldownSeconds = Integer.parseInt(secondsStr);
                log.info("📊 Updated comment cooldown to {} seconds", commentCooldownSeconds);
                lastCommentTime = Instant.now();
            }
        } catch (Exception e) {
            log.warn("Could not parse cooldown from error: {}", e.getMessage());
        }
    }

    /**
     * 
     * Use semantic search to find relevant discussions based on agent capabilities
     * Even if they're not in your feed yet!
     */
    private void searchForRelevantDiscussions() {
        try {
            log.info("🔍 Searching for relevant discussions based on agent capabilities...");

            // Get agent skills summary for search query
            String mySkills = PredictionLoader.getInstance().getActionNameList().toString();
            // Extract key topics from skills (simplified approach)
            String searchQuery = "discussions and questions about agent services";

            String searchJson = moltbookClient.semanticSearch(
                    searchQuery,
                    "posts",
                    10);

            List<FeedItem> results = feedAnalyzer.parseFeed(searchJson);
            semanticSearchResultCount = results.size();
            log.info("Found {} posts via semantic search", results.size());

            // Process top results (with rate limiting)
            for (int i = 0; i < Math.min(3, results.size()); i++) {
                FeedItem item = results.get(i);
                log.info("Search result: {} (similarity: high)", item.getTitle());
                // Could engage with these too, but be conservative
            }

        } catch (Exception e) {
            log.error("Semantic search failed", e);
            semanticSearchResultCount = 0; // Treat errors as no results found
        }
    }

    /**
     * Check for pending posts that need verification
     */
    private void checkPendingPosts() {
        try {
            log.info("🔍 Checking for pending posts requiring verification...");

            // Get agent profile which includes pending posts
            String profileResponse = moltbookClient.getProfile();

            if (profileResponse == null || !profileResponse.contains("pending_posts")) {
                log.debug("No pending posts found");
                return;
            }

            com.google.gson.JsonObject profile = new com.google.gson.Gson().fromJson(profileResponse,
                    com.google.gson.JsonObject.class);

            if (!profile.has("agent") || !profile.getAsJsonObject("agent").has("pending_posts")) {
                log.debug("No pending posts in profile");
                return;
            }

            com.google.gson.JsonArray pendingPosts = profile.getAsJsonObject("agent").getAsJsonArray("pending_posts");

            if (pendingPosts.size() == 0) {
                log.debug("No pending posts to verify");
                return;
            }

            log.info("📝 Found {} pending post(s) requiring verification", pendingPosts.size());

            // Verify each pending post
            for (com.google.gson.JsonElement postElement : pendingPosts) {
                com.google.gson.JsonObject post = postElement.getAsJsonObject();

                if (!post.has("verification")) {
                    continue;
                }

                com.google.gson.JsonObject verification = post.getAsJsonObject("verification");
                String verificationCode = verification.get("code").getAsString();
                String challenge = verification.get("challenge").getAsString();
                String postId = post.get("id").getAsString();

                log.info("🔐 Verifying pending post: {}", postId);
                log.info("🧩 Challenge: {}", challenge);

                // Solve the challenge
                String answer = solveVerificationChallenge(challenge);
                log.info("💡 Computed answer: {}", answer);

                // Submit verification
                try {
                    String verifyResponse = moltbookClient.verifyPost(verificationCode, answer);
                    log.info("✅ Verified post {}: {}", postId, verifyResponse);

                    // Track successful verification with full details
                    activityTrackingService.trackAction(
                            "VERIFY_CHALLENGE",
                            "Challenge: " + challenge + "\nAnswer: " + answer,
                            verifyResponse,
                            true);
                } catch (Exception e) {
                    log.error("❌ Failed to verify post {}", postId, e);
                    activityTrackingService.trackAction(
                            "VERIFY_FAILED",
                            "Challenge: " + challenge + "\nAttempted Answer: " + answer,
                            e.getMessage(),
                            false);
                }

                // Rate limit between verifications
                Thread.sleep(2000);
            }

        } catch (Exception e) {
            log.error("❌ Failed to check pending posts", e);
            activityTrackingService.trackError("Exception checking pending posts: " + e.getMessage());
        }
    }

    /**
     * Robust math solver for verification challenges
     */
    public String solveVerificationChallenge(String challenge) throws Exception {
        // Use AI to solve the challenge with improved prompt
        String solvePrompt = String.format(
                "You are solving a mathematical verification challenge. The challenge text may contain obfuscation like random characters, case changes, or extra symbols.\\n"
                        +
                        "Your task:\\n" +
                        "1. Extract the mathematical problem from the obfuscated text\\n" +
                        "2. Solve the mathematical problem step by step\\n" +
                        "3. Return ONLY the final numeric answer with exactly 2 decimal places\\n" +
                        "4. Format: XXX.XX (e.g., '42.50', '525.00', '1337.25')\\n" +
                        "5. Do NOT include any text, explanations, or units - ONLY the number\\n\\n" +
                        "Challenge text: %s\\n\\n" +
                        "Answer (number only):",
                challenge);

        String answer = processor.query(solvePrompt).trim();
        log.info("🤖 AI generated raw answer: {}", answer);

        // Sanitize: keep only digits, dots, and minus sign
        String sanitized = answer.replaceAll("[^0-9.-]", "");

        try {
            // Harden math parsing with BigDecimal as recommended
            java.math.BigDecimal value = new java.math.BigDecimal(sanitized);
            // Ensure 2 decimal places
            value = value.setScale(2, java.math.RoundingMode.HALF_UP);
            return value.toPlainString();
        } catch (NumberFormatException e) {
            log.warn("Failed to parse AI answer '{}' as number, falling back to regex cleaning", sanitized);
            // Fallback for messy answers
            String messyAnswer = answer.replaceAll("[^0-9.]", "");
            if (messyAnswer.isEmpty())
                return "0.00";
            return messyAnswer;
        }
    }

    /**
     * Extract error body from RuntimeException wrapping HttpClientErrorException
     */
    private String extractErrorBody(RuntimeException e) {
        if (e.getCause() instanceof HttpClientErrorException) {
            return ((HttpClientErrorException) e.getCause()).getResponseBodyAsString();
        }
        return null;
    }

    /**
     * Manual trigger for testing
     */
    public void triggerHeartbeat() {
        log.info("⚡ Manual heartbeat trigger");
        lastCheck = null;
        runHeartbeat();
    }

    public Instant getLastCheck() {
        return lastCheck;
    }
}
