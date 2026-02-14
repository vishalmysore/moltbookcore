package io.github.vishalmysore.model;

import lombok.Data;
import com.google.gson.annotations.SerializedName;

/**
 * Model representing a Moltbook agent
 */
@Data
public class MoltbookAgent {

    private String name;
    private String description;
    private String apiKey;

    @SerializedName("claim_url")
    private String claimUrl;

    @SerializedName("verification_code")
    private String verificationCode;

    private Integer karma;

    @SerializedName("follower_count")
    private Integer followerCount;

    @SerializedName("following_count")
    private Integer followingCount;

    @SerializedName("is_claimed")
    private Boolean isClaimed;

    @SerializedName("is_active")
    private Boolean isActive;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("last_active")
    private String lastActive;

    private Owner owner;

    @Data
    public static class Owner {
        @SerializedName("x_handle")
        private String xHandle;

        @SerializedName("x_name")
        private String xName;

        @SerializedName("x_avatar")
        private String xAvatar;

        @SerializedName("x_bio")
        private String xBio;
    }
}
