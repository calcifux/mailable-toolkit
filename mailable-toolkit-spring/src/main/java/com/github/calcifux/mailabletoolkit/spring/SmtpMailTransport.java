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
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

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

    /**
     * Build a transport from raw SMTP settings — the one-liner a {@link com.github.calcifux.mailabletoolkit.MailerProvider}
     * uses to turn a DB row into a mailer. {@code encryption} is {@code starttls} (default) | {@code ssl} | {@code none}.
     */
    public static SmtpMailTransport smtp(String name, String host, int port, String username, String password,
                                         String encryption, String fromEmail, String fromName) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            sender.setPassword(password);
        }

        Properties mailProps = sender.getJavaMailProperties();
        mailProps.put("mail.transport.protocol", "smtp");
        mailProps.put("mail.smtp.auth", String.valueOf(username != null && !username.isBlank()));
        String mode = encryption == null ? "starttls" : encryption.toLowerCase();
        switch (mode) {
            case "starttls" -> mailProps.put("mail.smtp.starttls.enable", "true");
            case "ssl" -> mailProps.put("mail.smtp.ssl.enable", "true");
            default -> { /* none: plain SMTP (dev / internal relay) */ }
        }

        return new SmtpMailTransport(name, sender, fromEmail, fromName);
    }

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
