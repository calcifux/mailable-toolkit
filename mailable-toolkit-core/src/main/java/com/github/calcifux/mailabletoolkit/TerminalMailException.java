package com.github.calcifux.mailabletoolkit;

/**
 * A PERMANENT failure that retrying will not fix (bad credentials, invalid recipient, missing template
 * or attachment). The async queue does NOT retry — it dead-letters immediately, burning zero attempts.
 */
public class TerminalMailException extends MailToolkitException {

    public TerminalMailException(String message) {
        super(message);
    }

    public TerminalMailException(String message, Throwable cause) {
        super(message, cause);
    }
}
