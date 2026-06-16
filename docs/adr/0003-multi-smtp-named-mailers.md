# 0003. Multi-SMTP Named Mailers

- **Status:** Accepted
- **Date:** 2026-06-16

## Context
A single app often sends through more than one SMTP server — transactional mail from the internal
relay, billing mail from a vendor with its own sender address, per-tenant servers stored in a database.
Credentials must live in config once (never inline in a `Mailable`), and a mail must be able to pick
*which* server it goes out from in a single, predictable way.

## Decision
SMTP is a pluggable SPI: `MailTransport`, a **named** transport that sends a `RenderedMail` and exposes
`name()` plus optional per-mailer `fromEmail()`/`fromName()`. Named transports are held in a
`MailerRegistry` with **one** resolution rule, applied everywhere:

> per-send `mailerOverride` > `Envelope.mailer` > configured `default-mailer`

Only the **name** is ever passed around; credentials stay inside each transport. The Spring starter
builds one `SmtpMailTransport` per `mailable-toolkit.mailers.*` entry into the registry. `defaultName`
is resolved smartly: an explicit `default-mailer` wins; a single static mailer becomes the default; an
empty static set with only providers means "caller must name a mailer"; several static mailers with no
default is an error.

**Dynamic mailers** (e.g. an SMTP table) plug in via the `MailerProvider` SPI
(`MailTransport resolve(String name)`, returning `null` for names it doesn't own). The registry
resolves in order: **static mailers → short-TTL cache → providers**. Resolved dynamic transports are
cached for `mailable-toolkit.mailer-cache-ttl` (default `5m`; `0` disables); `registry.evict(name)`
drops a cached entry when its backing row changes. An app can run with *only* dynamic mailers by always
naming the mailer per send.

## Consequences
- One resolution rule means a mail's SMTP choice is predictable from any entry point
  (`Mail.mailer("billing").send(...)`, `Envelope.mailer`, or the default).
- Credentials are centralized in config/the transport, never in mailable code.
- Dynamic, runtime-resolved mailers (multi-tenant SMTP from a DB) are supported without code changes,
  with caching to avoid a lookup per send.
- Trade-off: the cache means a changed DB row is stale until TTL expiry unless `evict(name)` is called.
- Trade-off: static mailers always win, so a provider can never override a name already declared in
  config (intentional, but a footgun if a name collides).
- Trade-off: with multiple static mailers and no `default-mailer`, startup fails fast — the operator
  must choose a default.
