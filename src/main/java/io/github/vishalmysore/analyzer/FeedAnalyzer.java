package io.github.vishalmysore.analyzer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
     * Check if text looks relevant to agent capabilities using NLP/intent detection
     * TODO: Make this fully dynamic by analyzing Tools4AI available actions
     */
    public boolean looksRelevant(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        String lower = text.toLowerCase();

        // For now, keep car-related logic as example domain
        // In future, this should analyze Tools4AI action descriptions dynamically
        return looksCarRelated(text);
    }

    /**
     * Check if text looks car-related using NLP/intent detection
     * Kept for backward compatibility and as example implementation
     */
    public boolean looksCarRelated(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        String lower = text.toLowerCase();

        // Direct car mentions
        if (lower.contains("car") || lower.contains("vehicle") ||
                lower.contains("auto") || lower.contains("automobile")) {
            return true;
        }

        // Car brands
        String[] brands = { "tesla", "bmw", "toyota", "honda", "ford",
                "chevrolet", "nissan", "mercedes", "audi" };
        for (String brand : brands) {
            if (lower.contains(brand)) {
                return true;
            }
        }

        // Car-related terms
        String[] terms = { "electric vehicle", "ev", "hybrid", "gas mileage",
                "horsepower", "mpg", "sedan", "suv", "truck" };
        for (String term : terms) {
            if (lower.contains(term)) {
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
                log.info("Found relevant post: {} by @{}",
                        item.getTitle() != null ? item.getTitle() : item.getContent(),
                        item.getAuthor().getName());
            }
        }

        return relevantItems;
    }

    /**
     * Legacy method name for backward compatibility
     */
    public List<FeedItem> findCarRelatedItems(List<FeedItem> feed) {
        return findRelevantItems(feed);
    }

    /**
     * Determine engagement strategy for an item
     */
    public EngagementAction analyzeForEngagement(FeedItem item) {
        String text = item.getFullText().toLowerCase();

        // Questions deserve answers
        if (text.contains("?")) {
            if (text.contains("best car") || text.contains("which car") ||
                    text.contains("recommend") || text.contains("advice")) {
                return EngagementAction.COMMENT_WITH_RECOMMENDATION;
            }
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
