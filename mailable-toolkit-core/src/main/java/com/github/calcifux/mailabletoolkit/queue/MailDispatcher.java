package com.github.calcifux.mailabletoolkit.queue;

/**
 * What a queue worker calls to actually deliver a dequeued {@link QueuedMail} (render + transport).
 * Implemented by the Mailer ({@code mailer::dispatch}). Kept as a tiny port so the queue adapters do
 * not depend on the full Mailer and there is no construction cycle.
 */
@FunctionalInterface
public interface MailDispatcher {

    /** Render and send the mail now (synchronously, worker-side). Throws on failure (retryable/terminal). */
    void dispatch(QueuedMail mail);
}
