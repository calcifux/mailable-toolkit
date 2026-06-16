package com.github.calcifux.mailabletoolkit;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The pure PAYLOAD a {@link Mailable#build()} returns: what to render and to whom — never HOW it is
 * sent (no SMTP, no engine). The architect's core takes it from here. Immutable; built with a fluent
 * builder. Collections default to empty, so 0 attachments / 0 inline images is the normal case.
 *
 * <pre>{@code
 * Envelope.builder()
 *     .subject("Welcome")
 *     .template("mail/welcome")                 // logical ref; the engine resolves it (inheritance ok)
 *     .with("userName", name)                   // model var (repeatable)
 *     .to("user@example.com")                   // recipient (repeatable; or pass to Mailer.send)
 *     .inlineAsset(InlineAsset.ofResource("logo", "classpath:/mail/logo.png"))  // 0..N
 *     .attachData(new DataAttachment("guide.pdf", bytes, "application/pdf"))     // 0..N
 *     .mailer("transactional")                  // optional: pin a named SMTP for this mail
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class Envelope implements Serializable {

    private final String subject;
    private final String fromEmail;
    private final String fromName;
    private final String replyTo;

    /** Logical template ref the engine resolves (supports {@code extends}/layout inheritance). */
    private final String template;
    /** Raw inline template source (needs an inline-capable engine); mutually exclusive with template. */
    private final String inlineTemplate;

    /** Template model. Add vars with {@code .with(key, value)}. */
    @Singular("with")
    private final Map<String, Object> model;

    @Singular("to")
    private final List<String> to;
    @Singular("cc")
    private final List<String> cc;
    @Singular("bcc")
    private final List<String> bcc;

    /** Locale for the render (i18n); null → the engine/default locale. */
    private final Locale locale;

    /** 0..N inline CID images. Empty = none. */
    @Singular("inlineAsset")
    private final List<InlineAsset> inlineAssets;

    /** 0..N file-path attachments. Empty = none. */
    @Singular("attach")
    private final List<String> attachments;

    /** 0..N byte attachments (no disk). Empty = none. */
    @Singular("attachData")
    private final List<DataAttachment> dataAttachments;

    /** Opt-in: files to delete after a SUCCESSFUL send (e.g. a temp PDF). */
    @Singular("cleanupPath")
    private final List<String> cleanupPaths;

    /**
     * Named SMTP transport ("mailer") this mail goes out from. null → the default mailer. A per-send
     * {@code Mail.mailer("x")} overrides this.
     */
    private final String mailer;
}
