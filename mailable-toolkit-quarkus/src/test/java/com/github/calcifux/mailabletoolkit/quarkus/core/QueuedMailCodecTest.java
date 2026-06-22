package com.github.calcifux.mailabletoolkit.quarkus.core;

import com.github.calcifux.mailabletoolkit.queue.QueuedMail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Redis stream payload format: a {@link QueuedMail} (carrying its serializable {@link com.github.calcifux.mailabletoolkit.Mailable})
 * survives an ObjectStream+Base64 round-trip. Same wire format as the Spring adapter, so a payload written
 * by one is readable by the other.
 */
class QueuedMailCodecTest {

    @Test
    void round_trips_a_queued_mail_with_its_mailable() {
        QueuedMail original = QueuedMail.builder()
                .id("abc-123")
                .mailable(new WelcomeMail("Calcifux"))
                .to(List.of("dest@example.com"))
                .cc(List.of())
                .bcc(List.of())
                .mailer("billing")
                .queue("priority")
                .attempts(1)
                .enqueuedAtEpochMs(1_700_000_000_000L)
                .lastError("boom")
                .build();

        QueuedMail decoded = QueuedMailCodec.decode(QueuedMailCodec.encode(original));

        assertThat(decoded.getId()).isEqualTo("abc-123");
        assertThat(decoded.getTo()).containsExactly("dest@example.com");
        assertThat(decoded.getMailer()).isEqualTo("billing");
        assertThat(decoded.getQueue()).isEqualTo("priority");
        assertThat(decoded.getAttempts()).isEqualTo(1);
        assertThat(decoded.getEnqueuedAtEpochMs()).isEqualTo(1_700_000_000_000L);
        assertThat(decoded.getLastError()).isEqualTo("boom");
        // the mailable survived and still builds the same envelope
        assertThat(decoded.getMailable()).isInstanceOf(WelcomeMail.class);
        assertThat(decoded.getMailable().build().getSubject()).isEqualTo("Welcome, Calcifux");
        assertThat(decoded.getMailable().queue()).isEqualTo("priority");
    }

    @Test
    void next_attempt_is_preserved_through_a_round_trip() {
        QueuedMail original = QueuedMail.builder()
                .id("id-1").mailable(new WelcomeMail("X")).to(List.of("a@b.c"))
                .queue("default").attempts(0).enqueuedAtEpochMs(1L).build();

        QueuedMail retried = original.nextAttempt("smtp down");
        QueuedMail decoded = QueuedMailCodec.decode(QueuedMailCodec.encode(retried));

        assertThat(decoded.getAttempts()).isEqualTo(1);
        assertThat(decoded.getLastError()).isEqualTo("smtp down");
    }
}
