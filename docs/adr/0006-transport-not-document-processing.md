# 0006. Transport, Not Document Processing

- **Status:** Accepted
- **Date:** 2026-06-16

## Context
A mail with an attached PDF invariably raises the question "can the toolkit password-protect or
watermark that PDF before attaching it?" If the toolkit grew document-processing features it would pull
in PDF/imaging libraries, leak document policy into mail code, and blur the line with the dedicated
document toolkits.

## Decision
mailable-toolkit's job is to **assemble MIME and transport bytes**: take an `Envelope`, render it
through a `TemplateRenderer`, attach assets resolved by `AssetResolver`, build the
`multipart/mixed → related → alternative` tree, and hand it to a `MailTransport`. **Processing the
documents themselves — PDF password-protection, watermarking, merging — is the application's job, not
the toolkit's.**

The app produces the finished bytes (or a URI to them) and passes them in via `Envelope.attach(...)` /
`attachData(DataAttachment)`; the `Envelope.cleanupPath` option lets the app have a temp file deleted
after a successful send. For PDF-specific work, cross-reference **pdf-toolkit ADR-0005**, which owns
that concern.

(For the record: the single-tenant appliance's "no explicit CSRF token" style decision lives with the
appliance/auth, not here — it is **out of scope** for this toolkit.)

## Consequences
- The toolkit stays focused and lightweight — no PDF/imaging dependencies, no document policy in mail
  code.
- Clear separation of concerns: pdf-toolkit (or the app) protects/transforms documents; mailable-toolkit
  delivers them. Each can evolve independently.
- The `attach`/`attachData`/`cleanupPath`/`AssetResolver` surface is enough to carry any already-processed
  document, including ones produced just-in-time.
- Trade-off: an app that needs protected/watermarked attachments must orchestrate two toolkits (produce
  the document, then mail it) rather than getting it from one call.
- Trade-off: temp-file lifecycle (e.g. a generated protected PDF) is the app's responsibility, partly
  eased by `cleanupPath` after a successful send.
