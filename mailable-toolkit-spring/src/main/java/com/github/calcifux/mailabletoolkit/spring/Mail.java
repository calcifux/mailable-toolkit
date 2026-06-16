package com.github.calcifux.mailabletoolkit.spring;

import com.github.calcifux.mailabletoolkit.MailToolkitException;
import com.github.calcifux.mailabletoolkit.Mailable;
import com.github.calcifux.mailabletoolkit.Mailer;
import com.github.calcifux.mailabletoolkit.queue.MailQueue;
import com.github.calcifux.mailabletoolkit.queue.QueuedMail;

import java.util.List;
import java.util.UUID;

/**
 * Static facade for sending mail — the jr's entry point (like auth-toolkit's {@code Auth}). The
 * autoconfig wires the {@link Mailer} and (optionally) the {@link MailQueue} into it.
 *
 * <pre>{@code
 * Mail.send(new WelcomeMail(name), List.of(email));            // sync
 * Mail.queue(new WelcomeMail(name), List.of(email));           // async (mailable's queue, or default)
 * Mail.mailer("billing").send(new InvoiceMail(id), List.of(email));     // pick the SMTP
 * Mail.onQueue("priority").queue(new AlertMail(x), List.of(email));     // pick the queue
 * String html = Mail.preview(new WelcomeMail(name));           // render only, no send
 * }</pre>
 */
public final class Mail {

    private static volatile Mailer mailer;
    private static volatile MailQueue queue;
    private static volatile String defaultQueue = "default";

    private Mail() {
    }

    /** Wired once by the autoconfig. */
    static void init(Mailer mailer, MailQueue queue, String defaultQueue) {
        Mail.mailer = mailer;
        Mail.queue = queue;
        Mail.defaultQueue = defaultQueue;
    }

    public static void send(Mailable mailable, List<String> to) {
        send(mailable, to, List.of(), List.of());
    }

    public static void send(Mailable mailable, List<String> to, List<String> cc, List<String> bcc) {
        require().send(mailable, to, cc, bcc, null);
    }

    public static void queue(Mailable mailable, List<String> to) {
        queue(mailable, to, List.of(), List.of());
    }

    public static void queue(Mailable mailable, List<String> to, List<String> cc, List<String> bcc) {
        requireQueue().enqueue(toQueuedMail(mailable, to, cc, bcc, null, resolveQueue(mailable, null)));
    }

    /** Render the HTML without sending (QA/preview). */
    public static String preview(Mailable mailable) {
        return require().preview(mailable);
    }

    /** Pick the named SMTP for this mail (overrides Envelope.mailer / default). */
    public static Sender mailer(String name) {
        return new Sender(name, null);
    }

    /** Pick the queue this mail rides (overrides Mailable.queue() / default). */
    public static Sender onQueue(String queueName) {
        return new Sender(null, queueName);
    }

    /** Per-call selectors for mailer (SMTP) and/or queue. */
    public static final class Sender {
        private final String mailerOverride;
        private final String queueOverride;

        private Sender(String mailerOverride, String queueOverride) {
            this.mailerOverride = mailerOverride;
            this.queueOverride = queueOverride;
        }

        public Sender mailer(String name) {
            return new Sender(name, queueOverride);
        }

        public Sender onQueue(String queueName) {
            return new Sender(mailerOverride, queueName);
        }

        public void send(Mailable mailable, List<String> to) {
            send(mailable, to, List.of(), List.of());
        }

        public void send(Mailable mailable, List<String> to, List<String> cc, List<String> bcc) {
            require().send(mailable, to, cc, bcc, mailerOverride);
        }

        public void queue(Mailable mailable, List<String> to) {
            queue(mailable, to, List.of(), List.of());
        }

        public void queue(Mailable mailable, List<String> to, List<String> cc, List<String> bcc) {
            requireQueue().enqueue(toQueuedMail(mailable, to, cc, bcc, mailerOverride, resolveQueue(mailable, queueOverride)));
        }
    }

    private static QueuedMail toQueuedMail(Mailable mailable, List<String> to, List<String> cc, List<String> bcc,
                                           String mailerOverride, String queueName) {
        return QueuedMail.builder()
                .id(UUID.randomUUID().toString())
                .mailable(mailable)
                .to(to)
                .cc(cc)
                .bcc(bcc)
                .mailer(mailerOverride)
                .queue(queueName)
                .attempts(0)
                .enqueuedAtEpochMs(System.currentTimeMillis())
                .build();
    }

    private static String resolveQueue(Mailable mailable, String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        String declared = mailable.queue();
        return (declared != null && !declared.isBlank()) ? declared : defaultQueue;
    }

    private static Mailer require() {
        if (mailer == null) {
            throw new MailToolkitException("Mail facade not initialized — is mailable-toolkit-spring on the classpath?");
        }
        return mailer;
    }

    private static MailQueue requireQueue() {
        if (queue == null) {
            throw new MailToolkitException("No mail queue configured — set mailable-toolkit.queue.driver (inmemory|redis)");
        }
        return queue;
    }
}
