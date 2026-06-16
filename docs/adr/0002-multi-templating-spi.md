# 0002. Multi-Templating SPI

- **Status:** Accepted
- **Date:** 2026-06-16

## Context
Email bodies need a real templating engine with **inheritance** — a shared layout/shell that each mail
extends (the milpa / Laravel Blade idea: `extends` + `block`). Teams already standardize on different
engines, so the toolkit must not marry one. A `Mailable` should describe *what* to render and never
import an engine, so switching engines is a config change, not a code change.

## Decision
Templating is a pluggable SPI: `TemplateRenderer` in the core. Each engine is a separate adapter
module:

- `render(String templateRef, Map<String,Object> model, Locale locale)` — renders a template by
  **logical name**; resolving parents/layouts (inheritance) is the engine's job.
- `renderInline(...)` + `supportsInline()` — optional support for a **raw inline template source**
  (e.g. a DB-stored, user-edited template). Engines that cannot do it inherit the default that throws
  `UnsupportedOperationException`.
- `engineId()` — stable id (`"pebble"`, `"thymeleaf"`, `"freemarker"`).

Three adapters ship: `mailable-toolkit-pebble` (default — `{% extends %}`/`{% block %}`, closest to
the milpa templates), `mailable-toolkit-thymeleaf` (fragment inheritance), and
`mailable-toolkit-freemarker` (macro/include inheritance, HTML auto-escape). The
`Envelope` carries either `template` (logical ref) or `inlineTemplate` (raw source, mutually
exclusive) plus `model` and `locale`. The core and every `Mailable` depend only on the interface.

## Consequences
- A `Mailable` is engine-agnostic: swapping engines is a dependency swap + one `template.engine`
  property; mailable code never changes.
- Inheritance/i18n are first-class — the layout shell lives once, mails extend it; `locale` flows into
  the render.
- Inline (DB-stored) templates are supported where the engine allows, enabling user-edited templates
  (the billing-mail case).
- Trade-off: `supportsInline()` is engine-dependent, so an app relying on inline templates must pick an
  inline-capable engine or it fails fast at render with a clear message.
- Trade-off: inline template strings render arbitrary user content — a template-injection surface the
  app must guard (sandbox/escape untrusted input).
- Trade-off: feature parity across engines is not guaranteed; templates are written against one
  engine's dialect, so a true engine switch may require touching the templates themselves.
