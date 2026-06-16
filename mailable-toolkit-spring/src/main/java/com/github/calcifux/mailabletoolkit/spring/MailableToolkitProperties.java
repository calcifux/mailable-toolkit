package com.github.calcifux.mailabletoolkit.spring;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Config for the toolkit, bound from {@code mailable-toolkit.*}.
 *
 * <pre>{@code
 * mailable-toolkit:
 *   template: { engine: pebble, prefix: "mail/", suffix: ".peb" }
 *   from: { email: noreply@corp.com, name: Corp }   # global fallback sender
 *   default-mailer: transactional
 *   mailers:
 *     transactional: { host: smtp.internal, port: 587, username: u, password: ${SMTP_PW}, encryption: starttls }
 *     billing:       { host: smtp.vendor.com, port: 465, username: b, password: ${BILLING_PW}, encryption: ssl, from-email: billing@corp.com }
 *   queue: { driver: redis, queues: [default, priority], default-queue: default, max-attempts: 3 }
 *   preview: { enabled: true }
 * }</pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mailable-toolkit")
public class MailableToolkitProperties {

    private Template template = new Template();
    private From from = new From();
    private String defaultMailer;
    private Map<String, Mailer> mailers = new LinkedHashMap<>();
    private Queue queue = new Queue();
    private Preview preview = new Preview();

    /** Which templating engine + where templates live. */
    @Getter
    @Setter
    public static class Template {
        /** Engine id when several adapters are on the classpath ({@code pebble}/{@code thymeleaf}/{@code freemarker}); auto-picked if exactly one. */
        private String engine;
        /** Classpath prefix for template refs (e.g. {@code "mail/"}). */
        private String prefix = "";
        /** Suffix for template files (engine default if null: .peb / .html / .ftlh). */
        private String suffix;
    }

    /** Global fallback sender (used when neither the Envelope nor the chosen mailer sets a from). */
    @Getter
    @Setter
    public static class From {
        private String email;
        private String name;
    }

    /** One named SMTP server ("mailer"). */
    @Getter
    @Setter
    public static class Mailer {
        private String host;
        private int port = 587;
        private String username;
        private String password;
        /** {@code starttls} | {@code ssl} | {@code none}. */
        private String encryption = "starttls";
        /** This mailer's default sender (overrides the global from for mails it sends). */
        private String fromEmail;
        private String fromName;
    }

    /** Async queue settings. */
    @Getter
    @Setter
    public static class Queue {
        /** {@code inmemory} (default, dev) | {@code redis} (durable, appliance). */
        private String driver = "inmemory";
        /** Queue names this instance's worker processes. */
        private List<String> queues = List.of("default");
        /** Queue used when a mailable declares none. */
        private String defaultQueue = "default";
        private int maxAttempts = 3;
        private long baseBackoffMillis = 2_000L;
        private long maxBackoffMillis = 300_000L;
        /** Redis key prefix for the streams ({@code <prefix>:<queue>}). */
        private String keyPrefix = "mailable";
        /** Redis consumer group name. */
        private String consumerGroup = "mailable-workers";
    }

    /** Render-without-send preview. */
    @Getter
    @Setter
    public static class Preview {
        private boolean enabled = false;
    }
}
