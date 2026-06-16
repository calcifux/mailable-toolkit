package com.github.calcifux.mailabletoolkit.spring;

import com.github.calcifux.mailabletoolkit.MailToolkitException;
import com.github.calcifux.mailabletoolkit.TerminalMailException;
import com.github.calcifux.mailabletoolkit.queue.MailDispatcher;
import com.github.calcifux.mailabletoolkit.queue.MailQueue;
import com.github.calcifux.mailabletoolkit.queue.QueuedMail;
import com.github.calcifux.mailabletoolkit.queue.RetryPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Durable {@link MailQueue} backed by Redis Streams (one stream {@code <prefix>:<queue>} per queue, so
 * each mailable rides its own queue) — the appliance default (Valkey/Redis already in the stack). A
 * virtual-thread worker per queue reads via a consumer group, dispatches WORKER-side, and acks. Retries
 * a {@link com.github.calcifux.mailabletoolkit.RetryableMailException} by re-adding with attempts++ and
 * backoff; dead-letters terminal/exhausted mails to {@code <prefix>:<queue>:dlq}.
 */
@Slf4j
public class RedisMailQueue implements MailQueue, AutoCloseable {

    private static final String PAYLOAD = "payload";

    private final StringRedisTemplate redis;
    private final MailDispatcher dispatcher;
    private final RetryPolicy retry;
    private final String keyPrefix;
    private final String group;
    private final String consumer;

    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mailable-redis-retry");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running = true;

    public RedisMailQueue(StringRedisTemplate redis, MailDispatcher dispatcher, RetryPolicy retry,
                          String keyPrefix, String group, Set<String> queues) {
        this.redis = redis;
        this.dispatcher = dispatcher;
        this.retry = retry;
        this.keyPrefix = keyPrefix;
        this.group = group;
        this.consumer = "c-" + Long.toHexString(System.nanoTime());
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
        Consumer consumerId = Consumer.from(group, consumer);
        StreamReadOptions options = StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2));
        while (running) {
            try {
                List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                        .read(consumerId, options, StreamOffset.create(key, ReadOffset.lastConsumed()));
                if (records == null) {
                    continue;
                }
                for (MapRecord<String, Object, Object> record : records) {
                    process(key, record);
                }
            } catch (RuntimeException e) {
                log.warn("[mailable-toolkit] redis read error on '{}': {}", queue, e.getMessage());
                sleep(1000);
            }
        }
    }

    private void process(String key, MapRecord<String, Object, Object> record) {
        QueuedMail mail;
        try {
            mail = decode(String.valueOf(record.getValue().get(PAYLOAD)));
        } catch (RuntimeException badPayload) {
            log.error("[mailable-toolkit] undecodable queue record {} → dead-letter", record.getId(), badPayload);
            redis.opsForStream().acknowledge(group, record);
            return;
        }
        try {
            dispatcher.dispatch(mail);
            redis.opsForStream().acknowledge(group, record);
        } catch (TerminalMailException terminal) {
            redis.opsForStream().acknowledge(group, record);
            deadLetter(mail, terminal);
        } catch (RuntimeException transientOrUnexpected) {
            redis.opsForStream().acknowledge(group, record);
            int made = mail.getAttempts() + 1;
            if (retry.canRetry(made)) {
                long delay = retry.backoffMillis(made);
                QueuedMail next = mail.nextAttempt(transientOrUnexpected.getMessage());
                log.warn("[mailable-toolkit] send failed (attempt {}/{}) on '{}', retrying in {}ms: {}",
                        made, retry.maxAttempts(), mail.getQueue(), delay, transientOrUnexpected.getMessage());
                scheduler.schedule(() -> add(key, next), delay, TimeUnit.MILLISECONDS);
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
            redis.opsForStream().add(key, Map.of(PAYLOAD, encode(mail)));
        } catch (RuntimeException e) {
            throw new MailToolkitException("Redis enqueue failed for stream " + key, e);
        }
    }

    private void ensureGroup(String key) {
        try {
            redis.opsForStream().createGroup(key, ReadOffset.latest(), group);
        } catch (RuntimeException existsOrNoStream) {
            // BUSYGROUP (already there) → fine. No stream yet → create it, then the group.
            try {
                redis.opsForStream().add(key, Map.of("_init", "1"));
                redis.opsForStream().createGroup(key, ReadOffset.latest(), group);
            } catch (RuntimeException ignored) {
                // group exists now (concurrent worker / restart)
            }
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

    // --- (de)serialization: the QueuedMail (incl. the Serializable Mailable) ---

    static String encode(QueuedMail mail) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(mail);
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            throw new MailToolkitException("Could not serialize QueuedMail (are the Mailable's fields serializable?)", e);
        }
    }

    static QueuedMail decode(String base64) {
        byte[] data = Base64.getDecoder().decode(base64);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (QueuedMail) in.readObject();
        } catch (Exception e) {
            throw new MailToolkitException("Could not deserialize QueuedMail", e);
        }
    }
}
