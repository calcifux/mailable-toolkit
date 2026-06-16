package com.github.calcifux.mailabletoolkit;

import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * Architect-owned glue: takes an {@link Envelope}, renders its template through the selected
 * {@link TemplateRenderer}, and assembles a jakarta {@link MimeMessage} — transport-agnostic (no SMTP
 * here). MIME shape is the robust nesting that survives Outlook/Gmail:
 *
 * <pre>
 *   multipart/mixed                 (only if there are attachments)
 *     multipart/related             (only if there are inline CID images)
 *       multipart/alternative       text/plain fallback + text/html
 *       inline image, inline image  (0..N, Content-ID + disposition=inline)
 *     attachment, attachment        (0..N: byte and/or file-path)
 * </pre>
 *
 * With 0 inline images and 0 attachments it collapses to a bare multipart/alternative. The plain-text
 * part is auto-derived from the HTML so every mail has a text fallback.
 */
public class MailRenderer {

    private final TemplateRenderer renderer;
    private final AssetResolvers assets;

    /** Uses the default asset resolvers (classpath / http(s) / file). */
    public MailRenderer(TemplateRenderer renderer) {
        this(renderer, AssetResolvers.defaults());
    }

    public MailRenderer(TemplateRenderer renderer, AssetResolvers assets) {
        this.renderer = renderer;
        this.assets = assets;
    }

    /** Render + assemble. {@code from*} are already resolved (Envelope > mailer > global) by the caller. */
    public RenderedMail render(Envelope env, List<String> to, List<String> cc, List<String> bcc,
                               String fromEmail, String fromName) {
        String html = renderHtml(env);
        try {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
            applyHeaders(message, env, to, cc, bcc, fromEmail, fromName);
            message.setContent(assembleBody(env, html));
            message.saveChanges();
            return new RenderedMail(message, html);
        } catch (TerminalMailException | RetryableMailException e) {
            throw e;
        } catch (Exception e) {
            throw new MailToolkitException("Failed to assemble MIME message", e);
        }
    }

    /** Render ONLY the HTML (no MIME, no transport) — used for preview. */
    public String renderHtml(Envelope env) {
        if (env.getInlineTemplate() != null && !env.getInlineTemplate().isBlank()) {
            if (!renderer.supportsInline()) {
                throw new TerminalMailException("Engine '" + renderer.engineId()
                        + "' does not support inline templates; use a named template instead");
            }
            return renderer.renderInline(env.getInlineTemplate(), env.getModel(), env.getLocale());
        }
        if (env.getTemplate() == null || env.getTemplate().isBlank()) {
            throw new TerminalMailException("Envelope has neither a template nor an inline template");
        }
        return renderer.render(env.getTemplate(), env.getModel(), env.getLocale());
    }

    private void applyHeaders(MimeMessage message, Envelope env, List<String> to, List<String> cc,
                              List<String> bcc, String fromEmail, String fromName) throws Exception {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new TerminalMailException("No from address (set Envelope.fromEmail, the mailer's from, or a default)");
        }
        message.setFrom(address(fromEmail, fromName));
        if (env.getReplyTo() != null && !env.getReplyTo().isBlank()) {
            message.setReplyTo(new InternetAddress[]{address(env.getReplyTo(), null)});
        }
        addRecipients(message, Message.RecipientType.TO, to);
        addRecipients(message, Message.RecipientType.CC, cc);
        addRecipients(message, Message.RecipientType.BCC, bcc);
        if (message.getAllRecipients() == null || message.getAllRecipients().length == 0) {
            throw new TerminalMailException("No recipients (to/cc/bcc all empty)");
        }
        message.setSubject(env.getSubject() == null ? "" : env.getSubject(), StandardCharsets.UTF_8.name());
    }

    /** Body = alternative(text,html), wrapped in related if inline images, wrapped in mixed if attachments. */
    private MimeMultipart assembleBody(Envelope env, String html) throws Exception {
        MimeMultipart alternative = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(toPlainText(html), StandardCharsets.UTF_8.name());
        alternative.addBodyPart(textPart);
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");
        alternative.addBodyPart(htmlPart);

        MimeMultipart body = alternative;

        if (!env.getInlineAssets().isEmpty()) {
            MimeMultipart related = new MimeMultipart("related");
            MimeBodyPart altWrapper = new MimeBodyPart();
            altWrapper.setContent(body);
            related.addBodyPart(altWrapper);
            for (InlineAsset asset : env.getInlineAssets()) {
                related.addBodyPart(inlinePart(asset));
            }
            body = related;
        }

        if (!env.getAttachments().isEmpty() || !env.getDataAttachments().isEmpty()) {
            MimeMultipart mixed = new MimeMultipart("mixed");
            MimeBodyPart bodyWrapper = new MimeBodyPart();
            bodyWrapper.setContent(body);
            mixed.addBodyPart(bodyWrapper);
            for (String reference : env.getAttachments()) {
                ResolvedAsset resolved = assets.resolve(reference);
                MimeBodyPart part = new MimeBodyPart();
                part.setDataHandler(new DataHandler(new ByteArrayDataSource(resolved.content(), resolved.contentType())));
                part.setFileName(resolved.filename());
                mixed.addBodyPart(part);
            }
            for (DataAttachment data : env.getDataAttachments()) {
                String type = data.contentType() == null ? "application/octet-stream" : data.contentType();
                MimeBodyPart part = new MimeBodyPart();
                part.setDataHandler(new DataHandler(new ByteArrayDataSource(data.content(), type)));
                part.setFileName(data.filename());
                mixed.addBodyPart(part);
            }
            body = mixed;
        }

        return body;
    }

    private MimeBodyPart inlinePart(InlineAsset asset) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        if (asset.hasBytes()) {
            String type = asset.contentType() == null ? "application/octet-stream" : asset.contentType();
            part.setDataHandler(new DataHandler(new ByteArrayDataSource(asset.content(), type)));
        } else {
            ResolvedAsset resolved = assets.resolve(asset.resource());
            String type = (asset.contentType() != null && !asset.contentType().isBlank())
                    ? asset.contentType()
                    : resolved.contentType();
            part.setDataHandler(new DataHandler(new ByteArrayDataSource(resolved.content(), type)));
        }
        part.setContentID("<" + asset.cid() + ">");
        part.setDisposition(MimeBodyPart.INLINE);
        return part;
    }

    private void addRecipients(MimeMessage message, Message.RecipientType type, List<String> addresses)
            throws Exception {
        if (addresses == null) {
            return;
        }
        for (String a : addresses) {
            if (a != null && !a.isBlank()) {
                message.addRecipient(type, address(a, null));
            }
        }
    }

    private InternetAddress address(String email, String name) throws UnsupportedEncodingException,
            jakarta.mail.internet.AddressException {
        return name == null || name.isBlank()
                ? new InternetAddress(email)
                : new InternetAddress(email, name, StandardCharsets.UTF_8.name());
    }

    /** Naive HTML → text fallback (strip tags, collapse whitespace). Good enough for the alternative part. */
    private String toPlainText(String html) {
        return html
                .replaceAll("(?s)<(script|style)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
