package io.github.vishalmysore.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.vishalmysore.config.MoltbookConfig;
import io.github.vishalmysore.model.MoltbookAgent;
import io.github.vishalmysore.model.MoltbookPost;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Service for interacting with Moltbook API
 */
@Service
@Slf4j
public class MoltbookService {

    private final MoltbookConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private String apiKey;

    public MoltbookService(MoltbookConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        this.apiKey = config.getApi().getKey();
    }

    /**
     * Register the agent with Moltbook
     */
    public MoltbookAgent register() throws IOException {
        log.info("Registering agent: {}", config.getAgent().getName());

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", config.getAgent().getName());
        requestBody.addProperty("description", config.getAgent().getDescription());

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(config.getApi().getBaseUrl() + "/agents/register")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.debug("Registration response: {}", responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Registration failed: " + responseBody);
            }

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonObject agentData = jsonResponse.getAsJsonObject("agent");

            MoltbookAgent agent = new MoltbookAgent();
            agent.setName(config.getAgent().getName());
            agent.setDescription(config.getAgent().getDescription());
            agent.setApiKey(agentData.get("api_key").getAsString());
            agent.setClaimUrl(agentData.get("claim_url").getAsString());
            agent.setVerificationCode(agentData.get("verification_code").getAsString());

            // Store the API key
            this.apiKey = agent.getApiKey();

            log.info("Agent registered successfully!");
            log.info("API Key: {}", agent.getApiKey());
            log.info("Claim URL: {}", agent.getClaimUrl());
            log.info("Verification Code: {}", agent.getVerificationCode());

            return agent;
        }
    }

    /**
     * Get agent profile
     */
    public MoltbookAgent getProfile() throws IOException {
        Request request = new Request.Builder()
                .url(config.getApi().getBaseUrl() + "/agents/me")
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get profile: " + response.body().string());
            }

            JsonObject jsonResponse = gson.fromJson(response.body().string(), JsonObject.class);
            return gson.fromJson(jsonResponse.getAsJsonObject("agent"), MoltbookAgent.class);
        }
    }

    /**
     * Check claim status
     */
    public String getClaimStatus() throws IOException {
        Request request = new Request.Builder()
                .url(config.getApi().getBaseUrl() + "/agents/status")
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            JsonObject jsonResponse = gson.fromJson(response.body().string(), JsonObject.class);
            return jsonResponse.get("status").getAsString();
        }
    }

    /**
     * Create a post
     */
    public MoltbookPost createPost(String submolt, String title, String content, String url) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("submolt", submolt);
        requestBody.addProperty("title", title);
        if (content != null) {
            requestBody.addProperty("content", content);
        }
        if (url != null) {
            requestBody.addProperty("url", url);
        }

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(config.getApi().getBaseUrl() + "/posts")
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create post: " + response.body().string());
            }

            JsonObject jsonResponse = gson.fromJson(response.body().string(), JsonObject.class);
            return gson.fromJson(jsonResponse.getAsJsonObject("post"), MoltbookPost.class);
        }
    }

    /**
     * Get feed posts
     */
    public List<MoltbookPost> getFeed(String sort, int limit) throws IOException {
        HttpUrl url = HttpUrl.parse(config.getApi().getBaseUrl() + "/posts").newBuilder()
                .addQueryParameter("sort", sort)
                .addQueryParameter("limit", String.valueOf(limit))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get feed: " + response.body().string());
            }

            JsonObject jsonResponse = gson.fromJson(response.body().string(), JsonObject.class);
            MoltbookPost[] posts = gson.fromJson(jsonResponse.getAsJsonArray("posts"), MoltbookPost[].class);
            return Arrays.asList(posts);
        }
    }

    /**
     * Comment on a post
     */
    public void createComment(String postId, String content) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("content", content);

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(config.getApi().getBaseUrl() + "/posts/" + postId + "/comments")
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create comment: " + response.body().string());
            }
        }
    }

    /**
     * Upvote a post
     */
    public void upvotePost(String postId) throws IOException {
        Request request = new Request.Builder()
                .url(config.getApi().getBaseUrl() + "/posts/" + postId + "/upvote")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create("", null))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to upvote post: " + response.body().string());
            }
        }
    }

    /**
     * Search posts semantically
     */
    public List<MoltbookPost> searchPosts(String query, int limit) throws IOException {
        HttpUrl url = HttpUrl.parse(config.getApi().getBaseUrl() + "/search").newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("type", "posts")
                .addQueryParameter("limit", String.valueOf(limit))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to search posts: " + response.body().string());
            }

            JsonObject jsonResponse = gson.fromJson(response.body().string(), JsonObject.class);
            MoltbookPost[] posts = gson.fromJson(jsonResponse.getAsJsonArray("results"), MoltbookPost[].class);
            return Arrays.asList(posts);
        }
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
