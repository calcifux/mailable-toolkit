package com.github.calcifux.mailabletoolkit.queue;

/**
 * Retry policy shared by the queue adapters: how many attempts and the exponential backoff between
 * them. A {@code RetryableMailException} is retried up to {@code maxAttempts}; a
 * {@code TerminalMailException} skips retries and dead-letters immediately (the adapter decides).
 *
 * @param maxAttempts        total attempts before dead-lettering (e.g. 3)
 * @param baseBackoffMillis  first backoff (e.g. 2000)
 * @param maxBackoffMillis   cap (e.g. 300000)
 */
public record RetryPolicy(int maxAttempts, long baseBackoffMillis, long maxBackoffMillis) {

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, 2_000L, 300_000L);
    }

    public boolean canRetry(int attempts) {
        return attempts < maxAttempts;
    }

    /** Exponential backoff for the given (already-made) attempt count, capped. */
    public long backoffMillis(int attempts) {
        int shift = Math.min(Math.max(attempts - 1, 0), 16);
        long backoff = baseBackoffMillis << shift;
        return Math.min(backoff, maxBackoffMillis);
    }
}
