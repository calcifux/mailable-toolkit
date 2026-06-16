package com.github.calcifux.mailabletoolkit;

/**
 * The multi-SMTP SEAM. A named transport that actually sends a {@link RenderedMail}. The Spring starter
 * builds one per configured "mailer" (SMTP server) into a {@link MailerRegistry}; {@code log}/{@code noop}
 * transports ship in core for dev/tests. A mail picks its transport by NAME (per-send override >
 * Envelope.mailer > default) — credentials live in config once, never inline.
 */
public interface MailTransport {

    /**
     * Send the assembled message. Throw {@link RetryableMailException} for transient failures (network,
     * SMTP timeout, 4xx) so the queue retries; {@link TerminalMailException} for permanent ones (bad
     * credentials/recipient) so it goes straight to the dead-letter queue.
     */
    void send(RenderedMail mail);

    /** The name this transport is registered under (e.g. {@code "transactional"}, {@code "billing"}). */
    String name();

    /** This mailer's configured default sender email (used when the Envelope sets none). null = none. */
    default String fromEmail() {
        return null;
    }

    /** This mailer's configured default sender display name. null = none. */
    default String fromName() {
        return null;
    }
}
