package io.github.vishalmysore.analyzer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.t4a.predict.PredictionLoader;
import io.github.vishalmysore.model.FeedItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes feed content to find relevant discussions
 */
@Component
@Slf4j
public class FeedAnalyzer {

    private final Gson gson = new Gson();
    private final com.t4a.processor.AIProcessor processor;
    private final java.util.List<String> relevantKeywords;
    private final String skills;

    public FeedAnalyzer() {
        this.processor = com.t4a.predict.PredictionLoader.getInstance().createOrGetAIProcessor();
        this.skills = PredictionLoader.getInstance().getActionNameList().toString();
        this.relevantKeywords = extractKeywordsFromSkills();
    }

    public List<String> getRelevantKeywords() {
        return relevantKeywords;
    }

    public String getSkills() {
        return skills;
    }

    /**
     * Parse JSON feed response into FeedItem objects
     */
    public List<FeedItem> parseFeed(String feedJson) {
        List<FeedItem> items = new ArrayList<>();

        try {
            JsonObject response = gson.fromJson(feedJson, JsonObject.class);

            if (response.has("posts")) {
                JsonArray posts = response.getAsJsonArray("posts");
                for (JsonElement element : posts) {
                    FeedItem item = gson.fromJson(element, FeedItem.class);
                    items.add(item);
                }
            }

            if (response.has("results")) {
                JsonArray results = response.getAsJsonArray("results");
                for (JsonElement element : results) {
                    FeedItem item = gson.fromJson(element, FeedItem.class);
                    items.add(item);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse feed", e);
        }

        return items;
    }

    /**
     * Extract relevant keywords from agent skills using AI once at startup
     */
    private java.util.List<String> extractKeywordsFromSkills() {
        java.util.List<String> keywords = new java.util.ArrayList<>();
        try {
            String prompt = "Extract 5-10 single-word broad topics/keywords from these agent skills. " +
                    "Return ONLY a comma-separated list of lowercase words (e.g. 'java, coding, ai').\n\nSkills:\n"
                    + skills;

            String response = processor.query(prompt).trim();
            // Clean response
            response = response.replace("\"", "").replace(".", "");

            for (String part : response.split(",")) {
                String keyword = part.trim().toLowerCase();
                if (!keyword.isEmpty() && keyword.length() > 2) {
                    keywords.add(keyword);
                }
            }
            log.info("🎯 Initialized relevant keywords: {}", keywords);
        } catch (Exception e) {
            log.warn("Failed to extract keywords from skills, using defaults", e);
            keywords.add("ai");
            keywords.add("agent");
            keywords.add("java");
        }
        return keywords;
    }

    /**
     * Check if text looks relevant to agent capabilities using AI semantic matching
     */
    public boolean looksRelevant(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        try {
            // First do a quick keyword check to save AI tokens/time
            if (!keywordMatch(text)) {
                return false;
            }

            // Get agent skills dynamically
            String mySkills = PredictionLoader.getInstance().getActionNameList().toString();

            // Build prompt for semantic matching
            String prompt = String.format(
                    "You are an AI decision engine for an autonomous agent.\n" +
                            "Your Skills/Capabilities:\n%s\n\n" +
                            "Analyze this feed item text:\n\"%s\"\n\n" +
                            "Task: Determine if this text is relevant to your skills or if you can provide a helpful response based on your capabilities.\n"
                            +
                            "Return ONLY a JSON object with this format:\n" +
                            "{\"relevant\": boolean, \"reason\": \"short explanation\"}",
                    mySkills, text.replace("\"", "\\\""));

            String response = processor.query(prompt);

            // Parse response
            try {
                // Clean up markdown code blocks if present
                if (response.contains("```json")) {
                    response = response.substring(response.indexOf("```json") + 7);
                    if (response.contains("```")) {
                        response = response.substring(0, response.indexOf("```"));
                    }
                } else if (response.contains("```")) {
                    response = response.substring(response.indexOf("```") + 3);
                    if (response.contains("```")) {
                        response = response.substring(0, response.indexOf("```"));
                    }
                }

                JsonObject result = gson.fromJson(response.trim(), JsonObject.class);
                boolean isRelevant = result.get("relevant").getAsBoolean();
                String reason = result.get("reason").getAsString();

                if (isRelevant) {
                    log.info("✅ Relevant item found: \"{}\"\n   Reason: {}",
                            text.substring(0, Math.min(50, text.length())) + "...", reason);
                } else {
                    log.debug("❌ Irrelevant item: \"{}\"\n   Reason: {}",
                            text.substring(0, Math.min(50, text.length())) + "...", reason);
                }

                return isRelevant;

            } catch (Exception e) {
                log.warn("Failed to parse AI relevance decision: " + response);
                // Fallback to keyword matching if AI fails
                return keywordMatch(text);
            }

        } catch (Exception e) {
            log.error("Error during semantic relevance check", e);
            return keywordMatch(text);
        }
    }

    /**
     * Keyword matcher based on skills
     */
    private boolean keywordMatch(String text) {
        if (text == null)
            return false;
        String lower = text.toLowerCase();
        for (String keyword : relevantKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Analyze feed items and filter for relevant content based on agent
     * capabilities
     * TODO: Make this fully dynamic by analyzing Tools4AI actions
     */
    public List<FeedItem> findRelevantItems(List<FeedItem> feed) {
        List<FeedItem> relevantItems = new ArrayList<>();

        for (FeedItem item : feed) {
            if (looksRelevant(item.getFullText())) {
                relevantItems.add(item);
                // Log is already handled in looksRelevant
            }
        }

        return relevantItems;
    }

    /**
     * Determine engagement strategy for an item
     */
    public EngagementAction analyzeForEngagement(FeedItem item) {
        String text = item.getFullText().toLowerCase();

        // Questions deserve answers
        if (text.contains("?")) {
            return EngagementAction.COMMENT_WITH_INFO;
        }

        // Comparisons - we're good at those
        if (text.contains("vs") || text.contains("compare") ||
                text.contains("better than")) {
            return EngagementAction.COMMENT_WITH_COMPARISON;
        }

        // Good content - just upvote
        if (item.getUpvotes() > 5 || text.contains("insight") ||
                text.contains("learned") || text.contains("interesting")) {
            return EngagementAction.UPVOTE_ONLY;
        }

        return EngagementAction.OBSERVE_ONLY;
    }

    public enum EngagementAction {
        UPVOTE_ONLY,
        COMMENT_WITH_INFO,
        COMMENT_WITH_COMPARISON,
        COMMENT_WITH_RECOMMENDATION,
        OBSERVE_ONLY
    }
}
