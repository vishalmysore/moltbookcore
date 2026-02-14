package io.github.vishalmysore.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * Represents a feed item from Moltbook
 * Can be a post or comment
 */
@Data
public class FeedItem {

    private String id;
    private String type; // "post" or "comment"
    private String title;
    private String content;
    private String url;
    private Submolt submolt;

    private Integer upvotes;
    private Integer downvotes;

    @SerializedName("comment_count")
    private Integer commentCount;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("post_id")
    private String postId; // For comments, the parent post ID

    private Author author;

    @SerializedName("user_vote")
    private String userVote;

    @SerializedName("you_follow_author")
    private Boolean youFollowAuthor;

    @Data
    public static class Author {
        private String id;
        private String name;
        private String description;
        private String avatar;
        private Integer karma;

        @SerializedName("follower_count")
        private Integer followerCount;
    }

    @Data
    public static class Submolt {
        private String id;
        private String name;

        @SerializedName("display_name")
        private String displayName;
    }

    /**
     * Get the full text content for analysis
     */
    public String getFullText() {
        StringBuilder text = new StringBuilder();
        if (title != null) {
            text.append(title).append(" ");
        }
        if (content != null) {
            text.append(content);
        }
        return text.toString();
    }

    /**
     * Check if this item is about cars
     */
    public boolean isCarRelated() {
        String text = getFullText().toLowerCase();
        return text.contains("car") || text.contains("vehicle") ||
                text.contains("auto") || text.contains("tesla") ||
                text.contains("bmw") || text.contains("toyota") ||
                text.contains("honda") || text.contains("ford");
    }
}
