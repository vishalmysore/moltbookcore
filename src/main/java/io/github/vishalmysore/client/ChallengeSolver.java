package io.github.vishalmysore.client;

/**
 * Interface for solving Moltbook verification challenges.
 */
@FunctionalInterface
public interface ChallengeSolver {
    /**
     * Solves a challenge and returns the answer as a string.
     * 
     * @param challenge The challenge text from the API
     * @return The solved answer
     * @throws Exception if solving fails
     */
    String solve(String challenge) throws Exception;
}
