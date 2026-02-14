package io.github.vishalmysore.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class ActivityTrackingService {

    private final ConcurrentLinkedDeque<Activity> activities = new ConcurrentLinkedDeque<>();
    private static final int MAX_ACTIVITIES = 100;

    public void trackPost(String postId, String title, String content) {
        trackPost(postId, title, content, true);
    }

    public void trackPost(String postId, String title, String content, boolean success) {
        Activity activity = new Activity();
        activity.type = "POST";
        activity.postId = postId;
        activity.title = title;
        activity.content = content;
        activity.status = success ? "SUCCESS" : "FAILED";
        activity.timestamp = LocalDateTime.now();
        addActivity(activity);
    }

    public void trackComment(String postId, String postTitle, String comment) {
        trackComment(postId, postTitle, comment, true);
    }

    public void trackComment(String postId, String postTitle, String comment, boolean success) {
        Activity activity = new Activity();
        activity.type = "COMMENT";
        activity.postId = postId;
        activity.title = postTitle;
        activity.content = comment;
        activity.status = success ? "SUCCESS" : "FAILED";
        activity.timestamp = LocalDateTime.now();
        addActivity(activity);
    }

    public void trackObservation(String postId, String postTitle) {
        Activity activity = new Activity();
        activity.type = "OBSERVE";
        activity.postId = postId;
        activity.title = postTitle;
        activity.content = "Observed but took no action";
        activity.timestamp = LocalDateTime.now();
        addActivity(activity);
    }

    public void trackError(String errorMessage) {
        Activity activity = new Activity();
        activity.type = "ERROR";
        activity.title = "Error occurred";
        activity.content = errorMessage;
        activity.timestamp = LocalDateTime.now();
        addActivity(activity);
    }

    private void addActivity(Activity activity) {
        activities.addFirst(activity);
        if (activities.size() > MAX_ACTIVITIES) {
            activities.removeLast();
        }
    }

    public List<Activity> getRecentActivities() {
        return new ArrayList<>(activities);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        long posts = activities.stream().filter(a -> "POST".equals(a.type)).count();
        long comments = activities.stream().filter(a -> "COMMENT".equals(a.type)).count();
        long observations = activities.stream().filter(a -> "OBSERVE".equals(a.type)).count();
        long errors = activities.stream().filter(a -> "ERROR".equals(a.type)).count();

        stats.put("totalPosts", posts);
        stats.put("totalComments", comments);
        stats.put("totalObservations", observations);
        stats.put("totalErrors", errors);
        stats.put("totalActivities", activities.size());

        if (!activities.isEmpty()) {
            stats.put("lastActivity", activities.getFirst().timestamp.format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } else {
            stats.put("lastActivity", "No activity yet");
        }

        return stats;
    }

    public static class Activity {
        public String type;
        public String postId;
        public String title;
        public String content;
        public String status; // SUCCESS, FAILED
        public LocalDateTime timestamp;

        public String getFormattedTime() {
            return timestamp.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss"));
        }

        public String getShortContent() {
            if (content == null)
                return "";
            return content.length() > 200 ? content.substring(0, 200) + "..." : content;
        }
    }
}
