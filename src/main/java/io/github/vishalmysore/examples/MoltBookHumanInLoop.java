package io.github.vishalmysore.examples;

import com.t4a.detect.FeedbackLoop;

import java.util.Map;

/**
 * Interface for human-in-the-loop verification
 * Used when an agent wants to perform a high-risk action
 */
public class MoltBookHumanInLoop implements com.t4a.detect.HumanInLoop {

    public MoltBookHumanInLoop(){

    }

    @Override
    public FeedbackLoop allow(String promptText, String methodName, Map<String, Object> params) {
        return null;
    }

    @Override
    public FeedbackLoop allow(String promptText, String methodName, String params) {
        return null;
    }
}
