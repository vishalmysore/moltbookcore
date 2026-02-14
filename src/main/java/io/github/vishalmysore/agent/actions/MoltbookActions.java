package io.github.vishalmysore.agent.actions;

import com.t4a.annotations.Action;
import com.t4a.annotations.Agent;
import io.github.vishalmysore.model.MoltbookPost;
import io.github.vishalmysore.service.MoltbookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Moltbook-related actions that the agent can perform
 * These allow the agent to interact with Moltbook through conversation
 */
@Agent(groupName = "MoltbookAgent", groupDescription = "AI agent that interacts with Moltbook social network - posts, searches, comments, and engages with other AI agents")
@Component
@Slf4j
public class MoltbookActions {

    private final MoltbookService moltbookService;

    public MoltbookActions(MoltbookService moltbookService) {
        this.moltbookService = moltbookService;
    }

    @Action(description = "Create a new post on Moltbook social network. Post to a specific submolt community like 'general' or 'aithoughts'. Requires a title and content for the post. Returns confirmation with post ID")
    public String createMoltbookPost(String submolt, String title, String content) {

        try {
            log.info("Creating Moltbook post in m/{}: {}", submolt, title);
            MoltbookPost post = moltbookService.createPost(submolt, title, content, null);
            return String.format("✅ Posted to m/%s: '%s' (ID: %s)", submolt, title, post.getId());
        } catch (Exception e) {
            log.error("Failed to create post", e);
            return "❌ Failed to create post: " + e.getMessage();
        }
    }

    @Action(description = "Get recent posts from Moltbook feed showing title, author, submolt, and vote counts. Specify the number of posts to retrieve (maximum 25). Returns formatted list of posts")
    public String getMoltbookFeed(int limit) {

        try {
            log.info("Getting Moltbook feed (limit: {})", limit);
            List<MoltbookPost> posts = moltbookService.getFeed("hot", Math.min(limit, 25));

            if (posts.isEmpty()) {
                return "No posts found in feed.";
            }

            return posts.stream()
                    .map(p -> String.format("📝 [%s] %s by @%s (↑%d ↓%d)",
                            p.getSubmolt() != null ? p.getSubmolt().getName() : "unknown",
                            p.getTitle(),
                            p.getAuthor() != null ? p.getAuthor().getName() : "unknown",
                            p.getUpvotes(), p.getDownvotes()))
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            log.error("Failed to get feed", e);
            return "❌ Failed to get feed: " + e.getMessage();
        }
    }

    @Action(description = "Search for posts on Moltbook using AI-powered semantic search that understands meaning, not just keywords. Provide a natural language query about topics you want to find. Specify number of results (max 20). Returns relevant posts with titles, authors, and upvotes")
    public String searchMoltbookPosts(String query, int limit) {

        try {
            log.info("Searching Moltbook for: {}", query);
            List<MoltbookPost> posts = moltbookService.searchPosts(query, Math.min(limit, 20));

            if (posts.isEmpty()) {
                return "No posts found matching: " + query;
            }

            return "🔍 Search results for '" + query + "':\n\n" +
                    posts.stream()
                            .map(p -> String.format("• %s by @%s (↑%d) - m/%s",
                                    p.getTitle(),
                                    p.getAuthor() != null ? p.getAuthor().getName() : "unknown",
                                    p.getUpvotes(),
                                    p.getSubmolt() != null ? p.getSubmolt().getName() : "unknown"))
                            .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            log.error("Failed to search posts", e);
            return "❌ Search failed: " + e.getMessage();
        }
    }

    @Action(description = "Comment on a specific Moltbook post by post ID. Provide the post ID and your comment content. Returns confirmation when comment is successfully posted")
    public String commentOnPost(String postId, String comment) {

        try {
            log.info("Commenting on post {}", postId);
            moltbookService.createComment(postId, comment);
            return "✅ Comment posted successfully!";
        } catch (Exception e) {
            log.error("Failed to comment", e);
            return "❌ Failed to comment: " + e.getMessage();
        }
    }

    @Action(description = "Upvote a Moltbook post by post ID to show you like or agree with the content. Returns confirmation with the Moltbook lobster emoji")
    public String upvotePost(String postId) {

        try {
            log.info("Upvoting post {}", postId);
            moltbookService.upvotePost(postId);
            return "✅ Post upvoted! 🦞";
        } catch (Exception e) {
            log.error("Failed to upvote", e);
            return "❌ Failed to upvote: " + e.getMessage();
        }
    }

    @Action(description = "Describe all capabilities and features of this CarServiceBot agent including car services and Moltbook integration. Use this when other agents ask what you can do or help with")
    public String describeCapabilities() {
        return "🦞 Hi! I'm CarServiceBot on Moltbook!\n\n" +
                "I can help with:\n" +
                "🚗 Car Services:\n" +
                "  • Get detailed info about car models (Tesla, BMW, Toyota, etc.)\n" +
                "  • Compare two cars side-by-side\n" +
                "  • Get pricing for different car types (electric, hybrid, gas)\n" +
                "  • List all available car models\n" +
                "  • Check booking status for service appointments\n\n" +
                "📱 Moltbook Integration:\n" +
                "  • Create posts and share insights\n" +
                "  • Search posts semantically (understands meaning!)\n" +
                "  • Comment on and upvote posts\n" +
                "  • Browse the feed for interesting content\n\n" +
                "💬 I'm built with Tools4AI and Spring Boot, so I can understand natural language!\n" +
                "Just ask me anything about cars or Moltbook!";
    }
}
