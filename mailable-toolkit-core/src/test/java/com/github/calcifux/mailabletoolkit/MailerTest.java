package com.github.calcifux.mailabletoolkit;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Core wiring + MIME assembly, with a fake renderer and a capturing transport (no engine, no SMTP):
 * proves the render → resolve(from/mailer/recipients) → MIME → transport path, and the MIME shape for
 * 0 vs N inline images / attachments.
 */
class MailerTest {

    private static final class FakeRenderer implements TemplateRenderer {
        @Override
        public String render(String templateRef, Map<String, Object> model, Locale locale) {
            return "<html><body><p>Hi " + model.get("name") + "</p></body></html>";
        }

        @Override
        public String engineId() {
            return "fake";
        }
    }

    private static final class CapturingTransport implements MailTransport {
        private RenderedMail last;

        @Override
        public void send(RenderedMail mail) {
            this.last = mail;
        }

        @Override
        public String name() {
            return "primary";
        }

        @Override
        public String fromEmail() {
            return "noreply@corp.com";
        }

        @Override
        public String fromName() {
            return "Corp";
        }
    }

    private static final class HelloMail extends Mailable {
        private final String name;

        private HelloMail(String name) {
            this.name = name;
        }

        @Override
        public Envelope build() {
            return Envelope.builder()
                    .subject("Hi " + name)
                    .template("mail/hello")
                    .with("name", name)
                    .build();
        }
    }

    private static final class RichMail extends Mailable {
        @Override
        public Envelope build() {
            return Envelope.builder()
                    .subject("Rich")
                    .template("mail/rich")
                    .with("name", "Rich")
                    .inlineAsset(InlineAsset.ofData("logo", new byte[]{1, 2, 3}, "image/png"))
                    .attachData(new DataAttachment("guide.pdf", new byte[]{4, 5, 6}, "application/pdf"))
                    .build();
        }
    }

    private Mailer mailer(CapturingTransport transport) {
        return new Mailer(
                new MailRenderer(new FakeRenderer()),
                new MailerRegistry(List.of(transport), "primary"),
                "default@corp.com", "Default");
    }

    @Test
    void rendersAndSendsWithResolvedHeaders() throws Exception {
        CapturingTransport transport = new CapturingTransport();
        mailer(transport).send(new HelloMail("Calcifux"), List.of("calcifux@example.com"));

        MimeMessage message = transport.last.getMimeMessage();
        assertThat(message.getSubject()).isEqualTo("Hi Calcifux");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("calcifux@example.com");
        // Envelope set no from → falls back to the mailer's from.
        assertThat(message.getFrom()[0].toString()).contains("noreply@corp.com");
        assertThat(transport.last.getHtml()).contains("Hi Calcifux");
        // No inline / no attachments → bare multipart/alternative.
        assertThat(message.getContentType()).contains("multipart/alternative");
    }

    @Test
    void wrapsRelatedAndMixedWhenInlineAndAttachmentsPresent() throws Exception {
        CapturingTransport transport = new CapturingTransport();
        mailer(transport).send(new RichMail(), List.of("a@example.com"));

        MimeMessage message = transport.last.getMimeMessage();
        // Attachments present → outermost is multipart/mixed (which nests related → alternative).
        assertThat(message.getContentType()).contains("multipart/mixed");
    }

    @Test
    void unknownMailerFailsTerminally() {
        CapturingTransport transport = new CapturingTransport();
        assertThatThrownBy(() ->
                mailer(transport).send(new HelloMail("X"), List.of("a@b.com"), List.of(), List.of(), "does-not-exist"))
                .isInstanceOf(TerminalMailException.class);
    }
}
