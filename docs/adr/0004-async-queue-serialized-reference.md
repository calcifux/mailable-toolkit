# 0004. Async Queue Carries a Serialized Mailable Reference

- **Status:** Accepted
- **Date:** 2026-06-16

## Context
Sending mail synchronously on a request thread is slow and fragile (SMTP latency, transient failures).
Mail needs an async path with retries and a dead-letter on exhaustion. The naive approach — render the
MIME and push the bytes through the broker — bloats the broker with inline images and attachments and
makes payloads awkward to replay. Different kinds of mail (priority alerts vs. bulk) also need
independent throughput.

## Decision
Async send is a pluggable SPI: `MailQueue.enqueue(QueuedMail)`. Two adapters ship — `InMemoryMailQueue`
(core, dev default) and `RedisMailQueue` (spring, Redis Streams, durable).

The queue carries a **serialized `Mailable` reference, not rendered bytes**. A `QueuedMail` holds the
`Serializable` `Mailable` (whose fields are primitives/ids — see ADR-0002 / the `Mailable` contract),
the recipients, the resolved `mailer` and `queue` names, and retry metadata (`attempts`,
`enqueuedAtEpochMs`, `lastError`). The worker **deserializes and re-runs `build()` + render + `send()`
on its side**, so attachment/inline bytes are produced worker-side and never travel the broker. In
`RedisMailQueue` this is Java serialization Base64-encoded into a single `payload` stream field.

**Each mailable rides its own queue.** `Mailable.queue()` declares its default queue; a per-send
`onQueue("x")` overrides it; otherwise it falls to the configured default. `RedisMailQueue` runs one
Redis Stream (`<keyPrefix>:<queue>`) and one virtual-thread worker per queue name, reading through a
consumer group with explicit ack — so several app nodes balance the work.

**Retry with backoff + dead-letter.** A `RetryPolicy` (max attempts, base/max backoff) governs retries.
A `RetryableMailException` (or unexpected error) re-adds the mail with `attempts++` after a backoff
delay; a `TerminalMailException` skips retry. On exhaustion the mail is written to a
`<keyPrefix>:<queue>:dlq` dead-letter stream.

## Consequences
- The broker stays small and replay-safe: only a tiny URI/id-bearing reference is stored, regardless of
  attachment size.
- Per-queue streams + workers give independent throughput and rate limits per concern.
- Retries are bounded and typed; permanent failures dead-letter immediately instead of looping.
- The same SPI admits future Rabbit/SQS adapters with no caller change.
- Trade-off: the `Mailable` **must** be serializable with primitive/id fields — a live JPA entity or DB
  connection in a field breaks enqueue (the encoder throws a clear `MailToolkitException`).
- Trade-off: `build()` + render run twice for a queued mail (once is not enough — the worker re-derives
  everything), so `build()` must be deterministic and side-effect-free.
- Trade-off: Java serialization couples the on-broker payload to class shape; an incompatible `Mailable`
  change can make in-flight messages undecodable (those records are acked and dead-lettered).
