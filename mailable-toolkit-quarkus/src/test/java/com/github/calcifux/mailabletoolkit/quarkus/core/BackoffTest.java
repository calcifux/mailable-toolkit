package com.github.calcifux.mailabletoolkit.quarkus.core;

import com.github.calcifux.mailabletoolkit.queue.RetryPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The retry/backoff arithmetic the Redis worker drives — exercised here (no broker) exactly as
 * {@link RedisStreamMailQueue} uses it: {@code canRetry(made)} gates the re-enqueue and
 * {@code backoffMillis(made)} is the schedule delay, capped at the configured max.
 */
class BackoffTest {

    @Test
    void exponential_backoff_doubles_per_attempt_and_caps() {
        RetryPolicy policy = new RetryPolicy(5, 2_000L, 30_000L);

        assertThat(policy.backoffMillis(1)).isEqualTo(2_000L);   // 2000 << 0
        assertThat(policy.backoffMillis(2)).isEqualTo(4_000L);   // 2000 << 1
        assertThat(policy.backoffMillis(3)).isEqualTo(8_000L);   // 2000 << 2
        assertThat(policy.backoffMillis(4)).isEqualTo(16_000L);  // 2000 << 3
        assertThat(policy.backoffMillis(5)).isEqualTo(30_000L);  // 32000 capped to 30000
        assertThat(policy.backoffMillis(20)).isEqualTo(30_000L); // stays capped
    }

    @Test
    void can_retry_until_max_attempts_then_dead_letters() {
        RetryPolicy policy = new RetryPolicy(3, 1_000L, 10_000L);

        assertThat(policy.canRetry(1)).isTrue();
        assertThat(policy.canRetry(2)).isTrue();
        assertThat(policy.canRetry(3)).isFalse();  // 3rd attempt made → no more retries
        assertThat(policy.canRetry(4)).isFalse();
    }
}
