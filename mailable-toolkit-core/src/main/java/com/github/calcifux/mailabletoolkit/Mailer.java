package com.github.calcifux.mailabletoolkit;

import com.github.calcifux.mailabletoolkit.queue.QueuedMail;
import lombok.RequiredArgsConstructor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Architect-owned orchestrator (framework-agnostic). Renders a {@link Mailable} and sends it through the
 * resolved {@link MailTransport}. The Spring starter wraps this behind the static {@code Mail} facade and
 * adds the async {@code queue(...)} path; here it stays plain so a CLI/job can use it directly.
 *
 * <p>Resolution rules: the SMTP is {@code mailerOverride > Envelope.mailer > default}; recipients are the
 * passed lists if non-empty else the Envelope's; the from is {@code Envelope.from > mailer's from >
 * global default}.</p>
 */
@RequiredArgsConstructor
public class Mailer {

    private final MailRenderer renderer;
    private final MailerRegistry mailers;
    private final String defaultFromEmail;
    private final String defaultFromName;

    public void send(Mailable mailable, List<String> to) {
        send(mailable, to, List.of(), List.of(), null);
    }

    public void send(Mailable mailable, List<String> to, List<String> cc, List<String> bcc) {
        send(mailable, to, cc, bcc, null);
    }

    public void send(Mailable mailable, List<String> to, List<String> cc, List<String> bcc, String mailerOverride) {
        Envelope env = mailable.build();
        MailTransport transport = mailers.resolve(firstNonBlank(mailerOverride, env.getMailer()));

        List<String> resolvedTo = nonEmpty(to) ? to : env.getTo();
        List<String> resolvedCc = nonEmpty(cc) ? cc : env.getCc();
        List<String> resolvedBcc = nonEmpty(bcc) ? bcc : env.getBcc();

        String fromEmail = firstNonBlank(env.getFromEmail(), transport.fromEmail(), defaultFromEmail);
        String fromName = firstNonBlank(env.getFromName(), transport.fromName(), defaultFromName);

        RenderedMail rendered = renderer.render(env, resolvedTo, resolvedCc, resolvedBcc, fromEmail, fromName);
        transport.send(rendered);
        cleanup(env.getCleanupPaths());
    }

    /** Worker entrypoint: deliver a dequeued mail (unpacks the {@link QueuedMail}). */
    public void dispatch(QueuedMail mail) {
        send(mail.getMailable(), orEmpty(mail.getTo()), orEmpty(mail.getCc()), orEmpty(mail.getBcc()), mail.getMailer());
    }

    /** Render the HTML without sending — for a /preview endpoint or QA. */
    public String preview(Mailable mailable) {
        return renderer.renderHtml(mailable.build());
    }

    private void cleanup(List<String> paths) {
        if (paths == null) {
            return;
        }
        for (String path : paths) {
            try {
                Files.deleteIfExists(Path.of(path));
            } catch (Exception ignored) {
                // best-effort cleanup; a leftover temp file must not fail a sent mail
            }
        }
    }

    private static boolean nonEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
