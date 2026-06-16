# mailable-toolkit

A **Laravel-Mailable for Java**. Write a typed `Mailable` — a subject, a template + its vars, optional
inline images and attachments — and send it:

```java
Mail.send(new WelcomeMail(user.name()), List.of(user.email()));
```

Templating is pluggable behind one SPI (**Pebble**, **Thymeleaf**, **FreeMarker** — all with template
inheritance). SMTP is pluggable behind named **"mailers"**, so a mail can choose which server it goes out
from (multi-SMTP). Sending is sync or async over a **queue** (in-memory or **Redis**), and **each mailable
rides its own queue**. It mounts as a Spring Boot starter, but the core has no Spring dependency.

## Modules

| Module | What it is |
| --- | --- |
| `mailable-toolkit-core` | Pure Java (`jakarta.mail` + slf4j). The `Mailable`/`Envelope` model, MIME assembly, the `TemplateRenderer` / `MailTransport` / `MailQueue` SPIs, the mailer registry, and the in-memory queue. No Spring. |
| `mailable-toolkit-pebble` | `TemplateRenderer` over Pebble (default engine — `{% extends %}`/`{% block %}` inheritance). |
| `mailable-toolkit-thymeleaf` | `TemplateRenderer` over Thymeleaf (fragment inheritance). |
| `mailable-toolkit-freemarker` | `TemplateRenderer` over FreeMarker (macro/include inheritance, HTML auto-escape). |
| `mailable-toolkit-spring` | Spring Boot starter: auto-config, `mailable-toolkit.*` properties, the named-mailer SMTP registry, the Redis Streams queue, and the static `Mail` facade. |

## Install (JitPack)

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<!-- the starter -->
<dependency>
  <groupId>com.github.calcifux.mailable-toolkit</groupId>
  <artifactId>mailable-toolkit-spring</artifactId>
  <version>v0.1.0</version>
</dependency>

<!-- pick ONE templating engine -->
<dependency>
  <groupId>com.github.calcifux.mailable-toolkit</groupId>
  <artifactId>mailable-toolkit-pebble</artifactId>
  <version>v0.1.0</version>
</dependency>
```

Add `spring-boot-starter-data-redis` too if you want the Redis queue.

---

# For the jr — write a mailable and send it

### 1. Configure once (`application.yml`)

```yaml
mailable-toolkit:
  template:
    engine: pebble          # pebble | thymeleaf | freemarker (auto if only one on the classpath)
    prefix: "mail/"         # templates live in src/main/resources/mail/
  from:
    email: noreply@corp.com # global fallback sender
    name: Corp
  default-mailer: transactional
  mailers:                  # one or many SMTP servers
    transactional:
      host: smtp.internal
      port: 587
      username: app
      password: ${SMTP_PASSWORD}
      encryption: starttls  # starttls | ssl | none
    billing:
      host: smtp.vendor.com
      port: 465
      username: billing
      password: ${BILLING_SMTP_PASSWORD}
      encryption: ssl
      from-email: billing@corp.com   # this mailer's own sender
  queue:
    driver: redis           # inmemory (dev) | redis (durable)
    queues: [default, priority]
```

### 2. Write a `Mailable`

A mailable is a plain class: its `build()` returns an `Envelope` describing the email.

```java
public class WelcomeMail extends Mailable {

    private final String name;

    public WelcomeMail(String name) {
        this.name = name;
    }

    @Override
    public Envelope build() {
        return Envelope.builder()
                .subject("Welcome, " + name)
                .template("welcome")            // resolves mail/welcome.peb
                .with("name", name)             // template variables
                .build();
    }
}
```

### 3. A template with inheritance

`src/main/resources/mail/layout.peb` — the shared shell:

```html
<html>
  <body style="font-family: sans-serif">
    <div class="content">{% block content %}{% endblock %}</div>
    <hr><small>© Corp</small>
  </body>
