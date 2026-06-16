# Examples

Standalone snippets (not compiled as part of the build) showing the common shapes. See the root
[README](../README.md) for the `application.yml` config and the templating setup.

| File | Shows |
| --- | --- |
| [`WelcomeMail.java`](WelcomeMail.java) | The simplest mailable — subject + template + vars. |
| [`InvoiceMail.java`](InvoiceMail.java) | Inline CID image, an attachment by path **and** by bytes, reply-to, locale, a per-mailable queue, and going out from a named SMTP mailer. |
| [`SendingExamples.java`](SendingExamples.java) | Every dispatch path: `send`, `queue`, `mailer("x").send`, `onQueue("y").queue`, both together, and `preview`. |
| [`DynamicMailersAndAssets.java`](DynamicMailersAndAssets.java) | Resolve SMTP servers from a DB table at runtime (`MailerProvider`) and add a custom asset scheme like `s3://` (`AssetResolver`) — both just beans. |

### Templates these expect (Pebble)

`src/main/resources/mail/layout.peb`

```html
<html><body>
  <div class="content">{% block content %}{% endblock %}</div>
</body></html>
```

`src/main/resources/mail/welcome.peb`

```html
{% extends "mail/layout" %}
{% block content %}<h1>Hi {{ name }}</h1><p>Welcome aboard.</p>{% endblock %}
```

`src/main/resources/mail/invoice.peb`

```html
{% extends "mail/layout" %}
{% block content %}
  <img src="cid:logo" alt="logo">
  <h1>Invoice {{ invoiceId }}</h1>
{% endblock %}
```
