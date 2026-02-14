package io.github.vishalmysore.client;

import io.github.vishalmysore.service.ActivityTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Simplified Moltbook API client using RestTemplate
 */
@Component
@Slf4j
public class MoltbookClient {

    private static final String BASE_URL = "https://www.moltbook.com/api/v1";
    private final ActivityTrackingService activityService;
    private final RestTemplate restTemplate;
    private String apiKey;

    public MoltbookClient(
            @Value("${moltbook.api.key:}") String configuredApiKey,
            ActivityTrackingService activityService) {

        this.restTemplate = new RestTemplate();
        this.activityService = activityService;

        // Try multiple sources for API key (in priority order)
        // 1. JVM system property (-DMOLTBOOK_API_KEY=...)
        String systemPropKey = System.getProperty("MOLTBOOK_API_KEY");
        // 2. Environment variable
        String envKey = System.getenv("MOLTBOOK_API_KEY");
        // 3. Spring application.properties

        if (systemPropKey != null && !systemPropKey.isEmpty()) {
            this.apiKey = systemPropKey;
            log.info("✓ Using MOLTBOOK_API_KEY from -D system property");
        } else if (envKey != null && !envKey.isEmpty()) {
            this.apiKey = envKey;
            log.info("✓ Using MOLTBOOK_API_KEY from environment variable");
        } else if (configuredApiKey != null && !configuredApiKey.isEmpty()) {
            this.apiKey = configuredApiKey;
            log.info("✓ Using moltbook.api.key from application.properties");
        } else {
            this.apiKey = null;
            log.warn("⚠️ No Moltbook API key found!");
            log.warn("   To get started:");
            log.warn("   1. Register: POST https://www.moltbook.com/api/v1/agents/register");
            log.warn("   2. Set via -D flag: -DMOLTBOOK_API_KEY=\"your_key\"");
            log.warn("   3. Or export: export MOLTBOOK_API_KEY=\"your_key\"");
            log.warn("   4. Or add to application.properties: moltbook.api.key=your_key");
        }
    }

    /**
     * Register agent with Moltbook (no API key needed)
     */
    public String registerAgent(String agentName, String description) {
        String requestBody = String.format(
                "{\"name\":\"%s\",\"description\":\"%s\"}",
                escapeJson(agentName), escapeJson(description));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    BASE_URL + "/agents/register",
                    entity,
                    String.class);

            log.info("✓ Agent registered successfully!");
            log.info("📋 IMPORTANT: Save the API key from the response!");
            return response.getBody();

        } catch (Exception e) {
            log.error("Registration failed", e);
            throw new RuntimeException("Failed to register agent: " + e.getMessage(), e);
        }
    }

    /**
     * Get agent claim status
     */
    public String getAgentStatus() {
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("Cannot check status - no API key set");
            return "{\"error\":\"No API key configured\"}";
        }
        return get("/agents/status");
    }

    /**
     * Get personalized feed (subscriptions + follows)
     */
    public String getFeed(int limit) {
        return get("/feed?sort=new&limit=" + limit);
    }

    /**
     * Get global posts feed
     */
    public String getPosts(String sort, int limit) {
        return get("/posts?sort=" + sort + "&limit=" + limit);
    }

    /**
     * Get agent profile
     */
    public String getProfile() {
        return get("/agents/me");
    }

    /**
     * Semantic search for posts
     */
    public String semanticSearch(String query) {
        String encodedQuery = UriUtils.encode(query, StandardCharsets.UTF_8);
        return get("/search?q=" + encodedQuery);
    }

    /**
     * Search with type and limit
     */
    public String semanticSearch(String query, String type, int limit) {
        String encodedQuery = UriUtils.encode(query, StandardCharsets.UTF_8);
        return get("/search?q=" + encodedQuery + "&type=" + type + "&limit=" + limit);
    }

    /**
     * Create a new post
     */
    public String createPost(String submolt, String title, String content) {
        String requestBody = String.format(
                "{\"submolt\":\"%s\",\"title\":\"%s\",\"content\":\"%s\"}",
                escapeJson(submolt), escapeJson(title), escapeJson(content));

        return post("/posts", requestBody);
    }

    /**
     * Create a link post
     */
    public String createLinkPost(String submolt, String title, String url) {
        String requestBody = String.format(
                "{\"submolt\":\"%s\",\"title\":\"%s\",\"url\":\"%s\"}",
                escapeJson(submolt), escapeJson(title), escapeJson(url));
        return post("/posts", requestBody);
    }

    /**
     * Upvote a post
     */
    public void upvote(String postId) {
        post("/posts/" + postId + "/upvote", null);
    }

    /**
     * Downvote a post
     */
    public void downvote(String postId) {
        post("/posts/" + postId + "/downvote", null);
    }

    /**
     * Comment on a post
     */
    public String createComment(String postId, String content) {
        String requestBody = String.format(
                "{\"content\":\"%s\"}",
                escapeJson(content));
        return post("/posts/" + postId + "/comments", requestBody);
    }

    /**
     * Get comments on a post
     */
    public String getComments(String postId, String sort) {
        return get("/posts/" + postId + "/comments?sort=" + sort);
    }

    /**
     * Verify a post by solving the challenge
     */
    public String verifyPost(String verificationCode, String answer) {
        String requestBody = String.format(
                "{\"verification_code\":\"%s\",\"answer\":\"%s\"}",
                escapeJson(verificationCode), escapeJson(answer));
        return post("/verify", requestBody);
    }

    /**
     * Follow another molty
     */
    public void followAgent(String agentName) {
        post("/agents/" + agentName + "/follow", null);
    }

    /**
     * Unfollow a molty
     */
    public void unfollowAgent(String agentName) {
        delete("/agents/" + agentName + "/follow");
    }

    /**
     * Subscribe to a submolt
     */
    public void subscribeToSubmolt(String submoltName) {
        post("/submolts/" + submoltName + "/subscribe", null);
    }

    /**
     * Update agent profile
     */
    public String updateProfile(String description) {
        String requestBody = String.format(
                "{\"description\":\"%s\"}",
                escapeJson(description));
        return patch("/agents/me", requestBody);
    }

    // ====== HTTP Methods ======

    private String get(String path) {
        HttpHeaders headers = authHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + path,
                    HttpMethod.GET,
                    entity,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("GET request failed: {}", path, e);
            throw new RuntimeException("Moltbook API request failed: " + e.getMessage(), e);
        }
    }

    private String post(String path, Object body) {
        HttpHeaders headers = authHeaders();
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    BASE_URL + path,
                    entity,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("POST request failed: {}", path, e);
            throw new RuntimeException("Moltbook API request failed: " + e.getMessage(), e);
        }
    }

    private String patch(String path, Object body) {
        HttpHeaders headers = authHeaders();
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + path,
                    HttpMethod.PATCH,
                    entity,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("PATCH request failed: {}", path, e);
            throw new RuntimeException("Moltbook API request failed: " + e.getMessage(), e);
        }
    }

    private void delete(String path) {
        HttpHeaders headers = authHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(
                    BASE_URL + path,
                    HttpMethod.DELETE,
                    entity,
                    Void.class);
        } catch (Exception e) {
            log.error("DELETE request failed: {}", path, e);
            throw new RuntimeException("Moltbook API request failed: " + e.getMessage(), e);
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Set API key programmatically (useful after registration)
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        log.info("✓ API key updated");
    }

    /**
     * Check if API key is configured
     */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
