package com.github.calcifux.mailabletoolkit.quarkus.core;

import com.github.calcifux.mailabletoolkit.MailToolkitException;
import com.github.calcifux.mailabletoolkit.TerminalMailException;
import com.github.calcifux.mailabletoolkit.queue.MailDispatcher;
import com.github.calcifux.mailabletoolkit.queue.MailQueue;
import com.github.calcifux.mailabletoolkit.queue.QueuedMail;
import com.github.calcifux.mailabletoolkit.queue.RetryPolicy;
import io.quarkus.redis.datasource.RedisDataSource;
import io.vertx.mutiny.redis.client.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Durable {@link MailQueue} backed by Redis Streams over the Quarkus {@link RedisDataSource} command API
 * (the portable twin of the Spring {@code RedisMailQueue}, which uses {@code StringRedisTemplate}). One
 * stream {@code <prefix>:<queue>} per queue — so each mailable rides its own queue. A virtual-thread
 * worker per queue reads via a consumer group ({@code XREADGROUP}), dispatches WORKER-side, and
 * {@code XACK}s. A {@link com.github.calcifux.mailabletoolkit.RetryableMailException} is re-added with
 * attempts++ and exponential backoff; terminal/exhausted mails are dead-lettered to
 * {@code <prefix>:<queue>:dlq}.
 *
 * <p>Commands run through the BLOCKING {@code RedisDataSource.execute(String, String...)} (raw RESP), so
 * this compiles against the provided-scope {@code quarkus-redis-client} with no reactive plumbing. The
 * Base64/ObjectStream payload format matches {@link QueuedMailCodec} (and the Spring adapter).</p>
 *
 * <p>Lifecycle is owned by {@link MailCdiService}: {@link #start()} spawns the workers (on the standard
 * CDI {@code @Initialized(ApplicationScoped.class)} event), {@link #close()} stops them (on
 * {@code @PreDestroy} / {@code @BeforeDestroyed}).</p>
 */
@Slf4j
public class RedisStreamMailQueue implements MailQueue, AutoCloseable {

    private static final String PAYLOAD = "payload";
    private static final String INIT_FIELD = "_init";

    private final RedisDataSource redis;
    private final MailDispatcher dispatcher;
    private final RetryPolicy retry;
    private final String keyPrefix;
    private final String group;
    private final String consumer;
    private final Set<String> queues;
    private final long blockMillis;

    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mailable-redis-retry");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running = false;

    public RedisStreamMailQueue(RedisDataSource redis, MailDispatcher dispatcher, RetryPolicy retry,
                                String keyPrefix, String group, Set<String> queues, long blockMillis) {
        this.redis = redis;
        this.dispatcher = dispatcher;
        this.retry = retry;
        this.keyPrefix = keyPrefix;
        this.group = group;
        this.consumer = "c-" + Long.toHexString(System.nanoTime());
        this.queues = Set.copyOf(queues);
        this.blockMillis = blockMillis;
    }

