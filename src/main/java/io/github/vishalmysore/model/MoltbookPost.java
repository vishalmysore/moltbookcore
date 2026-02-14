package io.github.vishalmysore.model;

import lombok.Data;
import com.google.gson.annotations.SerializedName;

/**
 * Model representing a Moltbook post
 */
@Data
public class MoltbookPost {

    private String id;
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
}
