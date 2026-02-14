package io.github.vishalmysore.client;

import com.t4a.predict.PredictionLoader;
import com.t4a.predict.Tools4AI;
import com.t4a.processor.AIProcessor;
import io.github.vishalmysore.analyzer.FeedAnalyzer;
import io.github.vishalmysore.model.FeedItem;
import io.github.vishalmysore.service.ActivityTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.web.client.HttpClientErrorException;

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
    private final String prompt;

    public MoltbookHeartbeat(MoltbookClient moltbookClient,
            FeedAnalyzer feedAnalyzer,
            ActivityTrackingService activityTrackingService) {
        this.moltbookClient = moltbookClient;
        this.feedAnalyzer = feedAnalyzer;
        this.activityTrackingService = activityTrackingService;
        this.processor = PredictionLoader.getInstance().createOrGetAIProcessor();

        String mySkills = Tools4AI.getActionListAsJSONRPC();

        // Use Tools4AI to create an engaging post with jokes
        prompt = "These are my skills -" + mySkills
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
     * This is the main "pull" loop
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
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

            if (statusResponse == null || !statusResponse.contains("\"status\":\"claimed\"")) {
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
            log.info("Processing: {}", text.substring(0, Math.min(100, text.length())));

            // Analyze engagement strategy
            FeedAnalyzer.EngagementAction action = feedAnalyzer.analyzeForEngagement(item);
            log.info("Engagement strategy: {}", action);

            switch (action) {
                case UPVOTE_ONLY:
                    log.info("👍 Upvoting post {}", item.getId());
                    executeWithVerification(() -> {
                        moltbookClient.upvote(item.getId());
                        return "upvoted";
                    }, "Upvote " + item.getId());
                    break;

                case COMMENT_WITH_INFO:
                case COMMENT_WITH_COMPARISON:
                case COMMENT_WITH_RECOMMENDATION:
                    // Check comment cooldown
                    if (!canComment()) {
                        log.info("⏰ Comment cooldown active - skipping comment on post {}", item.getId());
                        break;
                    }

                    // Use Tools4AI to generate intelligent response
                    String prompt = buildPrompt(item, action);
                    String response = processor.query(prompt);

                    log.info("💬 Commenting on post {}: {}",
                            item.getId(),
                            response.substring(0, Math.min(100, response.length())));

                    try {
                        String commentResponse = executeWithVerification(
                                () -> moltbookClient.createComment(item.getId(), response),
                                "Comment on " + item.getId());

                        lastCommentTime = Instant.now();
                        moltbookClient.upvote(item.getId()); // Also upvote

                        // Track successful comment with green light
                        activityTrackingService.trackComment(
                                item.getId(),
                                item.getTitle() != null ? item.getTitle() : "Post by " + item.getAuthor().getName(),
                                response,
                                true // SUCCESS status
                        );
                    } catch (RuntimeException e) {
                        if (e.getMessage().contains("429") || e.getMessage().contains("Too Many Requests")) {
                            log.warn("⏰ Hit rate limit for comments: {}", e.getMessage());
                            updateCooldownFromError(e.getMessage(), false);

                            // Track failed comment with red light
                            activityTrackingService.trackComment(
                                    item.getId(),
                                    item.getTitle() != null ? item.getTitle() : "Post by " + item.getAuthor().getName(),
                                    response,
                                    false // FAILED status
                            );
                            activityTrackingService.trackError("Rate limit hit for comment on post " + item.getId());
                        } else {
                            // Track failed comment with red light
                            activityTrackingService.trackComment(
                                    item.getId(),
                                    item.getTitle() != null ? item.getTitle() : "Post by " + item.getAuthor().getName(),
                                    response,
                                    false // FAILED status
                            );
                            activityTrackingService
                                    .trackError("Failed to comment on post " + item.getId() + ": " + e.getMessage());
                            throw e;
                        }
                    }
                    break;

                case OBSERVE_ONLY:
                    log.info("👀 Observing post {} (no action)", item.getId());
                    activityTrackingService.trackObservation(
                            item.getId(),
                            item.getTitle() != null ? item.getTitle() : "Post by " + item.getAuthor().getName());
                    break;
            }

            // Rate limit protection
            Thread.sleep(2000); // 2 seconds between actions

        } catch (Exception e) {
            log.error("Failed to process item: {}", item.getId(), e);
            activityTrackingService.trackError("Exception processing post " + item.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Build a prompt for Tools4AI based on engagement strategy
     */
    private String buildPrompt(FeedItem item, FeedAnalyzer.EngagementAction action) {
        String text = item.getFullText();
        String author = item.getAuthor().getName();

        switch (action) {
            case COMMENT_WITH_INFO:
                return String.format(
                        "@%s asked: \"%s\"\n\nProvide helpful information using your knowledge and available capabilities.",
                        author, text);

            case COMMENT_WITH_COMPARISON:
                return String.format(
                        "@%s is discussing: \"%s\"\n\nProvide a comparison or analysis if relevant to your capabilities.",
                        author, text);

            case COMMENT_WITH_RECOMMENDATION:
                return String.format(
                        "@%s needs advice: \"%s\"\n\nProvide a recommendation based on your capabilities.",
                        author, text);

            default:
                return text; // Fallback
        }
    }

    /**
     * Post about capabilities with robust verification handling
     */
    private void postAboutCapabilities() {
        try {
            // Check post cooldown
            if (!canPost()) {
                long minutesRemaining = getMinutesUntilCanPost();
                log.info("⏰ Post cooldown active - need to wait {} more minutes before posting", minutesRemaining);
                return;
            }

            log.info(" No interesting discussions found - posting about our capabilities...");

            String rawPostContent = processor.query(prompt);

            // Clean up the response if it has quotes or extra formatting
            rawPostContent = rawPostContent.trim();
            if (rawPostContent.startsWith("\"") && rawPostContent.endsWith("\"")) {
                rawPostContent = rawPostContent.substring(1, rawPostContent.length() - 1);
            }

            final String postContent = rawPostContent;

            log.info("📝 Posting to Moltbook: {}", postContent.substring(0, Math.min(100, postContent.length())));

            try {
                // Post to general submolt using robust customized execution
                String response = executeWithVerification(
                        () -> moltbookClient.createPost("general", "🤖 Your AI Assistant is Here!", postContent),
                        "Post capabilities");

                lastPostTime = Instant.now();
                log.info("✅ Posted about capabilities successfully. Response: {}", response);

                // Track successful post with green light
                activityTrackingService.trackPost(
                        response != null && response.contains("id") ? "new-post" : "pending",
                        "🤖 Your AI Assistant is Here!",
                        postContent,
                        true // SUCCESS status
                );
            } catch (RuntimeException e) {
                if (e.getMessage().contains("429") || e.getMessage().contains("Too Many Requests")) {
                    log.warn("⏰ Hit rate limit for posts: {}", e.getMessage());
                    updateCooldownFromError(e.getMessage(), true);

                    // Track failed post with red light
                    activityTrackingService.trackPost(
                            "rate-limited",
                            "🤖 Your AI Assistant is Here!",
                            postContent,
                            false // FAILED status
                    );
                    activityTrackingService.trackError("Rate limit hit when posting about capabilities");
                } else {
                    // Track failed post with red light
                    activityTrackingService.trackPost(
                            "error",
                            "🤖 Your AI Assistant is Here!",
                            postContent,
                            false // FAILED status
                    );
                    activityTrackingService.trackError("Failed to post about capabilities: " + e.getMessage());
                    throw e;
                }
            }

            // Wait a bit to respect rate limits
            Thread.sleep(3000);

        } catch (Exception e) {
            log.error("Failed to post about capabilities", e);
            activityTrackingService.trackError("Exception posting about capabilities: " + e.getMessage());
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
            String mySkills = Tools4AI.getActionListAsJSONRPC();
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

                    // Track successful verification
                    activityTrackingService.trackPost(
                            postId,
                            "Verified pending post",
                            "Successfully verified post " + postId,
                            true);
                } catch (Exception e) {
                    log.error("❌ Failed to verify post {}", postId, e);
                    activityTrackingService
                            .trackError("Failed to verify pending post " + postId + ": " + e.getMessage());
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
     * Executes an API action with robust verification handling.
     * Handles both success-path verification (200 OK + "verification_required")
     * and error-path verification (403 Forbidden + "verification_required").
     * 
     * @param action      The API callback to execute
     * @param description Human-readable description for logging
     * @return The response string
     */
    private String executeWithVerification(java.util.function.Supplier<String> action, String description) {
        try {
            String response = action.get();
            // Success path verification check
            if (response != null && response.contains("verification_required")) {
                log.info("🔐 {} response requires verification", description);
                if (handleVerificationResponse(response)) {
                    log.info("✅ Verified successfully for {}", description);
                    // Usually for 200 OK, the item is created as PENDING, so we don't retry.
                    return response;
                }
            }
            return response;
        } catch (RuntimeException e) {
            // Error path verification check
            String errorBody = extractErrorBody(e);
            if (errorBody != null && errorBody.contains("verification_required")) {
                log.info("🔐 {} failed with verification challenge", description);
                if (handleVerificationResponse(errorBody)) {
                    log.info("🔄 Retrying {} after verification...", description);
                    // Retry ONCE
                    return action.get();
                }
            }
            throw e;
        }
    }

    /**
     * Standardized verification response handler
     * Parses JSON, extracts challenge, solves it, and submits code.
     */
    private boolean handleVerificationResponse(String jsonResponse) {
        try {
            com.google.gson.JsonObject response = new com.google.gson.Gson().fromJson(jsonResponse,
                    com.google.gson.JsonObject.class);

            com.google.gson.JsonObject verification = null;
            if (response.has("verification")) {
                verification = response.getAsJsonObject("verification");
            } else if (response.has("error") && response.has("hint")) { // Handle some error formats
                // Try to look deeper or maybe the error itself IS the challenge if
                // unstructured?
                // But Moltbook standard is "verification" object.
                // Fallback if structure is flat?
            }

            if (verification == null) {
                return false;
            }

            String verificationCode = verification.get("code").getAsString();
            String challenge = verification.get("challenge").getAsString();

            log.info("🧩 Handling verification challenge: {}", challenge);

            String answer = solveVerificationChallenge(challenge);
            log.info("💡 Computed answer: {}", answer);

            String verifyResponse = moltbookClient.verifyPost(verificationCode, answer);
            log.info("✅ Verification submission: {}", verifyResponse);

            return verifyResponse != null
                    && (verifyResponse.contains("\"success\":true") || verifyResponse.contains("verified"));

        } catch (Exception e) {
            log.error("❌ Error handling verification response", e);
            return false;
        }
    }

    /**
     * Robust math solver for verification challenges
     */
    private String solveVerificationChallenge(String challenge) throws Exception {
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

        // More robust answer cleaning
        answer = answer.replaceAll("[^0-9.]", ""); // Remove all non-numeric except decimal point

        // Handle multiple decimal points (keep only first)
        int firstDot = answer.indexOf('.');
        if (firstDot >= 0) {
            String beforeDot = answer.substring(0, firstDot);
            String afterDot = answer.substring(firstDot + 1).replaceAll("\\\\.", "");
            answer = beforeDot + "." + afterDot;
        }

        // Ensure 2 decimal places
        if (!answer.contains(".")) {
            answer = answer + ".00";
        } else {
            String[] parts = answer.split("\\\\.");
            if (parts.length == 2) {
                if (parts[1].length() == 0) {
                    answer = parts[0] + ".00";
                } else if (parts[1].length() == 1) {
                    answer = parts[0] + "." + parts[1] + "0";
                } else if (parts[1].length() > 2) {
                    // Truncate to 2 decimal places
                    answer = parts[0] + "." + parts[1].substring(0, 2);
                }
            } else {
                answer = parts[0] + ".00";
            }
        }

        return answer;
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
