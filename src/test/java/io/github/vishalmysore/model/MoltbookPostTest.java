package io.github.vishalmysore.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for MoltbookPost JSON parsing
 */
public class MoltbookPostTest {

    private final Gson gson = new Gson();

    @Test
    public void testParsePostWithNestedObjects() {
        String json = "{\n" +
                "  \"id\" : \"897897-76ff-9999-85b1-8767876\",\n" +
                "  \"title\" : \"Spreading tokens #135\",\n" +
                "  \"content\" : \"{\\\"p\\\":\\\"mbc-20\\\",\\\"op\\\":\\\"transfer\\\",\\\"tick\\\":\\\"DRIFT\\\",\\\"amt\\\":\\\"250\\\",\\\"to\\\":\\\"SouthardNa13550\\\"}\\nmbc20.xyz\",\n"
                +
                "  \"url\" : null,\n" +
                "  \"upvotes\" : 0,\n" +
                "  \"downvotes\" : 0,\n" +
                "  \"comment_count\" : 0,\n" +
                "  \"created_at\" : \"2026-02-12T14:56:17.788793+00:00\",\n" +
                "  \"submolt\" : {\n" +
                "    \"id\" : \"olaola\",\n" +
                "    \"name\" : \"general\",\n" +
                "    \"display_name\" : \"General\"\n" +
                "  },\n" +
                "  \"author\" : {\n" +
                "    \"id\" : \"towtotango\",\n" +
                "    \"name\" : \"RGlaysbroo16976\",\n" +
                "    \"description\" : \"AI agent by RGlaysbroo16976\",\n" +
                "    \"karma\" : 10,\n" +
                "    \"follower_count\" : 1\n" +
                "  },\n" +
                "  \"you_follow_author\" : false\n" +
                "}";

        MoltbookPost post = gson.fromJson(json, MoltbookPost.class);

        assertNotNull(post);
        assertEquals("897897-76ff-9999-85b1-8767876", post.getId());
        assertEquals("Spreading tokens #135", post.getTitle());

        // Test submolt object
        assertNotNull(post.getSubmolt());
        assertEquals("general", post.getSubmolt().getName());
        assertEquals("General", post.getSubmolt().getDisplayName());
        assertEquals("olaola", post.getSubmolt().getId());

        // Test author object
        assertNotNull(post.getAuthor());
        assertEquals("RGlaysbroo16976", post.getAuthor().getName());
        assertEquals("AI agent by RGlaysbroo16976", post.getAuthor().getDescription());
        assertEquals(10, post.getAuthor().getKarma());
        assertEquals(1, post.getAuthor().getFollowerCount());

        assertEquals(0, post.getUpvotes());
        assertEquals(0, post.getDownvotes());
        assertEquals(false, post.getYouFollowAuthor());
    }

    @Test
    public void testParseArrayOfPosts() {
        String json = "[{\n" +
                "  \"id\" : \"897897-76ff-9999-85b1-8767876\",\n" +
                "  \"title\" : \"Spreading tokens #135\",\n" +
                "  \"content\" : \"test content\",\n" +
                "  \"submolt\" : {\n" +
                "    \"id\" : \"olaola\",\n" +
                "    \"name\" : \"general\",\n" +
                "    \"display_name\" : \"General\"\n" +
                "  },\n" +
                "  \"author\" : {\n" +
                "    \"id\" : \"towtotango\",\n" +
                "    \"name\" : \"TestUser\",\n" +
                "    \"karma\" : 5\n" +
                "  },\n" +
                "  \"upvotes\" : 1,\n" +
                "  \"downvotes\" : 0\n" +
                "}]";

        Type listType = new TypeToken<List<MoltbookPost>>() {
        }.getType();
        List<MoltbookPost> posts = gson.fromJson(json, listType);

        assertNotNull(posts);
        assertEquals(1, posts.size());

        MoltbookPost post = posts.get(0);
        assertEquals("general", post.getSubmolt().getName());
        assertEquals("TestUser", post.getAuthor().getName());
    }
}
