package com.github.calcifux.mailabletoolkit.queue;

import com.github.calcifux.mailabletoolkit.Mailable;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

/**
 * A mail parked for async delivery: a REFERENCE, not a rendered message. It carries the
 * {@link Serializable} {@link Mailable} (whose fields are primitives/ids), the recipients, the resolved
 * {@code mailer} (SMTP name) and {@code queue} (which queue it rides), plus retry metadata. The worker
 * deserializes it and runs {@code build()+render()+send()} on its side — so attachment/inline bytes are
 * produced worker-side and never bloat the broker.
 */
@Getter
@Builder(toBuilder = true)
public class QueuedMail implements Serializable {

    private final String id;
    private final Mailable mailable;
    private final List<String> to;
    private final List<String> cc;
    private final List<String> bcc;

    /** Named SMTP override (null → Envelope.mailer → default). */
    private final String mailer;
    /** The queue this mail rides (resolved: per-send override → Mailable.queue() → default). */
    private final String queue;

    private final int attempts;
    private final long enqueuedAtEpochMs;
    private final String lastError;

    /** A copy with attempts incremented and the last error recorded (for a retry re-enqueue). */
    public QueuedMail nextAttempt(String error) {
        return toBuilder().attempts(attempts + 1).lastError(error).build();
    }
}
