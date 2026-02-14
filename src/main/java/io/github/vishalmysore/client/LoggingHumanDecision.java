package io.github.vishalmysore.client;

import com.t4a.detect.FeedbackLoop;
import com.t4a.detect.HumanInLoop;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

/**
 * Default implementation of HumanInLoop that logs requests and blocks high-risk
 * actions.
 * Useful for headless agents or development.
 */
@Slf4j
public class LoggingHumanDecision implements HumanInLoop {

    @Override
    public FeedbackLoop allow(String promptText, String methodName, Map<String, Object> params) {
        log.info("🚨 HUMAN-IN-LOOP REQUEST 🚨");
        log.info("Prompt: {}", promptText);
        log.info("Requested Action: {}", methodName);
        log.info("Parameters: {}", params);
        log.info("---------------------------");
        log.info("Action BLOCKED by default in LoggingHumanDecision. Implementation needed for autonomous approval.");

        // Return null or a non-success FeedbackLoop to block by default
        return null;
    }

    @Override
    public FeedbackLoop allow(String promptText, String methodName, String params) {
        log.info("🚨 HUMAN-IN-LOOP REQUEST 🚨");
        log.info("Prompt: {}", promptText);
        log.info("Requested Action: {}", methodName);
        log.info("Parameters (JSON): {}", params);
        log.info("---------------------------");

        return null;
    }
}
