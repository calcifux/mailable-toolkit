package com.github.calcifux.mailabletoolkit.spring;

import com.github.calcifux.mailabletoolkit.MailTransport;
import com.github.calcifux.mailabletoolkit.RenderedMail;
import com.github.calcifux.mailabletoolkit.RetryableMailException;
import com.github.calcifux.mailabletoolkit.TerminalMailException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * A named SMTP transport backed by a Spring {@link JavaMailSender} (one per configured "mailer", built
 * once by the autoconfig from {@code mailable-toolkit.mailers.<name>}). Classifies failures so the queue
 * knows whether to retry: bad credentials / malformed message → terminal; everything else (network,
 * SMTP timeout) → retryable.
 */
@RequiredArgsConstructor
public class SmtpMailTransport implements MailTransport {

    private final String name;
    private final JavaMailSender sender;
    private final String fromEmail;
    private final String fromName;

    @Override
    public void send(RenderedMail mail) {
        try {
            sender.send(mail.getMimeMessage());
        } catch (MailAuthenticationException | MailParseException terminal) {
            throw new TerminalMailException("SMTP send failed (no retry): " + terminal.getMessage(), terminal);
        } catch (MailException transientFailure) {
            throw new RetryableMailException("SMTP send failed (will retry): " + transientFailure.getMessage(), transientFailure);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String fromEmail() {
        return fromEmail;
    }

    @Override
    public String fromName() {
        return fromName;
    }
}
