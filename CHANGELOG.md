# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0]

### Added

- **Dynamic mailers (`MailerProvider`)** — resolve an SMTP "mailer" by name at runtime (e.g. from a DB
  table of servers), on top of the static `mailable-toolkit.mailers.*`. The registry resolves **static →
  cache → providers**; dynamic results are cached (`mailable-toolkit.mailer-cache-ttl`, default 5m) with
  `MailerRegistry.evict(name)` on a change. `SmtpMailTransport.smtp(...)` builds a transport from raw SMTP
  settings in one call (what a provider returns). An app can run with **only** dynamic mailers.
- **Pluggable asset sources (`AssetResolver`)** — inline images and attachments can come from
  `classpath:`, `file:` / bare paths (any folder), or `http(s):` URLs — the last covers **pre-signed
  S3 / GCS / Azure links with no cloud SDK**. Register an `AssetResolver` bean for other schemes
  (`s3://`, …); it's consulted before the built-ins. References resolve at **send time** (worker-side when
  queued), so large files never travel through the broker. HTTP `5xx`/network errors are retryable,
  `4xx` terminal.

### Changed

- `MailRenderer` resolves inline/attachment references through the `AssetResolvers` chain (was
  classpath/file only). The raw-bytes paths (`InlineAsset.ofData`, `Envelope.attachData`) are unchanged.

## [0.1.0]

First release — a Laravel-Mailable for Java.

### Added

- **`mailable-toolkit-core`** — framework-agnostic engine, only `jakarta.mail` + slf4j:
  - `Mailable` (abstract, `Serializable`) → `Envelope` model: subject, from/reply-to, recipients,
    a template ref **or** inline source + model vars, a locale, the named mailer, **0..N inline CID
    images** (`InlineAsset`, by classpath/file/bytes) and **0..N attachments** (by path or `byte[]`).
  - `TemplateRenderer` SPI — pluggable templating with template **inheritance** and an inline mode.
  - `MailTransport` / `MailerRegistry` SPI — **named "mailers"** (multi-SMTP); a mail picks its
    server via `mailerOverride > Envelope.mailer > default`.
  - `MailRenderer` — assembles the MIME tree (`multipart/mixed → related → alternative`) with an
    auto plain-text alternative; `Mailer` — render + send + per-call mailer override + preview.
  - `MailQueue` SPI + `InMemoryMailQueue` — async sending; **each mailable rides its own queue**
    (`Mailable.queue()`), retry with backoff (`RetryPolicy`) and a dead-letter on exhaustion. The
    queue carries a serialized `Mailable` *reference* — the worker re-runs build + render + send, so
    attachment bytes never travel through the broker.
- **`mailable-toolkit-pebble`** — Pebble renderer (default engine; `{% extends %}`/`{% block %}`
  inheritance, the closest to milpa/Laravel-Blade templates) + inline mode.
- **`mailable-toolkit-thymeleaf`** — Thymeleaf renderer (fragment inheritance + inline mode).
- **`mailable-toolkit-freemarker`** — FreeMarker renderer (macro/include inheritance, HTML
  auto-escaping) + inline mode.
- **`mailable-toolkit-spring`** — Spring Boot starter:
  - `mailable-toolkit.*` properties: the named-mailer map (multi-SMTP), engine selection, queue
    driver, global from, preview.
  - `MailableToolkitAutoConfiguration` — builds one `SmtpMailTransport` (over `JavaMailSender`) per
    configured mailer, auto-picks the engine on the classpath, and wires the queue.
  - **`RedisMailQueue`** — durable queue over **Redis Streams** (one stream per queue name, consumer
    group + ack, retry + dead-letter stream); the appliance default.
  - The static **`Mail`** facade — `Mail.send(...)`, `Mail.queue(...)`,
    `Mail.mailer("billing").send(...)`, `Mail.onQueue("priority").queue(...)`, `Mail.preview(...)`.

[Unreleased]: https://github.com/calcifux/mailable-toolkit/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/calcifux/mailable-toolkit/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/calcifux/mailable-toolkit/releases/tag/v0.1.0
