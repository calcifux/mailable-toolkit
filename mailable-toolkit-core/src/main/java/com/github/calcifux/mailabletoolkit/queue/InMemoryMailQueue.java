package com.github.calcifux.mailabletoolkit.queue;

import com.github.calcifux.mailabletoolkit.MailToolkitException;
import com.github.calcifux.mailabletoolkit.TerminalMailException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory {@link MailQueue} for dev/tests and single-process apps — no broker. One virtual-thread
 * worker per queue NAME (created on demand), so each mailable rides its own queue. Retries
 * transient failures with backoff per {@link RetryPolicy}; dead-letters terminal/exhausted ones (logged).
 * The Redis Streams adapter (spring module) is the durable, multi-instance counterpart.
 *
 * <p>NOT durable: messages live in heap, lost on restart. Fine for dev/tests; use the Redis adapter
 * for production on the appliance.</p>
 */
@Slf4j
public class InMemoryMailQueue implements MailQueue, AutoCloseable {

    private final MailDispatcher dispatcher;
    private final RetryPolicy retry;
    private final Map<String, BlockingQueue<QueuedMail>> queues = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mailable-retry");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running = true;

    public InMemoryMailQueue(MailDispatcher dispatcher, RetryPolicy retry) {
        this.dispatcher = dispatcher;
        this.retry = retry;
    }

    @Override
    public void enqueue(QueuedMail mail) {
        if (!running) {
            throw new MailToolkitException("mail queue is closed");
        }
        queues.computeIfAbsent(mail.getQueue(), this::startQueue).offer(mail);
    }

    private BlockingQueue<QueuedMail> startQueue(String name) {
        BlockingQueue<QueuedMail> queue = new LinkedBlockingQueue<>();
        workers.submit(() -> drain(name, queue));
        return queue;
    }

    private void drain(String name, BlockingQueue<QueuedMail> queue) {
        log.info("[mailable-toolkit] in-memory queue worker started for '{}'", name);
        while (running) {
            QueuedMail mail;
            try {
                mail = queue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (mail != null) {
                process(mail, queue);
            }
        }
    }

    private void process(QueuedMail mail, BlockingQueue<QueuedMail> queue) {
        try {
            dispatcher.dispatch(mail);
        } catch (TerminalMailException terminal) {
            deadLetter(mail, terminal);
        } catch (RuntimeException transientOrUnexpected) {
            int made = mail.getAttempts() + 1;
            if (retry.canRetry(made)) {
                long delay = retry.backoffMillis(made);
                log.warn("[mailable-toolkit] send failed (attempt {}/{}) on queue '{}', retrying in {}ms: {}",
                        made, retry.maxAttempts(), mail.getQueue(), delay, transientOrUnexpected.getMessage());
                QueuedMail next = mail.nextAttempt(transientOrUnexpected.getMessage());
                scheduler.schedule(() -> queue.offer(next), delay, TimeUnit.MILLISECONDS);
            } else {
                deadLetter(mail, transientOrUnexpected);
            }
        }
    }

    private void deadLetter(QueuedMail mail, Exception cause) {
        log.error("[mailable-toolkit] DEAD-LETTER mail id={} queue={} after {} attempt(s): {}",
                mail.getId(), mail.getQueue(), mail.getAttempts() + 1, cause.getMessage(), cause);
    }

    @Override
    public void close() {
        running = false;
        workers.shutdownNow();
        scheduler.shutdownNow();
    }
}
