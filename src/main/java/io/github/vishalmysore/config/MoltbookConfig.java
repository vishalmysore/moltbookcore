package io.github.vishalmysore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Moltbook integration
 */
@Configuration
@ConfigurationProperties(prefix = "moltbook")
@Data
public class MoltbookConfig {

    private Api api = new Api();
    private Agent agent = new Agent();
    private Heartbeat heartbeat = new Heartbeat();

    @Data
    public static class Api {
        private String baseUrl = "https://www.moltbook.com/api/v1";
        private String key;
    }

    @Data
    public static class Agent {
        private String name = "CarServiceBot";
        private String description = "A helpful AI agent for car services";
    }

    @Data
    public static class Heartbeat {
        private Interval interval = new Interval();

        @Data
        public static class Interval {
            private int minutes = 30;
        }
    }
}
