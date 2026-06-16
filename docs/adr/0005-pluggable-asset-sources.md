# 0005. Pluggable Asset Sources

- **Status:** Accepted
- **Date:** 2026-06-16

## Context
Inline images (CID) and attachments come from many places: a bundled classpath resource, a file on
disk, an object in S3/GCS/Azure, an SFTP server. Hard-coding each source — especially via heavy cloud
SDKs — would bloat the toolkit and couple it to specific providers. Asset bytes are also large, so when
a mail is queued they must not be dragged through the broker (see ADR-0004).

## Decision
Inline images and attachments resolve through the `AssetResolver` SPI: `supports(String uri)` (usually
by URI scheme) and `resolve(String uri)` → `ResolvedAsset(bytes, contentType, filename)`. The built-in
`AssetResolvers.defaults()` cover `classpath:`, `file:` (a bare path works too), and `http(s):` — and
`https:` alone covers most "from the cloud" cases via **pre-signed S3/GCS/Azure URLs with no SDK**.

Custom schemes (`s3://`, `gcs://`, `sftp:`) are added by registering an `AssetResolver` bean; the
autoconfig stacks app-provided resolvers **before** the built-ins (`withFirst`) so they take
precedence. Resolution happens at **send time** — **worker-side when the mail is queued** — so for a
queued mail only the small URI travels through the broker (inside the serialized `Mailable`) and the
worker fetches the bytes when it sends. A resolver signals transient failure with
`RetryableMailException` (retried by the queue) and permanent failure with `TerminalMailException`
(dead-lettered).

## Consequences
- Any storage backend is reachable through one tiny SPI; `http(s):` handles cloud object storage with
  zero SDK dependency via pre-signed links.
- Large files never sit in the broker — only the URI does — keeping the queue small and replay-safe.
- Custom resolvers compose cleanly and override built-ins, so an app can add `s3://`/`sftp:` without
  forking the toolkit.
- Failure semantics are shared with the queue: resolvers throw the same retryable/terminal exceptions,
  so a flaky download retries and a deleted object dead-letters.
- Trade-off: resolving at send time means an asset that disappears between enqueue and delivery makes
  the send fail (by design — bytes aren't snapshotted into the broker).
- Trade-off: an `http(s):` pre-signed URL can expire before a delayed/retried send runs; the app must
  size URL TTLs against the queue's backoff window.
