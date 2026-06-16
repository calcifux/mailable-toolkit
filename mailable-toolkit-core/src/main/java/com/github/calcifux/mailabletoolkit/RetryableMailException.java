package com.github.calcifux.mailabletoolkit;

/**
 * A TRANSIENT failure (network blip, SMTP timeout, connection refused, 4xx greylisting). The async
 * queue retries it with backoff up to max-attempts; only after exhausting them does it dead-letter.
 */
public class RetryableMailException extends MailToolkitException {

    public RetryableMailException(String message) {
        super(message);
    }

    public RetryableMailException(String message, Throwable cause) {
        super(message, cause);
    }
}
