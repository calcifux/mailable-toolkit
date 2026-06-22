package com.github.calcifux.mailabletoolkit.quarkus.core;

import com.github.calcifux.mailabletoolkit.AssetResolvers;
import com.github.calcifux.mailabletoolkit.LogMailTransport;
import com.github.calcifux.mailabletoolkit.MailRenderer;
import com.github.calcifux.mailabletoolkit.MailTransport;
import com.github.calcifux.mailabletoolkit.Mailable;
import com.github.calcifux.mailabletoolkit.MailerRegistry;
import com.github.calcifux.mailabletoolkit.TemplateRenderer;
import com.github.calcifux.mailabletoolkit.pebble.PebbleTemplateRenderer;
import com.github.calcifux.mailabletoolkit.queue.InMemoryMailQueue;
import com.github.calcifux.mailabletoolkit.queue.MailQueue;
import com.github.calcifux.mailabletoolkit.queue.RetryPolicy;
import com.github.calcifux.mailabletoolkit.quarkus.Mail;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Quarkus / CDI entry point for the mailable toolkit — the backend's {@code @Inject} target and the
 * portable twin of the Spring {@code MailableToolkitAutoConfiguration} + {@code Mail} facade. It reads
 * {@code mailable-toolkit.*} via MicroProfile {@link ConfigProperty} (house style, like
 * {@code RemoteUploadCdiService} — NOT {@code @ConfigMapping}), builds one {@link JakartaMailTransport}
 * per configured mailer into a {@link MailerRegistry}, picks the queue (in-memory default or durable
 * Redis Streams), and exposes {@link #send}/{@link #queue}. It also initializes the static {@link Mail}
 * facade so application code can use either style.
 *
 * <p>Lifecycle is the standard CDI ApplicationScoped lifecycle (NOT {@code StartupEvent}): the queue's
 * background worker is spawned on {@link #onStart} ({@code @Initialized(ApplicationScoped.class)}) and
 * stopped on {@link #onStop} ({@code @PreDestroy}), so the bean stays portable across CDI runtimes.</p>
 *
 * <pre>{@code
 * @Inject MailCdiService mail;
 * mail.send(new WelcomeMail(name), List.of(email));   // sync
 * mail.queue(new RichMail(...), List.of(email));      // async (Redis stream per queue)
 * }</pre>
 */
@Slf4j
@ApplicationScoped
public class MailCdiService {

    private static final String PREFIX = "mailable-toolkit";

    private final Config config;
    private final Instance<RedisDataSource> redis;

    // --- template ---
    private final String templatePrefix;
    private final String templateSuffix;

    // --- from / default mailer ---
    private final String fromEmail;
    private final String fromName;
    private final String defaultMailer;

    // --- queue ---
    private final String queueDriver;
    private final List<String> queueNames;
    private final String defaultQueue;
    private final int maxAttempts;
    private final long baseBackoffMillis;
    private final long maxBackoffMillis;
    private final String keyPrefix;
    private final String consumerGroup;
    private final long blockMillis;

    private volatile com.github.calcifux.mailabletoolkit.Mailer mailer;
    private volatile MailQueue mailQueue;

    @Inject
    public MailCdiService(
            Config config,
            Instance<RedisDataSource> redis,
            // Optional, sin defaultValue="": SmallRye trata un default String vacío como null y truena
            // el arranque (SRCFG00040). Optional<String> + orElse("") es el patrón robusto.
            @ConfigProperty(name = PREFIX + ".template.prefix") Optional<String> templatePrefix,
            @ConfigProperty(name = PREFIX + ".template.suffix", defaultValue = ".peb") String templateSuffix,
            @ConfigProperty(name = PREFIX + ".from.email") Optional<String> fromEmail,
            @ConfigProperty(name = PREFIX + ".from.name") Optional<String> fromName,
            @ConfigProperty(name = PREFIX + ".default-mailer") Optional<String> defaultMailer,
            @ConfigProperty(name = PREFIX + ".queue.driver", defaultValue = "inmemory") String queueDriver,
            @ConfigProperty(name = PREFIX + ".queue.queues", defaultValue = "default") List<String> queueNames,
            @ConfigProperty(name = PREFIX + ".queue.default-queue", defaultValue = "default") String defaultQueue,
            @ConfigProperty(name = PREFIX + ".queue.max-attempts", defaultValue = "3") int maxAttempts,
            @ConfigProperty(name = PREFIX + ".queue.base-backoff-millis", defaultValue = "2000") long baseBackoffMillis,
            @ConfigProperty(name = PREFIX + ".queue.max-backoff-millis", defaultValue = "300000") long maxBackoffMillis,
            @ConfigProperty(name = PREFIX + ".queue.key-prefix", defaultValue = "mailable") String keyPrefix,
            @ConfigProperty(name = PREFIX + ".queue.consumer-group", defaultValue = "mailable-workers") String consumerGroup,
            @ConfigProperty(name = PREFIX + ".queue.block-millis", defaultValue = "2000") long blockMillis) {
        this.config = config;
        this.redis = redis;
        this.templatePrefix = templatePrefix.orElse("");
        this.templateSuffix = templateSuffix;
        this.fromEmail = blankToNull(fromEmail.orElse(""));
        this.fromName = blankToNull(fromName.orElse(""));
        this.defaultMailer = blankToNull(defaultMailer.orElse(""));
        this.queueDriver = queueDriver;
        this.queueNames = queueNames;
        this.defaultQueue = defaultQueue;
        this.maxAttempts = maxAttempts;
        this.baseBackoffMillis = baseBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
        this.keyPrefix = keyPrefix;
        this.consumerGroup = consumerGroup;
        this.blockMillis = blockMillis;
    }

    // --- lifecycle (standard CDI; portable) ---

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        startup();
    }

    /** Build the renderer/registry/mailer/queue and start the worker. Exposed for tests (no CDI container). */
    public synchronized void startup() {
        if (mailer != null) {
            return;
        }
        TemplateRenderer renderer = new PebbleTemplateRenderer(templatePrefix, suffixOr(templateSuffix, ".peb"));
        MailRenderer mailRenderer = new MailRenderer(renderer, AssetResolvers.defaults());
        MailerRegistry registry = buildRegistry();
        this.mailer = new com.github.calcifux.mailabletoolkit.Mailer(mailRenderer, registry, fromEmail, fromName);

        this.mailQueue = buildQueue(this.mailer);
        if (mailQueue instanceof RedisStreamMailQueue redisQueue) {
            redisQueue.start();
        }

        Mail.init(mailer, mailQueue, defaultQueue);
        log.info("[mailable-toolkit] MailCdiService ready (driver={}, mailers={}, queues={})",
                queueDriver, registry.defaultName(), queueNames);
    }

    @PreDestroy
    void onStop() {
        if (mailQueue instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("[mailable-toolkit] error closing mail queue: {}", e.getMessage());
            }
        }
    }

    // --- public API (parity with the static Mail facade) ---

    public void send(Mailable mailable, List<String> to) {
        requireMailer().send(mailable, to);
    }

    public void send(Mailable mailable, List<String> to, List<String> cc, List<String> bcc) {
        requireMailer().send(mailable, to, cc, bcc, null);
    }

    public void send(Mailable mailable, List<String> to, List<String> cc, List<String> bcc, String mailerOverride) {
        requireMailer().send(mailable, to, cc, bcc, mailerOverride);
    }

    public void queue(Mailable mailable, List<String> to) {
        Mail.queue(mailable, to);
    }

    public void queue(Mailable mailable, List<String> to, List<String> cc, List<String> bcc) {
        Mail.queue(mailable, to, cc, bcc);
    }

    /** Pick the named SMTP for this mail. */
    public Mail.Sender mailer(String name) {
        return Mail.mailer(name);
    }

    /** Pick the queue this mail rides. */
    public Mail.Sender onQueue(String queueName) {
        return Mail.onQueue(queueName);
    }

    /** Render the HTML without sending. */
    public String preview(Mailable mailable) {
        return requireMailer().preview(mailable);
    }

    // --- wiring helpers ---

    MailerRegistry buildRegistry() {
        List<MailTransport> transports = new ArrayList<>();
        for (String name : discoverMailerNames()) {
            transports.add(buildSmtp(name));
        }
        if (transports.isEmpty()) {
            log.warn("[mailable-toolkit] no SMTP mailers configured (mailable-toolkit.mailers.*) — "
                    + "falling back to a log transport: mail will be LOGGED, not sent");
            transports.add(new LogMailTransport("log"));
        }
        return new MailerRegistry(transports, defaultMailer);
    }

    private MailQueue buildQueue(com.github.calcifux.mailabletoolkit.Mailer mailer) {
        RetryPolicy retry = new RetryPolicy(maxAttempts, baseBackoffMillis, maxBackoffMillis);
        if ("redis".equalsIgnoreCase(queueDriver)) {
            if (redis.isUnsatisfied()) {
                throw new com.github.calcifux.mailabletoolkit.MailToolkitException(
                        "mailable-toolkit.queue.driver=redis but no RedisDataSource is available — add the "
                                + "quarkus-redis-client extension and configure quarkus.redis.hosts");
            }
            Set<String> queues = new LinkedHashSet<>(queueNames);
            return new RedisStreamMailQueue(redis.get(), mailer::dispatch, retry,
                    keyPrefix, consumerGroup, queues, blockMillis);
        }
        return new InMemoryMailQueue(mailer::dispatch, retry);
    }

    /** Discover mailer names from {@code mailable-toolkit.mailers.<name>.host} keys in the config. */
    Set<String> discoverMailerNames() {
        Set<String> names = new TreeSet<>();
        String mailersPrefix = PREFIX + ".mailers.";
        for (String property : config.getPropertyNames()) {
            if (property.startsWith(mailersPrefix)) {
                String rest = property.substring(mailersPrefix.length());
                int dot = rest.indexOf('.');
                if (dot > 0) {
                    names.add(rest.substring(0, dot));
                }
            }
        }
        return names;
    }

    private JakartaMailTransport buildSmtp(String name) {
        String base = PREFIX + ".mailers." + name + ".";
        String host = get(base + "host", "localhost");
        int port = Integer.parseInt(get(base + "port", "587"));
        String username = get(base + "username", "");
        String password = get(base + "password", "");
        String encryption = get(base + "encryption", "starttls");
        String mailerFromEmail = firstNonBlank(get(base + "from-email", ""), fromEmail);
        String mailerFromName = firstNonBlank(get(base + "from-name", ""), fromName);
        return JakartaMailTransport.smtp(name, host, port,
                blankToNull(username), blankToNull(password), encryption, mailerFromEmail, mailerFromName);
    }

    private com.github.calcifux.mailabletoolkit.Mailer requireMailer() {
        if (mailer == null) {
            startup();
        }
        return mailer;
    }

    private String get(String key, String defaultValue) {
        return config.getOptionalValue(key, String.class).orElse(defaultValue);
    }

    private static String suffixOr(String configured, String engineDefault) {
        return (configured != null && !configured.isBlank()) ? configured : engineDefault;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