</html>
```

`src/main/resources/mail/welcome.peb` — extends it (just like milpa / Laravel Blade):

```html
{% extends "mail/layout" %}
{% block content %}
  <h1>Hi {{ name }}</h1>
  <p>Welcome aboard.</p>
{% endblock %}
```

### 4. Send it

```java
Mail.send(new WelcomeMail("Calcifux"), List.of("calcifux@example.com"));   // now
Mail.queue(new WelcomeMail("Calcifux"), List.of("calcifux@example.com"));  // async, on its queue
```

### Inline images (0..N) and attachments (0..N)

```java
@Override
public Envelope build() {
    return Envelope.builder()
            .subject("Your invoice")
            .template("invoice")
            // inline CID image — reference it in the template as <img src="cid:logo">
            .inlineAsset(InlineAsset.ofResource("logo", "classpath:mail/logo.png"))
            // attachment from a file path...
            .attach("/var/app/invoices/INV-1024.pdf")
            // ...or from bytes you already have
            .attachData(new DataAttachment("summary.csv", csvBytes, "text/csv"))
            .with("total", total)
            .build();
}
```

Pass `0` of either and it's a plain HTML email; the MIME tree is built to match (`multipart/mixed →
related → alternative`, with a plain-text fallback generated from the HTML).

### Pick a queue / pick an SMTP per send

```java
Mail.onQueue("priority").queue(new AlertMail(x), List.of(email));     // this queue
Mail.mailer("billing").send(new InvoiceMail(id), List.of(email));    // this SMTP
Mail.mailer("billing").onQueue("priority").queue(new InvoiceMail(id), List.of(email));
```

A mailable can declare its own default queue by overriding `String queue()`:

```java
public class AlertMail extends Mailable {
    @Override public String queue() { return "priority"; }
    @Override public Envelope build() { /* ... */ }
}
```

### Preview without sending

```java
String html = Mail.preview(new WelcomeMail("Calcifux"));   // rendered HTML, no SMTP — for a /preview endpoint or QA
```

---

# For the architect — how it's wired

Two **orthogonal** ports, each with its own adapters:

```
                       ┌─────────────── TemplateRenderer (SPI) ───────────────┐
 Mailable.build()      │  pebble · thymeleaf · freemarker  (inheritance)      │
        │              └───────────────────────────────────────────────────────┘
        ▼                                   │ html
   Envelope ──> MailRenderer ──────────────►│ + inline/attachments ──> RenderedMail (MIME)
   (subject, from,                          ▼
    template+vars,        ┌──────────── MailTransport (SPI) ─────────────┐
    0..N inline,          │ SmtpMailTransport per named "mailer"         │ ──> SMTP
    0..N attach,          │   resolve: override > Envelope.mailer > default
    mailer, queue)        └───────────────────────────────────────────────┘
        │
        └── async ──> MailQueue (SPI): InMemoryMailQueue | RedisMailQueue
                       carries a SERIALIZED Mailable reference, one stream/worker PER queue name;
                       the worker re-runs build + render + send → bytes never hit the broker.
                       retry w/ backoff (RetryPolicy) → dead-letter on exhaustion.
```

- **Bytes never travel through the broker.** A queued mail is the serialized `Mailable` + recipients +
  chosen mailer + queue name. The worker reconstructs everything (re-render, re-read attachments) at
  delivery time. Keeps the broker small and the payload replay-safe.
- **Failures are typed.** `SmtpMailTransport` maps bad credentials / malformed message to
  `TerminalMailException` (no retry → dead-letter) and everything else (network, timeout) to
  `RetryableMailException` (retry with backoff).
- **Everything is `@ConditionalOnMissingBean`.** Define your own `TemplateRenderer`, `MailTransport`,
  `MailerRegistry`, `Mailer` or `MailQueue` bean and the autoconfig steps aside.

### Without Spring

The core is standalone — wire it by hand in a CLI or a job:

```java
TemplateRenderer renderer = new PebbleTemplateRenderer("mail/", ".peb");
MailerRegistry registry = new MailerRegistry(List.of(myTransport), "default");
Mailer mailer = new Mailer(new MailRenderer(renderer), registry, "noreply@corp.com", "Corp");

mailer.send(new WelcomeMail("Calcifux"), List.of("calcifux@example.com"));
```

### Redis queue

`mailable-toolkit.queue.driver=redis` (with `spring-data-redis` on the classpath) swaps the in-memory
queue for `RedisMailQueue`: one Redis Stream per queue name (`<key-prefix>:<queue>`), a consumer group
with explicit ack, retry by re-adding with `attempts++` and backoff, and a `<...>:dlq` dead-letter
stream. Run the same app on several nodes and the consumer group balances the work.

## Build

```bash
mvn verify              # build + tests
mvn -DskipTests install
```

Requires JDK 21.

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) and the
[Code of Conduct](CODE_OF_CONDUCT.md). In short: open an issue to discuss non-trivial changes, keep
`mvn verify` green, and follow the existing style. Release notes live in [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE) © Carlos Guillermo Reyes Ramiro
