package com.github.calcifux.mailabletoolkit;

/**
 * Base unchecked exception for the toolkit. Unchecked so the jr facade never forces checked-exception
 * handling. Prefer the {@link RetryableMailException} / {@link TerminalMailException} subtypes so the
 * async queue knows whether to retry or dead-letter.
 */
public class MailToolkitException extends RuntimeException {

    public MailToolkitException(String message) {
        super(message);
    }

    public MailToolkitException(String message, Throwable cause) {
        super(message, cause);
    }
}