    /** Create the consumer groups and spawn one virtual-thread worker per queue. Idempotent. */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        for (String queue : queues) {
            String key = streamKey(queue);
            ensureGroup(key);
            workers.submit(() -> drain(queue, key));
        }
    }

    @Override
    public void enqueue(QueuedMail mail) {
        add(streamKey(mail.getQueue()), mail);
    }

    private void drain(String queue, String key) {
        log.info("[mailable-toolkit] redis queue worker started for '{}' (stream {})", queue, key);
        while (running) {
            try {
                // XREADGROUP GROUP <group> <consumer> COUNT 1 BLOCK <ms> STREAMS <key> >
                Response reply = redis.execute("XREADGROUP", "GROUP", group, consumer,
                        "COUNT", "1", "BLOCK", String.valueOf(blockMillis), "STREAMS", key, ">");
                if (reply == null || reply.size() == 0) {
                    continue;
                }
                forEachRecord(reply, (id, payload) -> process(key, id, payload));
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
                // NOGROUP: el consumer group no existe. Pasa por timing de arranque (el RedisDataSource
                // todavía no estaba listo cuando start() llamó a ensureGroup). Auto-sanado: lo (re)crea
                // —desde 0, así recoge los mensajes ya encolados— y reintenta sin dormir.
                if (e.getMessage() != null && e.getMessage().contains("NOGROUP")) {
                    ensureGroup(key);
                } else {
                    log.warn("[mailable-toolkit] redis read error on '{}': {}", queue, e.getMessage());
                    sleep(1000);
                }
            }
        }
    }

    /** Walk an XREADGROUP reply: [ [streamKey, [ [id, [field,value,...]], ... ]] ]. */
    private void forEachRecord(Response reply, RecordConsumer consumer) {
        for (Response stream : reply) {
            Response entries = stream.get(1);
            if (entries == null) {
                continue;
            }
            for (Response entry : entries) {
                String id = entry.get(0).toString();
                Response fields = entry.get(1);
                String payload = null;
                // fields is a flat [name, value, name, value, ...] array
                int n = fields == null ? 0 : fields.size();
                for (int i = 0; i + 1 < n; i += 2) {
                    if (PAYLOAD.equals(fields.get(i).toString())) {
                        payload = fields.get(i + 1).toString();
                        break;
                    }
                }
                consumer.accept(id, payload);
            }
        }
    }

    private void process(String key, String id, String payload) {
        if (payload == null) {
            // Stream-creation placeholder ("_init"). Ack and skip quietly.
            ack(key, id);
            return;
        }
        QueuedMail mail;
        try {
            mail = QueuedMailCodec.decode(payload);
        } catch (RuntimeException badPayload) {
            log.error("[mailable-toolkit] undecodable queue record {} -> dead-letter", id, badPayload);
            ack(key, id);
            return;
        }
        try {
            dispatcher.dispatch(mail);
            ack(key, id);
        } catch (TerminalMailException terminal) {
            ack(key, id);
            deadLetter(mail, terminal);
        } catch (RuntimeException transientOrUnexpected) {
            ack(key, id);
            int made = mail.getAttempts() + 1;
            if (retry.canRetry(made)) {
                long delay = retry.backoffMillis(made);
                QueuedMail next = mail.nextAttempt(transientOrUnexpected.getMessage());
                log.warn("[mailable-toolkit] send failed (attempt {}/{}) on '{}', retrying in {}ms: {}",
                        made, retry.maxAttempts(), mail.getQueue(), delay, transientOrUnexpected.getMessage());
                scheduler.schedule(() -> {
                    if (running) {
                        add(key, next);
                    }
                }, delay, TimeUnit.MILLISECONDS);
            } else {
                deadLetter(mail, transientOrUnexpected);
            }
        }
    }

    private void deadLetter(QueuedMail mail, Exception cause) {
        log.error("[mailable-toolkit] DEAD-LETTER mail id={} queue={} after {} attempt(s): {}",
                mail.getId(), mail.getQueue(), mail.getAttempts() + 1, cause.getMessage());
        try {
            add(streamKey(mail.getQueue()) + ":dlq", mail.nextAttempt(cause.getMessage()));
        } catch (RuntimeException e) {
            log.error("[mailable-toolkit] could not write dead-letter for {}", mail.getId(), e);
        }
    }

    private void add(String key, QueuedMail mail) {
        try {
            // XADD <key> * payload <base64>
            redis.execute("XADD", key, "*", PAYLOAD, QueuedMailCodec.encode(mail));
        } catch (RuntimeException e) {
            throw new MailToolkitException("Redis enqueue failed for stream " + key, e);
        }
    }

    private void ack(String key, String id) {
        try {
            redis.execute("XACK", key, group, id);
        } catch (RuntimeException e) {
            log.warn("[mailable-toolkit] XACK failed for {} on {}: {}", id, key, e.getMessage());
        }
    }

    private void ensureGroup(String key) {
        try {
            // A consumer group needs the stream to exist. Create it with a throwaway placeholder ONLY when
            // missing (mirrors the Spring adapter) so existing streams never pile up "_init" entries.
            Response exists = redis.execute("EXISTS", key);
            if (exists == null || exists.toInteger() == 0) {
                redis.execute("XADD", key, "*", INIT_FIELD, "1");
            }
            // Desde 0 (no $): si el grupo se crea DESPUÉS de que ya se encolaron mensajes (timing de
            // arranque), arrancar en $ los perdería. En 0 recoge todo; el placeholder "_init" (sin
            // payload) se ignora en el dispatch.
            redis.execute("XGROUP", "CREATE", key, group, "0");
        } catch (RuntimeException busyGroupOrRace) {
            // BUSYGROUP — the group already exists (restart / concurrent worker). Nothing to do.
            log.debug("[mailable-toolkit] consumer group on {} already present: {}", key, busyGroupOrRace.getMessage());
        }
    }

    private String streamKey(String queue) {
        return keyPrefix + ":" + (queue == null || queue.isBlank() ? "default" : queue);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        running = false;
        workers.shutdownNow();
        scheduler.shutdownNow();
    }

    @FunctionalInterface
    private interface RecordConsumer {
        void accept(String id, String payload);
    }
}
