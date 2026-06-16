# Contributing to mailable-toolkit

Thanks for your interest in improving mailable-toolkit! Contributions of all sizes are
welcome — bug reports, docs, tests, new provider adapters.

## Ground rules

- Be respectful — see the [Code of Conduct](CODE_OF_CONDUCT.md).
- Open an **issue** to discuss non-trivial changes before sending a PR.
- Keep the build green: `mvn verify` must pass (it runs the unit tests).
- Match the existing style: full words over abbreviations, focused classes, Javadoc on
  public types, and comments that explain the *why*.

## Development setup

```bash
git clone https://github.com/calcifux/mailable-toolkit.git
cd mailable-toolkit
mvn verify          # build + run tests (JDK 21 required)
```

The project is a Maven reactor:

- `mailable-toolkit-core` — framework-agnostic model (`Mailable`/`Envelope`), MIME assembly, the
  `TemplateRenderer` / `MailTransport` / `MailQueue` SPIs, the registry and the in-memory queue.
  Keep it free of Spring (only `jakarta.mail` + slf4j).
- `mailable-toolkit-pebble` / `-thymeleaf` / `-freemarker` — one `TemplateRenderer` each.
- `mailable-toolkit-spring` — Spring Boot starter (auto-config, `mailable-toolkit.*` properties, the
  SMTP mailer registry, the Redis queue, and the static `Mail` facade).

## Design rules to preserve

- **Two orthogonal ports.** Templating (`TemplateRenderer`) and transport (`MailTransport`) are
  independent SPIs — a new engine or a new transport is a new implementation, never an `if/else`
  inside an existing one.
- **Bytes never hit the broker.** The queue carries a serialized `Mailable` reference; the worker
  re-runs `build()` + render + send. Attachments/inline images are resolved worker-side.
- **Fail honestly.** A send failure is either `RetryableMailException` (network/timeout → retry with
  backoff) or `TerminalMailException` (bad credentials/recipient → straight to the dead-letter) —
  never a silent swallow.

## Adding a templating engine

1. New module `mailable-toolkit-<engine>` with a `TemplateRenderer` implementation (support template
   inheritance + an inline mode where the engine allows).
2. Light it up in `MailableToolkitAutoConfiguration` behind `@ConditionalOnClass(<engine>)` +
   `@ConditionalOnProperty(mailable-toolkit.template.engine=<id>, matchIfMissing=true)`.
3. Cover it with a renderer test (inheritance + HTML-escaping).

## Adding a transport or queue

1. Implement `MailTransport` (a delivery channel) or `MailQueue` (a broker adapter) in core or spring.
2. Register it in `MailableToolkitAutoConfiguration` behind its own `@ConditionalOnProperty` +
   `@ConditionalOnMissingBean`, with config under `mailable-toolkit.*`.
3. Cover it with a test.

## Pull requests

- Branch from `main`, keep PRs focused, and describe the change and motivation.
- Add/adjust tests for behavior changes.
- The CI workflow runs `mvn verify` on every PR.

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
