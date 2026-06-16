# 0007. Spring Boot Starter and Static Mail Facade

- **Status:** Accepted
- **Date:** 2026-06-16

## Context
The core is wireable by hand (a CLI/job can `new` up a `MailRenderer`, `MailerRegistry`, and `Mailer`),
but the common consumer is a Spring Boot app. That app wants two things: zero-ceremony wiring from
`application.yml`, and a one-liner send call a junior can use without injecting beans everywhere — the
same ergonomics as auth-toolkit's `Auth`. It must also stay overridable so an app can replace any
moving part.

## Decision
Ship a Spring Boot starter, `mailable-toolkit-spring`, with `MailableToolkitAutoConfiguration` driven by
`mailable-toolkit.*` properties. It:

- Picks the templating engine — Pebble by default; `template.engine` chooses when several adapters are
  on the classpath (`@ConditionalOnClass` + `@ConditionalOnProperty(matchIfMissing=true)` per engine).
- Builds one `SmtpMailTransport` per `mailers.*` entry into a `MailerRegistry`, wires in any
  `MailerProvider` beans, and falls back to a `LogMailTransport` (mail is logged, not sent) when none is
  configured.
- Assembles `AssetResolvers` (app `AssetResolver` beans stacked before the built-ins), the `MailRenderer`,
  and the `Mailer`.
- Selects the `MailQueue`: `InMemoryMailQueue` by default, `RedisMailQueue` when
  `queue.driver=redis` and `StringRedisTemplate` is present.

A static **`Mail` facade** is the entry point, initialized once by the autoconfig via a
`MailFacadeInitializer`: `Mail.send(...)`, `Mail.queue(...)`, `Mail.mailer("x")`, `Mail.onQueue("y")`
(both returning a chainable `Sender`), and `Mail.preview(...)` to render HTML without sending.

```java
Mail.send(new WelcomeMail("Calcifux"), List.of("calcifux@example.com"));
Mail.mailer("billing").onQueue("priority").queue(new InvoiceMail(id), List.of("calcifux@example.com"));
String html = Mail.preview(new WelcomeMail("Calcifux"));
```

**Everything is `@ConditionalOnMissingBean`** — define your own `TemplateRenderer`, `MailRenderer`,
`MailTransport`, `MailerRegistry`, `Mailer`, or `MailQueue` and the autoconfig steps aside.

## Consequences
- Drop in the starter, set `mailable-toolkit.*`, and send in one line — no bean plumbing in app code.
- Engine, mailers, asset resolvers, and queue are all chosen from config/classpath, yet every piece is
  overridable, so the toolkit never blocks an app's custom wiring.
- A misconfigured app still boots: with no mailers it logs mail (loud `warn`) rather than failing,
  surfacing the gap without crashing startup.
- Trade-off: the static `Mail` facade is global mutable state initialized at startup — calling it before
  the context is up (or outside a Spring app) throws a clear "not initialized" `MailToolkitException`,
  and it complicates parallel test isolation versus an injected bean.
- Trade-off: the engine-selection conditionals assume one templating adapter is intended; with several
  on the classpath and no `template.engine` set, ordering decides — operators should pin the engine
  explicitly.
